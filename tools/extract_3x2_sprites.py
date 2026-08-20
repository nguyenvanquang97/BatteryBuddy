#!/usr/bin/env python3
"""Extract, remove black background, isolate cat component, and normalize 3x2 Pounce & Confused sprites."""

import os
import sys
import struct
import zlib
from pathlib import Path
from collections import deque
from typing import Optional


CANVAS_SIZE = 320
BOTTOM_MARGIN = 12
TARGET_GROUND_Y = CANVAS_SIZE - BOTTOM_MARGIN  # 308px
MAX_ALLOWED_W = 296.0
MAX_ALLOWED_H = 280.0


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


def read_png_rgba(path: str) -> tuple[int, int, bytearray]:
    with open(path, "rb") as f:
        data = f.read()

    assert data[:8] == b"\x89PNG\r\n\x1a\n", f"{path} is not a valid PNG file"
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
            assert bit_depth == 8, f"Unsupported bit depth: {bit_depth}"
            assert color_type in (2, 6), f"Unsupported color type: {color_type}"
        elif chunk_type == b"IDAT":
            idat_chunks.append(chunk_data)
        elif chunk_type == b"IEND":
            break

    raw_decompressed = zlib.decompress(b"".join(idat_chunks))
    bpp = 4 if color_type == 6 else 3
    stride = width * bpp
    raw_rgba = bytearray(width * height * 4)

    raw_offset = 0
    rgba_offset = 0
    prev_scanline = bytearray(stride)

    for y in range(height):
        filter_type = raw_decompressed[raw_offset]
        raw_offset += 1
        curr_scanline = bytearray(raw_decompressed[raw_offset:raw_offset+stride])
        raw_offset += stride

        if filter_type == 0:
            pass
        elif filter_type == 1:
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
        else:
            raise ValueError(f"Unknown filter type: {filter_type}")

        prev_scanline = curr_scanline[:]

        if color_type == 6:
            raw_rgba[rgba_offset:rgba_offset+stride] = curr_scanline
            rgba_offset += stride
        else:
            for x in range(width):
                raw_rgba[rgba_offset] = curr_scanline[x * 3]
                raw_rgba[rgba_offset + 1] = curr_scanline[x * 3 + 1]
                raw_rgba[rgba_offset + 2] = curr_scanline[x * 3 + 2]
                raw_rgba[rgba_offset + 3] = 255
                rgba_offset += 4

    return width, height, raw_rgba


def write_png_rgba(path: str, width: int, height: int, rgba_bytes: bytearray) -> None:
    stride = width * 4
    raw_filtered = bytearray()

    for y in range(height):
        raw_filtered.append(0)
        raw_filtered.extend(rgba_bytes[y * stride:(y + 1) * stride])

    compressed = zlib.compress(bytes(raw_filtered), level=9)

    ihdr = struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0)
    ihdr_crc = zlib.crc32(b"IHDR" + ihdr)
    idat_crc = zlib.crc32(b"IDAT" + compressed)
    iend_crc = zlib.crc32(b"IEND")

    with open(path, "wb") as f:
        f.write(b"\x89PNG\r\n\x1a\n")
        f.write(struct.pack(">I4s", len(ihdr), b"IHDR"))
        f.write(ihdr)
        f.write(struct.pack(">I", ihdr_crc))
        f.write(struct.pack(">I4s", len(compressed), b"IDAT"))
        f.write(compressed)
        f.write(struct.pack(">I", idat_crc))
        f.write(struct.pack(">I4s", 0, b"IEND"))
        f.write(struct.pack(">I", iend_crc))


def remove_black_background(rgba: bytearray, width: int, height: int) -> None:
    """Soft keying for black background (#000000) preserving fluffy edges."""
    for i in range(0, width * height * 4, 4):
        r = rgba[i]
        g = rgba[i + 1]
        b = rgba[i + 2]

        max_c = max(r, g, b)
        if max_c <= 15:
            rgba[i + 3] = 0
        elif max_c <= 48:
            alpha_fraction = (max_c - 15.0) / (48.0 - 15.0)
            a = int(round(alpha_fraction * 255))
            rgba[i + 3] = a
            factor = 255.0 / a if a > 0 else 1.0
            rgba[i] = min(255, int(round(r * factor)))
            rgba[i + 1] = min(255, int(round(g * factor)))
            rgba[i + 2] = min(255, int(round(b * factor)))
        else:
            rgba[i + 3] = 255


