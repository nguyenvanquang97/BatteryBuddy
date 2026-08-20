#!/usr/bin/env python3
"""Extract, remove black background, normalize scale, and export pet run cycle sprites."""

import os
import struct
import zlib
from pathlib import Path


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

    assert data[:8] == b"\x89PNG\r\n\x1a\n", "Not a valid PNG file"
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

        # Unfilter scanline
        if filter_type == 0:
            pass
        elif filter_type == 1:  # Sub
            for x in range(bpp, stride):
                curr_scanline[x] = (curr_scanline[x] + curr_scanline[x - bpp]) & 0xFF
        elif filter_type == 2:  # Up
            for x in range(stride):
                curr_scanline[x] = (curr_scanline[x] + prev_scanline[x]) & 0xFF
        elif filter_type == 3:  # Average
            for x in range(stride):
                left = curr_scanline[x - bpp] if x >= bpp else 0
                up = prev_scanline[x]
                curr_scanline[x] = (curr_scanline[x] + ((left + up) // 2)) & 0xFF
        elif filter_type == 4:  # Paeth
            for x in range(stride):
                left = curr_scanline[x - bpp] if x >= bpp else 0
                up = prev_scanline[x]
                up_left = prev_scanline[x - bpp] if x >= bpp else 0
                curr_scanline[x] = (curr_scanline[x] + paeth_predictor(left, up, up_left)) & 0xFF
        else:
            raise ValueError(f"Unknown filter type: {filter_type}")

        prev_scanline = curr_scanline[:]

        # Convert to RGBA
        if color_type == 6:
            raw_rgba[rgba_offset:rgba_offset+stride] = curr_scanline
            rgba_offset += stride
        else:  # RGB -> RGBA
            for x in range(width):
                r = curr_scanline[x * 3]
                g = curr_scanline[x * 3 + 1]
                b = curr_scanline[x * 3 + 2]
                raw_rgba[rgba_offset] = r
                raw_rgba[rgba_offset + 1] = g
                raw_rgba[rgba_offset + 2] = b
                raw_rgba[rgba_offset + 3] = 255
                rgba_offset += 4

    return width, height, raw_rgba


def write_png_rgba(path: str, width: int, height: int, rgba_bytes: bytearray) -> None:
    stride = width * 4
    raw_filtered = bytearray()

    for y in range(height):
        raw_filtered.append(0)  # Filter type 0 (None)
        raw_filtered.extend(rgba_bytes[y * stride:(y + 1) * stride])

    compressed = zlib.compress(bytes(raw_filtered), level=9)

    ihdr = struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0)
    ihdr_crc = zlib.crc32(b"IHDR" + ihdr)
    idat_crc = zlib.crc32(b"IDAT" + compressed)
    iend_crc = zlib.crc32(b"IEND")

    with open(path, "wb") as f:
        f.write(b"\x89PNG\r\n\x1a\n")
        # IHDR
        f.write(struct.pack(">I4s", len(ihdr), b"IHDR"))
        f.write(ihdr)
        f.write(struct.pack(">I", ihdr_crc))
        # IDAT
        f.write(struct.pack(">I4s", len(compressed), b"IDAT"))
        f.write(compressed)
        f.write(struct.pack(">I", idat_crc))
        # IEND
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
            # Smooth feathered alpha
            alpha_fraction = (max_c - 15.0) / (48.0 - 15.0)
            a = int(round(alpha_fraction * 255))
            rgba[i + 3] = a
            # Color un-premultiply to avoid dark fringe
            factor = 255.0 / a if a > 0 else 1.0
            rgba[i] = min(255, int(round(r * factor)))
            rgba[i + 1] = min(255, int(round(g * factor)))
            rgba[i + 2] = min(255, int(round(b * factor)))
        else:
            rgba[i + 3] = 255


from typing import Optional

def get_bounding_box(rgba: bytearray, width: int, height: int) -> Optional[tuple[int, int, int, int]]:
    min_x, min_y = width, height
    max_x, max_y = -1, -1

    for y in range(height):
        for x in range(width):
            a = rgba[(y * width + x) * 4 + 3]
            if a > 20:
                if x < min_x: min_x = x
                if x > max_x: max_x = x
                if y < min_y: min_y = y
                if y > max_y: max_y = y

    if max_x < 0:
        return None
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
                # Alpha composite
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
    source_png = project_root / "artwork/pet_run_cycle_source.png"
    output_dir = project_root / "app/src/main/res/drawable-nodpi"
    output_dir.mkdir(parents=True, exist_ok=True)

    print(f"Reading source sprite sheet: {source_png}")
    src_w, src_h, src_rgba = read_png_rgba(str(source_png))
    print(f"Source size: {src_w} x {src_h}")

    # The sheet is 2x2 grid (1024x1024). Each cell is 512x512.
    cell_w = src_w // 2
    cell_h = src_h // 2

    cells_coords = [
        ("pet_run_01", 0, 0, cell_w, cell_h),           # Frame 1: Top-Left (Crouch)
        ("pet_run_02", cell_w, 0, src_w, cell_h),       # Frame 2: Top-Right (Leap stretch)
        ("pet_run_03", 0, cell_h, cell_w, src_h),       # Frame 3: Bottom-Left (Front land)
        ("pet_run_04", cell_w, cell_h, src_w, src_h),   # Frame 4: Bottom-Right (Back swing)
    ]

    # Extract cells and clean black background
    raw_frames = []
    cell_ground_ys = []
    cell_head_heights = []

    for name, x1, y1, x2, y2 in cells_coords:
        cw, ch, c_rgba = crop_image(src_rgba, src_w, src_h, x1, y1, x2, y2)
        remove_black_background(c_rgba, cw, ch)
        bbox = get_bounding_box(c_rgba, cw, ch)
        assert bbox is not None, f"No sprite detected in cell {name}"
        raw_frames.append((name, cw, ch, c_rgba, bbox))
        # bbox is (min_x, min_y, max_x, max_y)
        cell_ground_ys.append(bbox[3])
        cell_head_heights.append(bbox[3] - bbox[1])
        print(f"Cell {name}: BBox={bbox}, Width={bbox[2]-bbox[0]}, Height={bbox[3]-bbox[1]}")

    # Ground level across grounded frames (Frame 1, 3, 4)
    grounded_ys = [raw_frames[0][4][3], raw_frames[2][4][3], raw_frames[3][4][3]]
    avg_ground_y = sum(grounded_ys) / len(grounded_ys)
    print(f"Average Ground Baseline in 512px cell: {avg_ground_y:.1f}px")

    # Find max width and height across all frames to ensure zero clipping
    max_frame_w = max(f[4][2] - f[4][0] for f in raw_frames)
    max_frame_h = max(f[4][3] - f[4][1] for f in raw_frames)

    # In 320x320 canvas with padding (max allowed width = 296, max allowed height = 280)
    MAX_ALLOWED_W = 296.0
    MAX_ALLOWED_H = 280.0
    global_scale = min(MAX_ALLOWED_W / max_frame_w, MAX_ALLOWED_H / max_frame_h)
    print(f"Uniform Global Scale Factor: {global_scale:.4f} (Max frame fits within {MAX_ALLOWED_W}x{MAX_ALLOWED_H})")

    # Standard Target Ground Y in 320x320 canvas
    CANVAS_SIZE = 320
    BOTTOM_MARGIN = 12
    TARGET_GROUND_Y = CANVAS_SIZE - BOTTOM_MARGIN  # 308px

    # Ground level in source 512px cell based on grounded frames (Frame 1, 3, 4)
    ground_y_source = max(raw_frames[0][4][3], raw_frames[2][4][3], raw_frames[3][4][3])

    for index, (name, cw, ch, c_rgba, bbox) in enumerate(raw_frames):
        min_x, min_y, max_x, max_y = bbox
        bw = max_x - min_x
        bh = max_y - min_y

        # Crop tight around bounding box
        _, _, tight_rgba = crop_image(c_rgba, cw, ch, min_x, min_y, max_x, max_y)

        # Scale using global scale factor
        scaled_w = max(1, int(round(bw * global_scale)))
        scaled_h = max(1, int(round(bh * global_scale)))
        scaled_rgba = bilinear_scale(tight_rgba, bw, bh, scaled_w, scaled_h)

        # Center horizontally
        place_x = (CANVAS_SIZE - scaled_w) // 2

        # Y position: align to ground baseline
        # Airborne frame (Frame 2 / index 1): keep relative distance from ground
        airborne_px = ground_y_source - max_y if index == 1 else 0
        scaled_airborne = int(round(airborne_px * global_scale))

        place_y = TARGET_GROUND_Y - scaled_h - scaled_airborne

        # Clamp within canvas
        place_x = max(0, min(CANVAS_SIZE - scaled_w, place_x))
        place_y = max(0, min(CANVAS_SIZE - scaled_h, place_y))

        final_canvas = place_on_canvas(
            scaled_rgba, scaled_w, scaled_h,
            canvas_w=CANVAS_SIZE, canvas_h=CANVAS_SIZE,
            offset_x=place_x, offset_y=place_y
        )

        out_path = output_dir / f"{name}.png"
        write_png_rgba(str(out_path), CANVAS_SIZE, CANVAS_SIZE, final_canvas)
        print(f"Saved: {out_path} ({scaled_w}x{scaled_h} placed at X={place_x}, Y={place_y}, airborne={scaled_airborne}px)")

    print("\nAll 4 pet_run frames successfully exported with 1:1 scale matching!")


if __name__ == "__main__":
    main()
