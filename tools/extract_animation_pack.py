#!/usr/bin/env python3
"""Extract the 4x4 sleep, charging, sit-down, and front-look pack."""

from pathlib import Path

from PIL import Image

from extract_pet_sprites import (
    create_contact_sheet,
    isolate_largest_component,
    normalize,
)


GRID_SIZE = 4
CELL_OVERLAP = 24
ROW_PREFIXES = (
    "pet_sleep_v2",
    "pet_charging_v2",
    "pet_sit_down",
    "pet_look_front",
)


def grid_edge(index: int, length: int) -> int:
    return round(index * length / GRID_SIZE)


def main() -> None:
    project_root = Path(__file__).resolve().parents[1]
    source_path = project_root / "artwork/pet_animation_pack_source.png"
    output_dir = project_root / "app/src/main/res/drawable-nodpi"
    contact_sheet_path = project_root / "artifacts/pet-animation-pack-contact-sheet.png"

    source = Image.open(source_path).convert("RGBA")
    outputs: list[tuple[str, Image.Image]] = []

    for row, prefix in enumerate(ROW_PREFIXES):
        top = max(0, grid_edge(row, source.height) - CELL_OVERLAP)
        bottom = min(source.height, grid_edge(row + 1, source.height) + CELL_OVERLAP)

        for column in range(GRID_SIZE):
            left = max(0, grid_edge(column, source.width) - CELL_OVERLAP)
            right = min(source.width, grid_edge(column + 1, source.width) + CELL_OVERLAP)
            cell = source.crop((left, top, right, bottom))
            frame = normalize(isolate_largest_component(cell))
            name = f"{prefix}_{column + 1:02d}"
            frame.save(output_dir / f"{name}.png", optimize=True)
            outputs.append((name, frame))

    create_contact_sheet(outputs, contact_sheet_path, columns=GRID_SIZE)
    print(f"Exported {len(outputs)} animation frames to {output_dir}")
    print(f"Contact sheet: {contact_sheet_path}")


if __name__ == "__main__":
    main()