def isolate_largest_component(rgba: bytearray, width: int, height: int) -> Optional[tuple[int, int, int, int]]:
    visited = bytearray(width * height)
    largest_pixels: list[int] = []

    for y in range(height):
        for x in range(width):
            idx = y * width + x
            if visited[idx] or rgba[idx * 4 + 3] <= 20:
                continue

            queue = deque([(x, y)])
            visited[idx] = 1
            component = []

            while queue:
                cx, cy = queue.popleft()
                component.append(cy * width + cx)
                for nx, ny in ((cx - 1, cy), (cx + 1, cy), (cx, cy - 1), (cx, cy + 1)):
                    if 0 <= nx < width and 0 <= ny < height:
                        nidx = ny * width + nx
                        if not visited[nidx] and rgba[nidx * 4 + 3] > 20:
                            visited[nidx] = 1
                            queue.append((nx, ny))

            if len(component) > len(largest_pixels):
                largest_pixels = component

    if not largest_pixels:
        return None

    largest_set = set(largest_pixels)
    min_x, min_y, max_x, max_y = width, height, -1, -1

    for idx in range(width * height):
        if idx not in largest_set:
            rgba[idx * 4 + 3] = 0
        else:
            x = idx % width
            y = idx // width
            if x < min_x: min_x = x
            if x > max_x: max_x = x
            if y < min_y: min_y = y
            if y > max_y: max_y = y

    return min_x, min_y, max_x + 1, max_y + 1


def crop_image(rgba: bytearray, src_w: int, src_h: int, x1: int, y1: int, x2: int, y2: int) -> tuple[int, int, bytearray]:
    w = x2 - x1
    h = y2 - y1
    out = bytearray(w * h * 4)
    for y in range(h):
        src_row = ((y1 + y) * src_w + x1) * 4
        dst_row = y * w * 4
        out[dst_row:dst_row + w * 4] = rgba[src_row:src_row + w * 4]
    return w, h, out


def bilinear_scale(src_rgba: bytearray, src_w: int, src_h: int, dst_w: int, dst_h: int) -> bytearray:
    dst = bytearray(dst_w * dst_h * 4)
    x_ratio = float(src_w - 1) / max(1, dst_w - 1) if dst_w > 1 else 0
    y_ratio = float(src_h - 1) / max(1, dst_h - 1) if dst_h > 1 else 0

    for y in range(dst_h):
        src_y = y * y_ratio
        y_l = int(src_y)
        y_h = min(src_h - 1, y_l + 1)
        y_weight = src_y - y_l

        for x in range(dst_w):
            src_x = x * x_ratio
            x_l = int(src_x)
            x_h = min(src_w - 1, x_l + 1)
            x_weight = src_x - x_l

            dst_idx = (y * dst_w + x) * 4

            for c in range(4):
                a = src_rgba[(y_l * src_w + x_l) * 4 + c]
                b = src_rgba[(y_l * src_w + x_h) * 4 + c]
                c_val = src_rgba[(y_h * src_w + x_l) * 4 + c]
                d = src_rgba[(y_h * src_w + x_h) * 4 + c]

                val = (
                    a * (1 - x_weight) * (1 - y_weight)
                    + b * x_weight * (1 - y_weight)
                    + c_val * (1 - x_weight) * y_weight
                    + d * x_weight * y_weight
                )
                dst[dst_idx + c] = min(255, max(0, int(round(val))))

    return dst


