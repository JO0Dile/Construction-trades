#!/usr/bin/env python3
"""The list of catalogue items still waiting for a photograph.

The column that matters most is "photograph exactly this": one
unambiguous sentence naming the object and the form it actually takes on a
site, written out in docs/translation/photo-briefs.json. It exists because
the names on their own are not enough to fetch or generate a picture from —
"Pit buffer", "Darby", "Base plate" — and a generator handed a name it
cannot place does not fail, it invents. That is how a batch of these came
back with faces and bones in it instead of cables.

`image-coverage.py` answers "how many are missing" and prints their ids.
That is the right answer for a build check and the wrong one for a person
about to go and take 527 photographs: an id says nothing about what the thing
is, and a list of ids in three scripts is not something anybody can work from
on a phone in a builders' merchant.

So this writes the same set twice, for the two people who need it.

`items-needing-photos.csv` is the spreadsheet: ten columns, the names in all
three languages, what the item is for, the word a site would actually shout
for it where the catalogue records one, and the exact filename to save the
picture as. It opens in Excel and feeds a script.

`docs/PHOTOS.md` is the same list for somebody holding a phone. That was the
whole point of writing this and the CSV did not achieve it: ten columns render
as a table wider than a phone, so the list nobody could work from as ids
became a list nobody could work from as a spreadsheet either. The Markdown is
a checklist — one item per entry, Hebrew first, the brief under it, the
filename under that — and GitHub renders it, with working tick boxes, on the
screen the person sourcing the photographs is actually holding.

Drop the files into shared/assets/catalog/images/ with those names and they
are picked up; no code changes, no ids to type.

    python3 tools/photo-worklist.py            # write both
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
BRIEFS = ROOT / "docs" / "translation" / "photo-briefs.json"
CATALOG = ROOT / "shared" / "assets" / "catalog"
IMAGES = CATALOG / "images"
OUT = ROOT / "docs" / "translation" / "items-needing-photos.csv"
CHECKLIST = ROOT / "docs" / "PHOTOS.md"
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
    briefs = json.loads(BRIEFS.read_text(encoding="utf-8"))["briefs"]
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
                    "photograph exactly this": briefs.get(item["id"], ""),
                    "save the file as": item["id"] + ".webp",
                }
            )
    return rows


def checklist(rows: list[dict[str, str]], counts: Counter) -> str:
    """The same list, for somebody holding a phone.

    Hebrew first because that is the language most of the people sourcing
    these will be reading, with the English underneath for a supplier's
    catalogue and the Arabic for the rest. The brief is the line that matters
    and it gets its own paragraph rather than a cell: it is the sentence that
    stops a picture of a face coming back where a cable should be.
    """
    out = [
        "# Photographs still needed",
        "",
        f"**{len(rows)} of the catalogue's items have no picture.** Everything else",
        "has one, so the app works — an item with no photograph falls back to its",
        "category icon rather than to a blank.",
        "",
        "Tick one off by taking the picture described under it and saving the file",
        "with the name given. Drop the files into `shared/assets/catalog/images/`",
        "and they are picked up: no code change, no ids to type, and nothing to",
        "rename afterwards.",
        "",
        "The line in italics is the one that matters. It is there because a name",
        "on its own is not enough to fetch or generate a picture from — \"Pit",
        "buffer\", \"Darby\", \"Base plate\" — and something handed a name it cannot",
        "place does not give up, it invents. That is how a batch of these came",
        "back with faces and bones in it instead of cables.",
        "",
        "Generated by `tools/photo-worklist.py`. The same list as a spreadsheet is",
        "in [`translation/items-needing-photos.csv`](translation/items-needing-photos.csv).",
        "",
    ]
    for trade, count in counts.most_common():
        out.append(f"## {trade} — {count}")
        out.append("")
        for row in [r for r in rows if r["trade"] == trade]:
            names = [row["hebrew"], row["english"], row["arabic"]]
            title = " · ".join(name for name in names if name)
            out.append(f"- [ ] **{title}**")
            spoken = row["also called on site"]
            if spoken:
                out.append(f"  On site: {spoken}")
            brief = row["photograph exactly this"] or row["what it is"]
            if brief:
                out.append(f"  *{brief}*")
            out.append(f"  `{row['save the file as']}`")
            out.append("")
    return "\n".join(out).rstrip() + "\n"


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--summary", action="store_true", help="counts only")
    parser.add_argument(
        "--check",
        action="store_true",
        help="verify the written lists match the catalogue, for CI",
    )
    args = parser.parse_args()

    rows = missing()
    counts = Counter(row["trade"] for row in rows)

    if args.check:
        # The lists are generated, and a photograph added without regenerating
        # them leaves a checklist telling somebody to go and take a picture
        # that is already in the repository.
        wanted = checklist(rows, counts) if rows else ""
        held = CHECKLIST.read_text(encoding="utf-8") if CHECKLIST.exists() else ""
        if held != wanted:
            print(f"{CHECKLIST.relative_to(ROOT)} is out of date.")
            print("Run tools/photo-worklist.py to rewrite it.")
            return 1
        print(f"The photograph checklist matches the catalogue: {len(rows)} still needed.")
        return 0

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
        CHECKLIST.write_text(checklist(rows, counts), encoding="utf-8")
        print(f"{len(rows)} items still need a photograph.")
        print(f"  spreadsheet: {OUT.relative_to(ROOT)}")
        print(f"  checklist:   {CHECKLIST.relative_to(ROOT)}")
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
