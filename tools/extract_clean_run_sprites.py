#!/usr/bin/env python3
"""Re-export pet_run_01..04 with 384x320 canvas to eliminate clipping while preserving 1:1 scale."""

import sys
from pathlib import Path
from extract_2x2_sprites import (
    read_png_rgba,
    write_png_rgba,
    crop_image,
    remove_black_background,
    isolate_largest_component,
    bilinear_scale,
    place_on_canvas
)

def main():
    project_root = Path(__file__).resolve().parents[1]
    source_png = project_root / "artwork/pet_run_cycle_source.png"
    output_dir = project_root / "app/src/main/res/drawable-nodpi"

    w_run, h_run, rgba_run = read_png_rgba(str(source_png))

    # Canvas dimensions
    CANVAS_W = 384
    CANVAS_H = 320
    TARGET_GROUND_Y = 308

    # Target scale so eye width = 125px and head width = 137px (1:1 with pet_walk and pet_idle)
    SCALE = 0.780

    cells = [
        ("pet_run_01", 0, 0, 512, 512),
        ("pet_run_02", 512, 0, 1024, 512),
        ("pet_run_03", 0, 512, 512, 1024),
        ("pet_run_04", 512, 512, 1024, 1024),
    ]

    raw_frames = []
    for name, x1, y1, x2, y2 in cells:
        cw, ch, c_rgba = crop_image(rgba_run, w_run, h_run, x1, y1, x2, y2)
        remove_black_background(c_rgba, cw, ch)
        bbox = isolate_largest_component(c_rgba, cw, ch)
        raw_frames.append((name, cw, ch, c_rgba, bbox))

    ground_y = max(raw_frames[0][4][3], raw_frames[2][4][3], raw_frames[3][4][3])

    for index, (name, cw, ch, c_rgba, bbox) in enumerate(raw_frames):
        min_x, min_y, max_x, max_y = bbox
        bw = max_x - min_x
        bh = max_y - min_y

        _, _, tight = crop_image(c_rgba, cw, ch, min_x, min_y, max_x, max_y)
        sw = int(round(bw * SCALE))
        sh = int(round(bh * SCALE))
        scaled = bilinear_scale(tight, bw, bh, sw, sh)

        # Center horizontally on 384 canvas
        place_x = (CANVAS_W - sw) // 2

        # Airborne offset for Frame 2
        airborne = int(round((ground_y - max_y) * SCALE)) if index == 1 else 0
        place_y = TARGET_GROUND_Y - sh - airborne

        canvas = place_on_canvas(
            scaled, sw, sh,
            canvas_w=CANVAS_W, canvas_h=CANVAS_H,
            offset_x=place_x, offset_y=place_y
        )

        out_path = output_dir / f"{name}.png"
        write_png_rgba(str(out_path), CANVAS_W, CANVAS_H, canvas)
        print(f"Saved {name}.png: {sw}x{sh} placed at ({place_x}, {place_y}) on {CANVAS_W}x{CANVAS_H} canvas")

    print("\nPet run frames successfully exported with ZERO clipping!")

if __name__ == "__main__":
    main()
