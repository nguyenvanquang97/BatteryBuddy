#!/usr/bin/env python3
"""Extract the four-frame v2 idle and sit animation strips."""

from pathlib import Path

from PIL import Image

from extract_pet_sprites import (
    create_contact_sheet,
    isolate_largest_component,
    normalize,
)


FRAME_COUNT = 4


def extract_cycle(
    source_path: Path,
    output_dir: Path,
    output_prefix: str,
    contact_sheet_path: Path,
) -> None:
    source = Image.open(source_path).convert("RGBA")
    if source.width % FRAME_COUNT != 0:
        raise ValueError(f"Source width {source.width} is not divisible by {FRAME_COUNT}")

    cell_width = source.width // FRAME_COUNT
    outputs: list[tuple[str, Image.Image]] = []

    for index in range(FRAME_COUNT):
        cell = source.crop(
            (index * cell_width, 0, (index + 1) * cell_width, source.height)
        )
        frame = normalize(isolate_largest_component(cell))
        name = f"{output_prefix}_{index + 1:02d}"
        frame.save(output_dir / f"{name}.png", optimize=True)
        outputs.append((name, frame))

    create_contact_sheet(outputs, contact_sheet_path)


def main() -> None:
    project_root = Path(__file__).resolve().parents[1]
    artwork_dir = project_root / "artwork"
    output_dir = project_root / "app/src/main/res/drawable-nodpi"
    artifacts_dir = project_root / "artifacts"

    extract_cycle(
        artwork_dir / "pet_idle_cycle_source.png",
        output_dir,
        "pet_idle_v2",
        artifacts_dir / "pet-idle-v2-contact-sheet.png",
    )
    extract_cycle(
        artwork_dir / "pet_sit_cycle_source.png",
        output_dir,
        "pet_sit_v2",
        artifacts_dir / "pet-sit-v2-contact-sheet.png",
    )

    print(f"Exported IDLE and SIT v2 frames to {output_dir}")


if __name__ == "__main__":
    main()
