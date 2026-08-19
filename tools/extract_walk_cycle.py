#!/usr/bin/env python3
"""Extract the v2 walk cycle from its six-cell source strip."""

from pathlib import Path
from collections import deque

from PIL import Image, ImageChops

from extract_pet_sprites import (
    create_contact_sheet,
    normalize,
)


FRAME_COUNT = 6
ALPHA_THRESHOLD = 10
MIN_COMPONENT_PIXELS = 1_000


def extract_main_components(source: Image.Image) -> list[Image.Image]:
    alpha = source.getchannel("A")
    visible = alpha.point(lambda value: 255 if value > ALPHA_THRESHOLD else 0)
    width, height = visible.size
    pixels = visible.load()
    visited = bytearray(width * height)
    components: list[tuple[int, tuple[int, int, int, int], bytearray]] = []

    for y in range(height):
        for x in range(width):
            start = y * width + x
            if visited[start] or pixels[x, y] == 0:
                continue

            queue = deque([(x, y)])
            visited[start] = 1
            mask_pixels = bytearray(width * height)
            min_x = max_x = x
            min_y = max_y = y
            count = 0

            while queue:
                current_x, current_y = queue.popleft()
                current_index = current_y * width + current_x
                mask_pixels[current_index] = 255
                count += 1
                min_x = min(min_x, current_x)
                max_x = max(max_x, current_x)
                min_y = min(min_y, current_y)
                max_y = max(max_y, current_y)

                for next_x, next_y in (
                    (current_x - 1, current_y),
                    (current_x + 1, current_y),
                    (current_x, current_y - 1),
                    (current_x, current_y + 1),
                ):
                    if not (0 <= next_x < width and 0 <= next_y < height):
                        continue
                    next_index = next_y * width + next_x
                    if visited[next_index] or pixels[next_x, next_y] == 0:
                        continue
                    visited[next_index] = 1
                    queue.append((next_x, next_y))

            if count >= MIN_COMPONENT_PIXELS:
                components.append(
                    (count, (min_x, min_y, max_x + 1, max_y + 1), mask_pixels)
                )

    if len(components) < FRAME_COUNT:
        raise ValueError(f"Expected at least {FRAME_COUNT} walk frames, found {len(components)}")

    main_components = sorted(components, reverse=True)[:FRAME_COUNT]
    frames: list[tuple[tuple[int, int, int, int], Image.Image]] = []

    for _, bounds, mask_pixels in main_components:
        mask = Image.new("L", source.size, 0)
        mask.frombytes(bytes(mask_pixels))
        isolated = source.copy()
        isolated.putalpha(ImageChops.multiply(alpha, mask))
        frames.append((bounds, isolated.crop(bounds)))

    return [frame for _, frame in sorted(frames, key=lambda item: item[0][0])]


def main() -> None:
    project_root = Path(__file__).resolve().parents[1]
    source_path = project_root / "artwork/pet_walk_cycle_source.png"
    output_dir = project_root / "app/src/main/res/drawable-nodpi"
    contact_sheet_path = project_root / "artifacts/pet-walk-v2-contact-sheet.png"

    source = Image.open(source_path).convert("RGBA")
    outputs: list[tuple[str, Image.Image]] = []

    for index, raw_frame in enumerate(extract_main_components(source)):
        frame = normalize(raw_frame)
        name = "pet_walk_start_v2" if index == 0 else f"pet_walk_v2_{index:02d}"
        frame.save(output_dir / f"{name}.png", optimize=True)
        outputs.append((name, frame))

    create_contact_sheet(outputs, contact_sheet_path)
    print(f"Exported {len(outputs)} walk frames to {output_dir}")
    print(f"Contact sheet: {contact_sheet_path}")


if __name__ == "__main__":
    main()
