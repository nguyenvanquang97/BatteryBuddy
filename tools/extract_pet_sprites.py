#!/usr/bin/env python3
"""Extract and normalize BatteryBuddy pet sprites from the source sheet."""

from dataclasses import dataclass
from collections import deque
from pathlib import Path
import sys

from PIL import Image, ImageChops, ImageDraw, ImageFont


CANVAS_SIZE = 320
CONTENT_SIZE = 288
BOTTOM_MARGIN = 12


@dataclass(frozen=True)
class Sprite:
    name: str
    crop: tuple[int, int, int, int]


SPRITES = (
    Sprite("pet_idle_01", (0, 620, 200, 835)),
    Sprite("pet_idle_02", (192, 620, 405, 840)),
    Sprite("pet_walk_01", (455, 0, 865, 465)),
    Sprite("pet_walk_02", (375, 620, 620, 840)),
    Sprite("pet_sit", (50, 0, 465, 465)),
    Sprite("pet_sleep_01", (1045, 835, 1254, 1055)),
    Sprite("pet_sleep_02", (1035, 620, 1254, 845)),
    Sprite("pet_charging_01", (410, 445, 630, 635)),
    Sprite("pet_charging_02", (600, 620, 840, 840)),
)


def isolate_largest_component(image: Image.Image) -> Image.Image:
    alpha = image.getchannel("A")
    visible = alpha.point(lambda value: 255 if value > 10 else 0)
    width, height = visible.size
    pixels = visible.load()
    visited = bytearray(width * height)
    largest: list[int] = []

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

            if len(component) > len(largest):
                largest = component

    if not largest:
        raise ValueError("Crop does not contain visible pixels")

    component_mask = Image.new("L", (width, height), 0)
    component_pixels = bytearray(width * height)
    for index in largest:
        component_pixels[index] = 255
    component_mask.frombytes(bytes(component_pixels))

    isolated = image.copy()
    isolated.putalpha(ImageChops.multiply(alpha, component_mask))
    bounds = component_mask.getbbox()
    if bounds is None:
        raise ValueError("Crop does not contain visible pixels")
    return isolated.crop(bounds)


def normalize(image: Image.Image) -> Image.Image:
    scale = min(CONTENT_SIZE / image.width, CONTENT_SIZE / image.height)
    width = max(1, round(image.width * scale))
    height = max(1, round(image.height * scale))
    resized = image.resize((width, height), Image.Resampling.LANCZOS)

    canvas = Image.new("RGBA", (CANVAS_SIZE, CANVAS_SIZE), (0, 0, 0, 0))
    x = (CANVAS_SIZE - width) // 2
    y = CANVAS_SIZE - BOTTOM_MARGIN - height
    canvas.alpha_composite(resized, (x, y))
    return canvas


def create_contact_sheet(
    outputs: list[tuple[str, Image.Image]],
    path: Path,
    columns: int = 3,
) -> None:
    label_height = 32
    rows = (len(outputs) + columns - 1) // columns
    sheet = Image.new(
        "RGB",
        (columns * CANVAS_SIZE, rows * (CANVAS_SIZE + label_height)),
        (35, 39, 47),
    )
    draw = ImageDraw.Draw(sheet)
    font = ImageFont.load_default(size=18)

    for index, (name, sprite) in enumerate(outputs):
        column = index % columns
        row = index // columns
        x = column * CANVAS_SIZE
        y = row * (CANVAS_SIZE + label_height)
        checker = Image.new("RGB", (CANVAS_SIZE, CANVAS_SIZE), (245, 245, 245))
        checker.paste((225, 225, 225), (0, CANVAS_SIZE // 2, CANVAS_SIZE, CANVAS_SIZE))
        checker.paste((225, 225, 225), (CANVAS_SIZE // 2, 0, CANVAS_SIZE, CANVAS_SIZE // 2))
        checker.paste(sprite, mask=sprite.getchannel("A"))
        sheet.paste(checker, (x, y))
        draw.text((x + 8, y + CANVAS_SIZE + 6), name, fill=(255, 255, 255), font=font)

    path.parent.mkdir(parents=True, exist_ok=True)
    sheet.save(path, optimize=True)


def main() -> None:
    project_root = Path(__file__).resolve().parents[1]
    source_path = Path(sys.argv[1]) if len(sys.argv) > 1 else project_root / "ChatGPT Image 17_07_44 18 thg 8, 2026.png"
    output_dir = project_root / "app/src/main/res/drawable-nodpi"
    contact_sheet_path = project_root / "artifacts/pet-sprites-contact-sheet.png"

    source = Image.open(source_path).convert("RGBA")
    output_dir.mkdir(parents=True, exist_ok=True)
    outputs: list[tuple[str, Image.Image]] = []

    for sprite in SPRITES:
        cropped = isolate_largest_component(source.crop(sprite.crop))
        normalized = normalize(cropped)
        normalized.save(output_dir / f"{sprite.name}.png", optimize=True)
        outputs.append((sprite.name, normalized))

    create_contact_sheet(outputs, contact_sheet_path)
    print(f"Exported {len(outputs)} sprites to {output_dir}")
    print(f"Contact sheet: {contact_sheet_path}")


if __name__ == "__main__":
    main()
