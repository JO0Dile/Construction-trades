#!/usr/bin/env python3
"""Which catalogue items have a picture, and which do not.

    python3 tools/image-coverage.py            # summary + the ids still missing
    python3 tools/image-coverage.py --ids      # just the ids, one per line
    python3 tools/image-coverage.py --check    # exit 1 if any file matches no id
"""

from __future__ import annotations

import argparse
import json
import pathlib
import signal
import sys

# This prints a long list, so it is routinely piped into head or less. Restore
# the default SIGPIPE handling that Python replaces, or closing the pipe raises
# BrokenPipeError instead of just ending the program.
if hasattr(signal, "SIGPIPE"):
    signal.signal(signal.SIGPIPE, signal.SIG_DFL)

ROOT = pathlib.Path(__file__).resolve().parent.parent
CATALOG = ROOT / "shared" / "assets" / "catalog"
IMAGES = CATALOG / "images"
EXTENSIONS = (".webp", ".jpg", ".jpeg", ".png")


def catalogue_items() -> dict[str, str]:
    """Item id -> the trade it belongs to."""
    manifest = json.loads((CATALOG / "manifest.json").read_text(encoding="utf-8"))
    items: dict[str, str] = {}
    for trade in manifest["trades"]:
        data = json.loads((CATALOG / trade["itemsFile"]).read_text(encoding="utf-8"))
        for item in data["items"]:
            items[item["id"]] = trade["id"]
    return items


def present() -> dict[str, pathlib.Path]:
    """Item id -> the image file found for it."""
    found: dict[str, pathlib.Path] = {}
    if not IMAGES.is_dir():
        return found
    for path in sorted(IMAGES.iterdir()):
        if path.suffix.lower() in EXTENSIONS:
            found.setdefault(path.stem, path)
    return found


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--ids", action="store_true", help="print missing ids only")
    parser.add_argument("--check", action="store_true", help="fail on files matching no item")
    args = parser.parse_args()

    items = catalogue_items()
    images = present()
    missing = sorted(set(items) - set(images))
    orphans = sorted(set(images) - set(items))

    if args.ids:
        print("\n".join(missing))
        return 0

    covered = len(items) - len(missing)
    print(f"{covered} of {len(items)} items have a picture.")

    if orphans:
        print("\nFiles matching no catalogue item — check the spelling:")
        for stem in orphans:
            print(f"  {images[stem].name}")

    if missing:
        by_trade: dict[str, list[str]] = {}
        for item_id in missing:
            by_trade.setdefault(items[item_id], []).append(item_id)
        print("\nStill missing:")
        for trade in sorted(by_trade):
            print(f"\n  {trade} ({len(by_trade[trade])})")
            for item_id in by_trade[trade]:
                print(f"    {item_id}")

    if args.check and orphans:
        print("\nA file that matches no item ships bytes nobody sees.", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