def place_on_canvas(
    sprite_rgba: bytearray,
    sprite_w: int,
    sprite_h: int,
    canvas_w: int = 320,
    canvas_h: int = 320,
    offset_x: int = 0,
    offset_y: int = 0
) -> bytearray:
    canvas = bytearray(canvas_w * canvas_h * 4)
    for y in range(sprite_h):
        cy = offset_y + y
        if not (0 <= cy < canvas_h):
            continue
        for x in range(sprite_w):
            cx = offset_x + x
            if not (0 <= cx < canvas_w):
                continue
            s_idx = (y * sprite_w + x) * 4
            c_idx = (cy * canvas_w + cx) * 4
            sa = sprite_rgba[s_idx + 3]
            if sa == 0:
                continue
            if sa == 255:
                canvas[c_idx:c_idx + 4] = sprite_rgba[s_idx:s_idx + 4]
            else:
                da = canvas[c_idx + 3]
                out_a = sa + int(round(da * (255 - sa) / 255.0))
                if out_a > 0:
                    for c in range(3):
                        sc = sprite_rgba[s_idx + c]
                        dc = canvas[c_idx + c]
                        canvas[c_idx + c] = min(255, (sc * sa + dc * da * (255 - sa) // 255) // out_a)
                    canvas[c_idx + 3] = out_a

    return canvas


def main():
    project_root = Path(__file__).resolve().parents[1]
    source_png = project_root / "artwork/pet_pounce_cycle_source.png"
    output_dir = project_root / "app/src/main/res/drawable-nodpi"
    output_dir.mkdir(parents=True, exist_ok=True)

    src_w, src_h, src_rgba = read_png_rgba(str(source_png))
    print(f"Reading 3x2 Pounce Sheet: {src_w}x{src_h}")

    # 3 columns, 2 rows
    COLS = 3
    ROWS = 2
    col_w = src_w // COLS  # ~341px
    row_h = src_h // ROWS  # 512px
    CELL_OVERLAP = 40

    frame_names = [
        "pet_pounce_01",    # Row 1 Col 1: Stalking low
        "pet_pounce_02",    # Row 1 Col 2: Butt wiggle crouch
        "pet_pounce_03",    # Row 1 Col 3: High leap swat (airborne)
        "pet_pounce_04",    # Row 2 Col 1: Floor landing belly-down
        "pet_confused_01",  # Row 2 Col 2: Sitting empty paws
        "pet_confused_02",  # Row 2 Col 3: Head tilt scratch ear
    ]

    cells_coords = []
    for r in range(ROWS):
        y1 = max(0, r * row_h - CELL_OVERLAP)
        y2 = min(src_h, (r + 1) * row_h + CELL_OVERLAP)
        for c in range(COLS):
            x1 = max(0, c * col_w - CELL_OVERLAP)
            x2 = min(src_w, (c + 1) * col_w + CELL_OVERLAP)
            idx = r * COLS + c
            cells_coords.append((frame_names[idx], x1, y1, x2, y2))

    raw_frames = []
    for name, x1, y1, x2, y2 in cells_coords:
        cw, ch, c_rgba = crop_image(src_rgba, src_w, src_h, x1, y1, x2, y2)
        remove_black_background(c_rgba, cw, ch)
        bbox = isolate_largest_component(c_rgba, cw, ch)
        assert bbox is not None, f"No sprite component in {name}"
        raw_frames.append((name, cw, ch, c_rgba, bbox))
        print(f"Cell [{name}]: BBox={bbox}, Size={bbox[2]-bbox[0]}x{bbox[3]-bbox[1]}")

    # Ground level across grounded frames (Frame 1, 2, 4, 5, 6)
    grounded_ys_r1 = [raw_frames[0][4][3], raw_frames[1][4][3]]
    grounded_ys_r2 = [raw_frames[3][4][3], raw_frames[4][4][3], raw_frames[5][4][3]]
    avg_ground_r1 = sum(grounded_ys_r1) / len(grounded_ys_r1)
    avg_ground_r2 = sum(grounded_ys_r2) / len(grounded_ys_r2)
    print(f"Row 1 Ground: {avg_ground_r1:.1f}px, Row 2 Ground: {avg_ground_r2:.1f}px")

    # In standing/sitting cat (Frame 5 / Row 2 Col 2), cat height is ~308px
    # Target sitting/standing height in 320x320 canvas is 288.0px (matching pet_sit_v2_01 / pet_idle_v2_01 exactly)
    standing_h = raw_frames[4][4][3] - raw_frames[4][4][1]
    global_scale = 288.0 / standing_h
    print(f"Uniform Global Scale Factor: {global_scale:.4f} (Target Sitting Height = 288px)")

    for index, (name, cw, ch, c_rgba, bbox) in enumerate(raw_frames):
        min_x, min_y, max_x, max_y = bbox
        bw = max_x - min_x
        bh = max_y - min_y

        _, _, tight_rgba = crop_image(c_rgba, cw, ch, min_x, min_y, max_x, max_y)

        scaled_w = max(1, int(round(bw * global_scale)))
        scaled_h = max(1, int(round(bh * global_scale)))
        scaled_rgba = bilinear_scale(tight_rgba, bw, bh, scaled_w, scaled_h)

        place_x = (CANVAS_SIZE - scaled_w) // 2

        # Airborne calculation for leap frame (Frame 3 / index 2)
        if index == 2:
            airborne_px = avg_ground_r1 - max_y
            scaled_airborne = int(round(airborne_px * global_scale))
        else:
            scaled_airborne = 0

        place_y = TARGET_GROUND_Y - scaled_h - scaled_airborne

        # Clamp
        place_x = max(0, min(CANVAS_SIZE - scaled_w, place_x))
        place_y = max(0, min(CANVAS_SIZE - scaled_h, place_y))

        final_canvas = place_on_canvas(
            scaled_rgba, scaled_w, scaled_h,
            canvas_w=CANVAS_SIZE, canvas_h=CANVAS_SIZE,
            offset_x=place_x, offset_y=place_y
        )

        out_path = output_dir / f"{name}.png"
        write_png_rgba(str(out_path), CANVAS_SIZE, CANVAS_SIZE, final_canvas)
        print(f"Exported: {out_path.name} ({scaled_w}x{scaled_h} at X={place_x}, Y={place_y}, airborne={scaled_airborne}px)")

    print("\nAll 6 pounce and confused frames exported successfully!")


if __name__ == "__main__":
    main()
