#!/usr/bin/env python3
"""Turn a folder of photographs into catalogue images the apps can ship.

Resizes, converts to WebP and copies into shared/assets/catalog/images under
the name the apps look for. Source files are never modified.

    python3 tools/prepare-images.py ~/Downloads/product-photos
    python3 tools/prepare-images.py ~/photos --dry-run

A source file is matched to a catalogue item by its name, ignoring case,
extension, and any separator: "EL.RCD.40A.30MA.jpg", "el rcd 40a 30ma.png" and
"el_rcd_40a_30ma.jpeg" all land on el.rcd.40a.30ma. Anything it cannot match
is listed rather than guessed at, because a picture filed against the wrong
item is worse than one that is missing.

Needs Pillow:  pip install Pillow
"""

from __future__ import annotations

import argparse
import json
import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
CATALOG = ROOT / "shared" / "assets" / "catalog"
IMAGES = CATALOG / "images"

EDGE = 512
QUALITY = 80
SOURCE_TYPES = (".jpg", ".jpeg", ".png", ".webp", ".bmp", ".tif", ".tiff")


def normalise(text: str) -> str:
    """Reduce a name to letters and digits so separators stop mattering."""
    return re.sub(r"[^a-z0-9]", "", text.lower())


def catalogue_ids() -> dict[str, str]:
    manifest = json.loads((CATALOG / "manifest.json").read_text(encoding="utf-8"))
    ids: dict[str, str] = {}
    for trade in manifest["trades"]:
        data = json.loads((CATALOG / trade["itemsFile"]).read_text(encoding="utf-8"))
        for item in data["items"]:
            ids[normalise(item["id"])] = item["id"]
    return ids


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("source", type=pathlib.Path, help="folder of photographs")
    parser.add_argument("--edge", type=int, default=EDGE, help=f"longest edge in pixels (default {EDGE})")
    parser.add_argument("--quality", type=int, default=QUALITY, help=f"WebP quality (default {QUALITY})")
    parser.add_argument("--dry-run", action="store_true", help="report what would happen, write nothing")
    args = parser.parse_args()

    try:
        from PIL import Image, ImageOps
    except ImportError:
        print("Pillow is needed for this:  pip install Pillow", file=sys.stderr)
        return 1

    if not args.source.is_dir():
        print(f"Not a folder: {args.source}", file=sys.stderr)
        return 1

    ids = catalogue_ids()
    IMAGES.mkdir(parents=True, exist_ok=True)

    written, unmatched, total_bytes = 0, [], 0

    for path in sorted(args.source.iterdir()):
        if path.suffix.lower() not in SOURCE_TYPES:
            continue
        item_id = ids.get(normalise(path.stem))
        if item_id is None:
            unmatched.append(path.name)
            continue

        target = IMAGES / f"{item_id}.webp"
        if args.dry_run:
            print(f"  {path.name}  ->  {target.name}")
            written += 1
            continue

        with Image.open(path) as image:
            # EXIF orientation is why a photo taken in portrait shows up on its
            # side; transpose bakes the rotation in before anything else.
            image = ImageOps.exif_transpose(image)
            image = image.convert("RGB")
            image.thumbnail((args.edge, args.edge), Image.LANCZOS)
            image.save(target, "WEBP", quality=args.quality, method=6)

        total_bytes += target.stat().st_size
        written += 1

    verb = "would convert" if args.dry_run else "converted"
    print(f"{verb} {written} image(s)")
    if written and not args.dry_run:
        print(f"average size: {total_bytes // max(written, 1) // 1024} KB")

    if unmatched:
        print(f"\n{len(unmatched)} file(s) matched no catalogue item:")
        for name in unmatched:
            print(f"  {name}")
        print("\nRun tools/image-coverage.py --ids to see the ids they should be named after.")

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
