#!/usr/bin/env python3
"""The list of catalogue items still waiting for a photograph.

`image-coverage.py` answers "how many are missing" and prints their ids.
That is the right answer for a build check and the wrong one for a person
about to go and take 527 photographs: an id says nothing about what the thing
is, and a list of ids in three scripts is not something anybody can work from
on a phone in a builders' merchant.

So this writes the same set as a spreadsheet with the names in all three
languages, what the item is for, the word a site would actually shout for it
where the catalogue records one, and — the column that matters — the exact
filename to save the picture as. Drop the files into
shared/assets/catalog/images/ with those names and they are picked up; no code
changes, no ids to type.

    python3 tools/photo-worklist.py            # write the spreadsheet
    python3 tools/photo-worklist.py --summary  # just the counts per trade
"""

from __future__ import annotations

import argparse
import csv
import json
import pathlib
import sys
from collections import Counter

ROOT = pathlib.Path(__file__).resolve().parent.parent
CATALOG = ROOT / "shared" / "assets" / "catalog"
IMAGES = CATALOG / "images"
OUT = ROOT / "docs" / "translation" / "items-needing-photos.csv"
EXTENSIONS = (".webp", ".jpg", ".jpeg", ".png")

# What a photograph is for, per kind, so somebody sourcing them knows whether
# a catalogue shot will do or the thing has to be photographed in place.
SHOT = {
    "MATERIAL": "product shot, plain background",
    "TOOL": "the tool itself, whole",
    "SAFETY": "the item as worn or used",
}


def photographed() -> set[str]:
    if not IMAGES.is_dir():
        return set()
    return {
        path.stem for path in IMAGES.iterdir() if path.suffix.lower() in EXTENSIONS
    }


def missing() -> list[dict[str, str]]:
    manifest = json.loads((CATALOG / "manifest.json").read_text(encoding="utf-8"))
    have = photographed()
    rows: list[dict[str, str]] = []
    for trade in manifest["trades"]:
        trade_name = trade.get("names", {}).get("en") or trade["id"]
        data = json.loads((CATALOG / trade["itemsFile"]).read_text(encoding="utf-8"))
        for item in data["items"]:
            if item["id"] in have:
                continue
            names = item.get("names", {})
            spoken = item.get("colloquial", {})
            rows.append(
                {
                    "trade": trade_name,
                    "kind": item.get("kind", ""),
                    "what the photo shows": SHOT.get(item.get("kind", ""), ""),
                    "english": names.get("en", ""),
                    "hebrew": names.get("he", ""),
                    "arabic": names.get("ar", ""),
                    "also called on site": " / ".join(
                        value for value in spoken.values() if value
                    ),
                    "what it is": item.get("spec", {}).get("en", ""),
                    "save the file as": item["id"] + ".webp",
                }
            )
    return rows


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--summary", action="store_true", help="counts only")
    args = parser.parse_args()

    rows = missing()
    counts = Counter(row["trade"] for row in rows)

    if not rows:
        print("Every catalogue item has a photograph.")
        return 0

    if not args.summary:
        OUT.parent.mkdir(parents=True, exist_ok=True)
        # utf-8-sig: Excel reads a plain UTF-8 CSV as Latin-1 and turns every
        # Hebrew and Arabic name into mojibake. The byte-order mark is what
        # tells it otherwise, and this file is for opening in a spreadsheet.
        with OUT.open("w", encoding="utf-8-sig", newline="") as handle:
            writer = csv.DictWriter(handle, fieldnames=list(rows[0].keys()))
            writer.writeheader()
            writer.writerows(rows)
        print(f"{len(rows)} items still need a photograph. Written to {OUT.relative_to(ROOT)}")
    else:
        print(f"{len(rows)} items still need a photograph.")

    for trade, count in counts.most_common():
        print(f"  {count:4}  {trade}")
    print(
        "\nSave each file into shared/assets/catalog/images/ under the name in the\n"
        "last column. Nothing else has to change: the app finds them by that name."
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())
