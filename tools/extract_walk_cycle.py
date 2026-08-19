#!/usr/bin/env python3
"""Extract the v2 walk cycle from its six-cell source strip."""

from pathlib import Path

from PIL import Image

from extract_pet_sprites import (
    create_contact_sheet,
    isolate_largest_component,
    normalize,
)


FRAME_COUNT = 6


def main() -> None:
    project_root = Path(__file__).resolve().parents[1]
    source_path = project_root / "artwork/pet_walk_cycle_source.png"
    output_dir = project_root / "app/src/main/res/drawable-nodpi"
    contact_sheet_path = project_root / "artifacts/pet-walk-v2-contact-sheet.png"

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
        name = "pet_walk_start_v2" if index == 0 else f"pet_walk_v2_{index:02d}"
        frame.save(output_dir / f"{name}.png", optimize=True)
        outputs.append((name, frame))

    create_contact_sheet(outputs, contact_sheet_path)
    print(f"Exported {len(outputs)} walk frames to {output_dir}")
    print(f"Contact sheet: {contact_sheet_path}")


if __name__ == "__main__":
    main()
