#!/usr/bin/env python3
"""Extract the Qixi backdrop and four-frame magpie flight cycle."""

from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

from extract_pet_sprites import isolate_largest_component, normalize


BACKGROUND_BOTTOM = 480
BACKGROUND_WIDTH = 1024
MAGPIE_COLUMNS = 4


def trim_transparency(image: Image.Image) -> Image.Image:
    bounds = image.getchannel("A").getbbox()
    if bounds is None:
        raise ValueError("Image does not contain visible pixels")
    return image.crop(bounds)


def create_preview(
    background: Image.Image,
    magpies: list[Image.Image],
    output_path: Path,
) -> None:
    width = 1280
    background_height = round(width * background.height / background.width)
    label_height = 32
    canvas = Image.new("RGB", (width, background_height + 320 + label_height), (35, 39, 47))
    draw = ImageDraw.Draw(canvas)
    font = ImageFont.load_default(size=18)

    backdrop = background.resize((width, background_height), Image.Resampling.LANCZOS)
    canvas.paste(backdrop, (0, 0), backdrop)
    for index, magpie in enumerate(magpies):
        canvas.paste(magpie, (index * 320, background_height), magpie)
        draw.text(
            (index * 320 + 8, background_height + 320 + 6),
            f"qixi_magpie_{index + 1:02d}",
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
    background = trim_transparency(source.crop((0, 0, source.width, BACKGROUND_BOTTOM)))
    background_height = round(BACKGROUND_WIDTH * background.height / background.width)
    background = background.resize(
        (BACKGROUND_WIDTH, background_height),
        Image.Resampling.LANCZOS,
    )
    background.save(output_dir / "qixi_background.png", optimize=True)

    magpie_area = source.crop((0, BACKGROUND_BOTTOM, source.width, source.height))
    magpies: list[Image.Image] = []
    overlap = 8
    for column in range(MAGPIE_COLUMNS):
        left = max(0, round(column * source.width / MAGPIE_COLUMNS) - overlap)
        right = min(source.width, round((column + 1) * source.width / MAGPIE_COLUMNS) + overlap)
        magpie = normalize(isolate_largest_component(magpie_area.crop((left, 0, right, magpie_area.height))))
        magpie.save(output_dir / f"qixi_magpie_{column + 1:02d}.png", optimize=True)
        magpies.append(magpie)

    create_preview(background, magpies, preview_path)
    print(f"Exported Qixi background and {len(magpies)} magpie frames to {output_dir}")
    print(f"Contact sheet: {preview_path}")


if __name__ == "__main__":
    main()
