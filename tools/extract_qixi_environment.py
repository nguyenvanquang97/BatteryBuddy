#!/usr/bin/env python3
"""Extract Qixi ambient status-bar decorations and magpie flight frames."""

from dataclasses import dataclass
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

from extract_pet_sprites import create_contact_sheet


def trim_transparency(image: Image.Image) -> Image.Image:
    bounds = image.getchannel("A").getbbox()
    if bounds is None:
        raise ValueError("Image does not contain visible pixels")
    return image.crop(bounds)


@dataclass(frozen=True)
class QixiAsset:
    name: str
    crop: tuple[int, int, int, int]


ASSETS = (
    QixiAsset("qixi_moon", (0, 0, 465, 390)),
    QixiAsset("qixi_bridge", (500, 70, 1145, 355)),
    QixiAsset("qixi_badge", (1255, 35, 1765, 355)),
    QixiAsset("qixi_sparkles", (1240, 390, 1765, 685)),
    QixiAsset("qixi_petals", (55, 720, 755, 845)),
    QixiAsset("qixi_cloud_01", (850, 705, 1225, 850)),
    QixiAsset("qixi_cloud_02", (1250, 690, 1765, 860)),
    QixiAsset("qixi_magpie_01", (0, 390, 330, 685)),
    QixiAsset("qixi_magpie_02", (350, 395, 615, 690)),
    QixiAsset("qixi_magpie_03", (645, 390, 905, 690)),
    QixiAsset("qixi_magpie_04", (890, 420, 1165, 690)),
)


def fit_inside(image: Image.Image, max_width: int, max_height: int) -> Image.Image:
    scale = min(max_width / image.width, max_height / image.height, 1.0)
    width = max(1, round(image.width * scale))
    height = max(1, round(image.height * scale))
    return image.resize((width, height), Image.Resampling.LANCZOS)


def create_preview(outputs: list[tuple[str, Image.Image]], output_path: Path) -> None:
    columns = 4
    cell_width = 320
    cell_height = 220
    label_height = 28
    rows = (len(outputs) + columns - 1) // columns
    canvas = Image.new(
        "RGB",
        (columns * cell_width, rows * (cell_height + label_height)),
        (35, 39, 47),
    )
    draw = ImageDraw.Draw(canvas)
    font = ImageFont.load_default(size=18)

    for index, (name, sprite) in enumerate(outputs):
        column = index % columns
        row = index // columns
        x = column * cell_width
        y = row * (cell_height + label_height)
        checker = Image.new("RGB", (cell_width, cell_height), (245, 245, 245))
        checker.paste((225, 225, 225), (0, cell_height // 2, cell_width, cell_height))
        checker.paste((225, 225, 225), (cell_width // 2, 0, cell_width, cell_height // 2))
        preview = fit_inside(sprite, cell_width - 20, cell_height - 20)
        paste_x = x + (cell_width - preview.width) // 2
        paste_y = y + (cell_height - preview.height) // 2
        checker.paste(preview, (paste_x - x, paste_y - y), preview)
        canvas.paste(checker, (x, y))
        draw.text(
            (x + 8, y + cell_height + 5),
            name,
            fill=(255, 255, 255),
            font=font,
        )

    output_path.parent.mkdir(parents=True, exist_ok=True)
    canvas.save(output_path, optimize=True)


def main() -> None:
    project_root = Path(__file__).resolve().parents[1]
    source_path = project_root / "artwork/qixi_environment_source.png"
    output_dir = project_root / "app/src/main/res/drawable-nodpi"
    preview_path = project_root / "artifacts/qixi-environment-contact-sheet.png"

    source = Image.open(source_path).convert("RGBA")
    outputs: list[tuple[str, Image.Image]] = []

    for asset in ASSETS:
        sprite = trim_transparency(source.crop(asset.crop))
        sprite.save(output_dir / f"{asset.name}.png", optimize=True)
        outputs.append((asset.name, sprite))

    create_preview(outputs, preview_path)
    create_contact_sheet(
        [(name, fit_inside(sprite, 288, 288)) for name, sprite in outputs],
        project_root / "artifacts/qixi-environment-normalized-contact-sheet.png",
        columns=4,
    )
    print(f"Exported {len(outputs)} Qixi ambient assets to {output_dir}")
    print(f"Contact sheet: {preview_path}")


if __name__ == "__main__":
    main()
