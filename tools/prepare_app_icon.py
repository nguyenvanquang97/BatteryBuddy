#!/usr/bin/env python3
"""Prepare the Status Cat adaptive launcher foreground and visual preview."""

from pathlib import Path

from PIL import Image, ImageDraw


CANVAS_SIZE = 432
ART_SIZE = 372
PREVIEW_SIZE = 256


def fit_art(source: Image.Image) -> Image.Image:
    bounds = source.getchannel("A").getbbox()
    if bounds is None:
        raise ValueError("Logo source does not contain visible pixels")
    source = source.crop(bounds)
    scale = min(ART_SIZE / source.width, ART_SIZE / source.height)
    resized = source.resize(
        (round(source.width * scale), round(source.height * scale)),
        Image.Resampling.LANCZOS,
    )
    canvas = Image.new("RGBA", (CANVAS_SIZE, CANVAS_SIZE), (0, 0, 0, 0))
    canvas.alpha_composite(
        resized,
        ((CANVAS_SIZE - resized.width) // 2, (CANVAS_SIZE - resized.height) // 2),
    )
    return canvas


def masked_preview(icon: Image.Image, shape: str) -> Image.Image:
    icon = icon.resize((PREVIEW_SIZE, PREVIEW_SIZE), Image.Resampling.LANCZOS)
    background = Image.new("RGBA", icon.size, "#173EAA")
    background.alpha_composite(icon)
    mask = Image.new("L", icon.size, 0)
    draw = ImageDraw.Draw(mask)
    if shape == "circle":
        draw.ellipse((0, 0, PREVIEW_SIZE, PREVIEW_SIZE), fill=255)
    else:
        draw.rounded_rectangle(
            (0, 0, PREVIEW_SIZE, PREVIEW_SIZE),
            radius=PREVIEW_SIZE // 5,
            fill=255,
        )
    output = Image.new("RGBA", icon.size, (0, 0, 0, 0))
    output.paste(background, mask=mask)
    return output


def main() -> None:
    project_root = Path(__file__).resolve().parents[1]
    source_path = project_root / "artwork/status_cat_logo_source.png"
    output_path = project_root / "app/src/main/res/drawable-nodpi/status_cat_launcher_art.png"
    preview_path = project_root / "artifacts/status-cat-icon-preview.png"

    icon = fit_art(Image.open(source_path).convert("RGBA"))
    icon.save(output_path, optimize=True)

    preview = Image.new("RGB", (PREVIEW_SIZE * 2 + 48, PREVIEW_SIZE + 32), "#F0F2F7")
    preview.paste(masked_preview(icon, "rounded"), (16, 16), masked_preview(icon, "rounded"))
    preview.paste(masked_preview(icon, "circle"), (PREVIEW_SIZE + 32, 16), masked_preview(icon, "circle"))
    preview.save(preview_path, optimize=True)
    print(f"Launcher art: {output_path}")
    print(f"Preview: {preview_path}")


if __name__ == "__main__":
    main()
