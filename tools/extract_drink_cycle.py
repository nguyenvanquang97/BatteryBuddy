#!/usr/bin/env python3
"""Extract the 2x4 charging milk animation while preserving the saucer."""

from collections import deque
from pathlib import Path

from PIL import Image, ImageChops

from extract_pet_sprites import create_contact_sheet, normalize


ROWS = 2
COLUMNS = 4
MAX_COMPONENTS = 2
ROW_PREFIXES = ("pet_drink_start", "pet_drink_milk")


def isolate_meaningful_components(
    image: Image.Image,
    max_components: int = MAX_COMPONENTS,
) -> Image.Image:
    alpha = image.getchannel("A")
    visible = alpha.point(lambda value: 255 if value > 10 else 0)
    width, height = visible.size
    pixels = visible.load()
    visited = bytearray(width * height)
    components: list[list[int]] = []

    for y in range(height):
        for x in range(width):
            start = y * width + x
            if visited[start] or pixels[x, y] == 0:
                continue

            component: list[int] = []
            queue = deque([(x, y)])
            visited[start] = 1
            while queue:
                current_x, current_y = queue.popleft()
                component.append(current_y * width + current_x)
                for next_x, next_y in (
                    (current_x - 1, current_y),
                    (current_x + 1, current_y),
                    (current_x, current_y - 1),
                    (current_x, current_y + 1),
                ):
                    if not (0 <= next_x < width and 0 <= next_y < height):
                        continue
                    index = next_y * width + next_x
                    if visited[index] or pixels[next_x, next_y] == 0:
                        continue
                    visited[index] = 1
                    queue.append((next_x, next_y))
            components.append(component)

    if not components:
        raise ValueError("Frame does not contain visible pixels")

    components.sort(key=len, reverse=True)
    minimum_size = max(16, len(components[0]) // 2000)

    def touches_border(component: list[int]) -> bool:
        return any(
            index % width in (0, width - 1) or index // width in (0, height - 1)
            for index in component
        )

    candidates = [components[0]] + [
        component for component in components[1:] if not touches_border(component)
    ]
    kept = [component for component in candidates[:max_components] if len(component) >= minimum_size]

    mask_bytes = bytearray(width * height)
    for component in kept:
        for index in component:
            mask_bytes[index] = 255

    mask = Image.new("L", (width, height), 0)
    mask.frombytes(bytes(mask_bytes))
    bounds = mask.getbbox()
    if bounds is None:
        raise ValueError("Frame does not contain visible pixels")

    isolated = image.copy()
    isolated.putalpha(ImageChops.multiply(alpha, mask))
    return isolated.crop(bounds)


def grid_edge(index: int, count: int, length: int) -> int:
    return round(index * length / count)


def main() -> None:
    project_root = Path(__file__).resolve().parents[1]
    source_path = project_root / "artwork/pet_drink_cycle_source.png"
    output_dir = project_root / "app/src/main/res/drawable-nodpi"
    contact_sheet_path = project_root / "artifacts/pet-drink-cycle-contact-sheet.png"

    source = Image.open(source_path).convert("RGBA")
    outputs: list[tuple[str, Image.Image]] = []

    for row, prefix in enumerate(ROW_PREFIXES):
        top = grid_edge(row, ROWS, source.height)
        bottom = grid_edge(row + 1, ROWS, source.height)
        for column in range(COLUMNS):
            left = grid_edge(column, COLUMNS, source.width)
            right = grid_edge(column + 1, COLUMNS, source.width)
            frame = source.crop((left, top, right, bottom))
            normalized = normalize(isolate_meaningful_components(frame))
            name = f"{prefix}_{column + 1:02d}"
            normalized.save(output_dir / f"{name}.png", optimize=True)
            outputs.append((name, normalized))

    create_contact_sheet(outputs, contact_sheet_path, columns=COLUMNS)
    print(f"Exported {len(outputs)} drink frames to {output_dir}")
    print(f"Contact sheet: {contact_sheet_path}")


if __name__ == "__main__":
    main()
