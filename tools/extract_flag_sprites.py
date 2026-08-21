#!/usr/bin/env python3
import os
import sys
import struct
import zlib

CANVAS_W = 384
CANVAS_H = 320
TARGET_GROUND_Y = 308

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

def extract_quadrant(src_rgba, src_w, ox, oy, qw, qh):
    crop_rgba = bytearray(qw * qh * 4)
    for y in range(qh):
        for x in range(qw):
            s_idx = ((oy + y) * src_w + (ox + x)) * 4
            d_idx = (y * qw + x) * 4
            r = src_rgba[s_idx]
            g = src_rgba[s_idx + 1]
            b = src_rgba[s_idx + 2]
            max_c = max(r, g, b)
            if max_c <= 18:
                alpha = 0
            elif max_c < 55:
                t = (max_c - 18) / (55 - 18)
                alpha = int(t * 255)
            else:
                alpha = 255
            crop_rgba[d_idx] = r
            crop_rgba[d_idx + 1] = g
            crop_rgba[d_idx + 2] = b
            crop_rgba[d_idx + 3] = alpha
    return crop_rgba

def crop_tight(crop_rgba, qw, qh):
    min_x, max_x = 9999, -1
    min_y, max_y = 9999, -1
    for y in range(qh):
        for x in range(qw):
            if crop_rgba[(y * qw + x) * 4 + 3] > 10:
                if x < min_x: min_x = x
                if x > max_x: max_x = x
                if y < min_y: min_y = y
                if y > max_y: max_y = y
    cw = max_x - min_x + 1
    ch = max_y - min_y + 1
    tight = bytearray(cw * ch * 4)
    for y in range(ch):
        for x in range(cw):
            s_idx = ((min_y + y) * qw + (min_x + x)) * 4
            d_idx = (y * cw + x) * 4
            tight[d_idx:d_idx+4] = crop_rgba[s_idx:s_idx+4]
    return tight, cw, ch

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
    w, h, rgba = read_png_rgba("artifacts/media_flag_2x2.png")
    qw, qh = w // 2, h // 2
    quads = [
        ("pet_flag_01", 0, 0),
        ("pet_flag_02", qw, 0),
        ("pet_flag_03", 0, qh),
        ("pet_flag_04", qw, qh),
    ]

    # Uniform scale factor
    # Frame 2 total height is 483px in source. To fit inside 302px in canvas:
    scale = 300.0 / 483.0  # ~0.6211
    print(f"Using uniform scale factor: {scale:.4f}")

    for name, ox, oy in quads:
        raw_quad = extract_quadrant(rgba, w, ox, oy, qw, qh)
        tight, cw, ch = crop_tight(raw_quad, qw, qh)
        dw = int(round(cw * scale))
        dh = int(round(ch * scale))
        scaled = resize_bilinear(tight, cw, ch, dw, dh)

        # Place onto 384x320 canvas
        canvas = bytearray(CANVAS_W * CANVAS_H * 4)
        target_bottom = TARGET_GROUND_Y
        target_top = target_bottom - dh
        if target_top < 6:
            # Shift slightly down if needed
            target_top = 6
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
        print(f"Exported {out_path} ({CANVAS_W}x{CANVAS_H}), placed {dw}x{dh} at x={target_left}, y={target_top}")

if __name__ == "__main__":
    main()
