#!/usr/bin/env python3
"""Top up docs/translation/interface.csv from shared/i18n/strings.json.

The worksheet is where the real Hebrew and Arabic wording goes — the drafts in
the app are drafts. Every string added to the app after the worksheet was
written was a string nobody was ever asked to translate, and it would have
shipped in draft wording without anybody noticing. Eleven had already gone
that way when this was written.

Anything already filled in is kept exactly as it is. This only appends rows
for keys the worksheet has never seen, and refreshes the English and the draft
columns for rows nobody has answered yet. A column somebody has typed into is
never touched, whatever the source now says.

Run it after adding strings:

    python3 tools/gen-strings.py && python3 tools/gen-translation-sheet.py
"""

from __future__ import annotations

import csv
import io
import json
import pathlib
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
SOURCE = ROOT / "shared" / "i18n" / "strings.json"
SHEET = ROOT / "docs" / "translation" / "interface.csv"

COLUMNS = [
    "key",
    "where_it_appears",
    "english",
    "hebrew_draft",
    "arabic_draft",
    "hebrew_yours",
    "arabic_yours",
]

# The columns a person fills in. Never overwritten.
THEIRS = ("where_it_appears", "hebrew_yours", "arabic_yours")


def main() -> int:
    strings = json.loads(SOURCE.read_text(encoding="utf-8"))["strings"]

    existing: dict[str, dict[str, str]] = {}
    order: list[str] = []
    if SHEET.exists():
        with io.open(SHEET, encoding="utf-8-sig", newline="") as handle:
            for row in csv.DictReader(handle):
                existing[row["key"]] = row
                order.append(row["key"])

    added = [key for key in strings if key not in existing]
    dropped = [key for key in order if key not in strings]

    rows = []
    for key in order + added:
        if key not in strings:
            # A string the app no longer has. Kept, because somebody may have
            # already translated it and deleting their work to tidy a file is
            # not a trade worth making. Reported instead.
            rows.append(existing[key])
            continue
        text = strings[key]
        was = existing.get(key, {})
        rows.append(
            {
                "key": key,
                "where_it_appears": was.get("where_it_appears", ""),
                "english": text.get("en", ""),
                "hebrew_draft": text.get("he", ""),
                "arabic_draft": text.get("ar", ""),
                "hebrew_yours": was.get("hebrew_yours", ""),
                "arabic_yours": was.get("arabic_yours", ""),
            }
        )

    # utf-8-sig so that Excel on a Hebrew or Arabic Windows opens it as UTF-8
    # rather than mangling every non-Latin character in the file.
    with io.open(SHEET, "w", encoding="utf-8-sig", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=COLUMNS)
        writer.writeheader()
        writer.writerows(rows)

    answered = sum(
        1 for row in rows if row.get("hebrew_yours", "").strip()
        or row.get("arabic_yours", "").strip()
    )
    print(f"{SHEET.relative_to(ROOT)}: {len(rows)} rows, {answered} answered.")
    if added:
        print(f"  added {len(added)}: {', '.join(added)}")
    if dropped:
        print(f"  no longer in the app ({len(dropped)}): {', '.join(dropped)}")
    for column in THEIRS:
        assert all(column in row for row in rows), column
    return 0


if __name__ == "__main__":
    sys.exit(main())
