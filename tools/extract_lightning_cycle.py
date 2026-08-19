#!/usr/bin/env python3
"""Extract the 2x4 lightning-hit and shocked animation sheet."""

from pathlib import Path

from PIL import Image

from extract_drink_cycle import grid_edge, isolate_meaningful_components
from extract_pet_sprites import create_contact_sheet, normalize


ROWS = 2
COLUMNS = 4
ROW_PREFIXES = ("pet_lightning_hit", "pet_shocked")


def main() -> None:
    project_root = Path(__file__).resolve().parents[1]
    source_path = project_root / "artwork/pet_lightning_cycle_source.png"
    output_dir = project_root / "app/src/main/res/drawable-nodpi"
    contact_sheet_path = project_root / "artifacts/pet-lightning-cycle-contact-sheet.png"

    source = Image.open(source_path).convert("RGBA")
    outputs: list[tuple[str, Image.Image]] = []

    for row, prefix in enumerate(ROW_PREFIXES):
        top = grid_edge(row, ROWS, source.height)
        bottom = grid_edge(row + 1, ROWS, source.height)
        for column in range(COLUMNS):
            left = grid_edge(column, COLUMNS, source.width)
            right = grid_edge(column + 1, COLUMNS, source.width)
            frame = source.crop((left, top, right, bottom))
            isolated = isolate_meaningful_components(frame, max_components=16)
            normalized = normalize(isolated)
            name = f"{prefix}_{column + 1:02d}"
            normalized.save(output_dir / f"{name}.png", optimize=True)
            outputs.append((name, normalized))

    create_contact_sheet(outputs, contact_sheet_path, columns=COLUMNS)
    print(f"Exported {len(outputs)} lightning frames to {output_dir}")
    print(f"Contact sheet: {contact_sheet_path}")


if __name__ == "__main__":
    main()
