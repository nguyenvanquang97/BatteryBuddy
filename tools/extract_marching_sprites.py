#!/usr/bin/env python3
import os
import sys
import struct
import zlib
from collections import deque

CANVAS_W = 384
CANVAS_H = 320
TARGET_GROUND_Y = 308

# Calibrated scale against benchmark pet_look_front_01.png (288px standing height)
# Source cat height is ~378px -> scale = 288.0 / 378.0 = 0.7500
SCALE = 0.7500

def paeth_predictor(a: int, b: int, c: int) -> int:
    p = a + b - c
    pa = abs(p - a)
    pb = abs(p - b)
    pc = abs(p - c)
    if pa <= pb and pa <= pc:
        return a
    elif pb <= pc:
        return b
    else:
        return c

def read_png_rgba(path: str):
    with open(path, "rb") as f:
        data = f.read()
    pos = 8
    width = 0
    height = 0
    bit_depth = 0
    color_type = 0
    idat_chunks = []
    while pos < len(data):
        length, chunk_type = struct.unpack(">I4s", data[pos:pos+8])
        chunk_data = data[pos+8:pos+8+length]
        pos += 12 + length
        if chunk_type == b"IHDR":
            width, height, bit_depth, color_type, _, _, _ = struct.unpack(">IIBBBBB", chunk_data)
        elif chunk_type == b"IDAT":
            idat_chunks.append(chunk_data)
        elif chunk_type == b"IEND":
            break
    raw_decompressed = zlib.decompress(b"".join(idat_chunks))
    bpp = 4 if color_type == 6 else 3
    stride = width * bpp
    raw_rgba = bytearray(width * height * 4)
    raw_offset = 0
    prev_scanline = bytearray(stride)
    for y in range(height):
        filter_type = raw_decompressed[raw_offset]
        raw_offset += 1
        curr_scanline = bytearray(raw_decompressed[raw_offset:raw_offset+stride])
        raw_offset += stride
        if filter_type == 1:
            for x in range(bpp, stride):
                curr_scanline[x] = (curr_scanline[x] + curr_scanline[x - bpp]) & 0xFF
        elif filter_type == 2:
            for x in range(stride):
                curr_scanline[x] = (curr_scanline[x] + prev_scanline[x]) & 0xFF
        elif filter_type == 3:
            for x in range(stride):
                left = curr_scanline[x - bpp] if x >= bpp else 0
                up = prev_scanline[x]
                curr_scanline[x] = (curr_scanline[x] + ((left + up) // 2)) & 0xFF
        elif filter_type == 4:
            for x in range(stride):
                left = curr_scanline[x - bpp] if x >= bpp else 0
                up = prev_scanline[x]
                up_left = prev_scanline[x - bpp] if x >= bpp else 0
                curr_scanline[x] = (curr_scanline[x] + paeth_predictor(left, up, up_left)) & 0xFF
        prev_scanline = curr_scanline
        for x in range(width):
            src_idx = x * bpp
            dst_idx = (y * width + x) * 4
            raw_rgba[dst_idx] = curr_scanline[src_idx]
            raw_rgba[dst_idx + 1] = curr_scanline[src_idx + 1]
            raw_rgba[dst_idx + 2] = curr_scanline[src_idx + 2]
            raw_rgba[dst_idx + 3] = curr_scanline[src_idx + 3] if bpp == 4 else 255
    return width, height, raw_rgba

def write_png_rgba(path: str, width: int, height: int, rgba: bytearray):
    raw_data = bytearray()
    for y in range(height):
        raw_data.append(0)
        start = y * width * 4
        raw_data.extend(rgba[start:start + width * 4])
    compressed = zlib.compress(bytes(raw_data), level=9)
    ihdr = struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0)
    def make_chunk(chunk_type: bytes, chunk_data: bytes) -> bytes:
        length = struct.pack(">I", len(chunk_data))
        crc = struct.pack(">I", zlib.crc32(chunk_type + chunk_data) & 0xFFFFFFFF)
        return length + chunk_type + chunk_data + crc
    png_bytes = b"\x89PNG\r\n\x1a\n" + make_chunk(b"IHDR", ihdr) + make_chunk(b"IDAT", compressed) + make_chunk(b"IEND", b"")
    with open(path, "wb") as f:
        f.write(png_bytes)

def extract_cell(src_rgba, src_w, src_h, x0, y0, x1, y1):
    cw = x1 - x0
    ch = y1 - y0
    crop_rgba = bytearray(cw * ch * 4)
    for y in range(ch):
        for x in range(cw):
            sx = x0 + x
            sy = y0 + y
            if 0 <= sx < src_w and 0 <= sy < src_h:
                s_idx = (sy * src_w + sx) * 4
                d_idx = (y * cw + x) * 4
                r = src_rgba[s_idx]
                g = src_rgba[s_idx + 1]
                b = src_rgba[s_idx + 2]
                max_c = max(r, g, b)
                if max_c <= 15:
                    alpha = 0
                elif max_c < 48:
                    t = (max_c - 15) / (48 - 15)
                    alpha = int(round(t * 255))
                    inv = 255.0 / max(1, alpha)
                    r = min(255, int(round(r * inv)))
                    g = min(255, int(round(g * inv)))
                    b = min(255, int(round(b * inv)))
                else:
                    alpha = 255
                crop_rgba[d_idx] = r
                crop_rgba[d_idx + 1] = g
                crop_rgba[d_idx + 2] = b
                crop_rgba[d_idx + 3] = alpha
    return crop_rgba, cw, ch

def isolate_main_component(crop_rgba, cw, ch):
    visited = [False] * (cw * ch)
    components = []
    for y in range(ch):
        for x in range(cw):
            idx = y * cw + x
            if not visited[idx] and crop_rgba[idx * 4 + 3] > 20:
                comp = []
                queue = deque([(x, y)])
                visited[idx] = True
                while queue:
                    cx, cy = queue.popleft()
                    comp.append((cx, cy))
                    for dx, dy in [(-1,0), (1,0), (0,-1), (0,1)]:
                        nx, ny = cx + dx, cy + dy
                        if 0 <= nx < cw and 0 <= ny < ch:
                            nidx = ny * cw + nx
                            if not visited[nidx] and crop_rgba[nidx * 4 + 3] > 20:
                                visited[nidx] = True
                                queue.append((nx, ny))
                components.append(comp)

    if not components:
        return crop_rgba, 0, 0, cw, ch

    main_comp = max(components, key=len)
    keep_set = set(main_comp)

    cleaned = bytearray(cw * ch * 4)
    min_x, max_x = 9999, -1
    min_y, max_y = 9999, -1
    for y in range(ch):
        for x in range(cw):
            idx = y * cw + x
            if (x, y) in keep_set:
                cleaned[idx * 4 : idx * 4 + 4] = crop_rgba[idx * 4 : idx * 4 + 4]
                if x < min_x: min_x = x
                if x > max_x: max_x = x
                if y < min_y: min_y = y
                if y > max_y: max_y = y

    tight_w = max_x - min_x + 1
    tight_h = max_y - min_y + 1
    tight = bytearray(tight_w * tight_h * 4)
    for y in range(tight_h):
        for x in range(tight_w):
            s_idx = ((min_y + y) * cw + (min_x + x)) * 4
            d_idx = (y * tight_w + x) * 4
            tight[d_idx : d_idx + 4] = cleaned[s_idx : s_idx + 4]

    return tight, tight_w, tight_h

def resize_bilinear(src_rgba, sw, sh, dw, dh):
    dst = bytearray(dw * dh * 4)
    for dy in range(dh):
        sy = dy * (sh - 1) / max(1, dh - 1)
        iy = int(sy)
        fy = sy - iy
        iy_next = min(iy + 1, sh - 1)
        for dx in range(dw):
            sx = dx * (sw - 1) / max(1, dw - 1)
            ix = int(sx)
            fx = sx - ix
            ix_next = min(ix + 1, sw - 1)
            d_idx = (dy * dw + dx) * 4
            for c in range(4):
                v00 = src_rgba[(iy * sw + ix) * 4 + c]
                v01 = src_rgba[(iy * sw + ix_next) * 4 + c]
                v10 = src_rgba[(iy_next * sw + ix) * 4 + c]
                v11 = src_rgba[(iy_next * sw + ix_next) * 4 + c]
                val = (1 - fx) * (1 - fy) * v00 + fx * (1 - fy) * v01 + (1 - fx) * fy * v10 + fx * fy * v11
                dst[d_idx + c] = int(round(val))
    return dst

def main():
    src_path = "artifacts/media_marching_2x2.png"
    w, h, rgba = read_png_rgba(src_path)

    # 4 cells with safe overlap
    cells = [
        ("pet_flag_walk_01", 100, 30, 512, 470),
        ("pet_flag_walk_02", 512, 30, 1024, 470),
        ("pet_flag_walk_03", 90, 530, 512, 970),
        ("pet_flag_walk_04", 512, 530, 1024, 970)
    ]

    print(f"Extracting 4 marching walk cycle frames with scale S={SCALE:.4f}...")

    for name, x0, y0, x1, y1 in cells:
        crop_rgba, cw, ch = extract_cell(rgba, w, h, x0, y0, x1, y1)
        tight, tw, th = isolate_main_component(crop_rgba, cw, ch)

        dw = int(round(tw * SCALE))
        dh = int(round(th * SCALE))
        scaled = resize_bilinear(tight, tw, th, dw, dh)

        # Place onto 384x320 canvas with ground baseline at TARGET_GROUND_Y (308)
        canvas = bytearray(CANVAS_W * CANVAS_H * 4)
        target_bottom = TARGET_GROUND_Y
        target_top = target_bottom - dh
        target_left = (CANVAS_W - dw) // 2

        for y in range(dh):
            for x in range(dw):
                cy = target_top + y
                cx = target_left + x
                if 0 <= cy < CANVAS_H and 0 <= cx < CANVAS_W:
                    s_idx = (y * dw + x) * 4
                    d_idx = (cy * CANVAS_W + cx) * 4
                    canvas[d_idx:d_idx+4] = scaled[s_idx:s_idx+4]

        out_path = f"app/src/main/res/drawable-nodpi/{name}.png"
        write_png_rgba(out_path, CANVAS_W, CANVAS_H, canvas)
        print(f"Exported {out_path} ({CANVAS_W}x{CANVAS_H}), content {dw}x{dh} placed at ({target_left}, {target_top})")

if __name__ == "__main__":
    main()
