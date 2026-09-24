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

Plurals get a row each. A count is spelled differently for one, two and
many in Hebrew and in six ways in Arabic, so the forms sit in one cell as
`one: ... | other: ...`, and a translator answers in the same shape.

A row for a string the app has since dropped goes, unless somebody has
already answered it: those stay, because deleting their work to tidy a file
is not a trade worth making.

Run it after adding strings:

    python3 tools/gen-strings.py && python3 tools/gen-translation-sheet.py

CI runs it with --check, which changes nothing and fails if the sheet is
behind the app. That is how 478 strings went missing from it once.
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


def forms(text: dict) -> str:
    """One cell for all of a plural's forms, in CLDR order."""
    order = ("zero", "one", "two", "few", "many", "other")
    return " | ".join(f"{name}: {text[name]}" for name in order if name in text)


def answered(row: dict[str, str]) -> bool:
    return bool(
        row.get("hebrew_yours", "").strip() or row.get("arabic_yours", "").strip()
    )


def build() -> tuple[list[dict[str, str]], list[str], list[str]]:
    source = json.loads(SOURCE.read_text(encoding="utf-8"))
    texts: dict[str, dict[str, str]] = {}
    for key, text in source["strings"].items():
        texts[key] = {
            "c": text.get("c", ""),
            "en": text.get("en", ""),
            "he": text.get("he", ""),
            "ar": text.get("ar", ""),
        }
    for key, plural in source.get("plurals", {}).items():
        note = plural.get("c", "")
        texts[key] = {
            "c": f"{note} (a count: one form per number)".strip(),
            "en": forms(plural.get("en", {})),
            "he": forms(plural.get("he", {})),
            "ar": forms(plural.get("ar", {})),
        }

    existing: dict[str, dict[str, str]] = {}
    order: list[str] = []
    if SHEET.exists():
        with io.open(SHEET, encoding="utf-8-sig", newline="") as handle:
            for row in csv.DictReader(handle):
                existing[row["key"]] = row
                order.append(row["key"])

    added = [key for key in texts if key not in existing]
    dropped = [key for key in order if key not in texts]

    rows = []
    for key in order + added:
        was = existing.get(key, {})
        if key not in texts:
            if answered(was):
                rows.append(was)
            continue
        text = texts[key]
        rows.append(
            {
                "key": key,
                # The developer's note fills an empty cell; it never replaces
                # one somebody wrote.
                "where_it_appears": was.get("where_it_appears", "") or text["c"],
                "english": text["en"],
                "hebrew_draft": text["he"],
                "arabic_draft": text["ar"],
                "hebrew_yours": was.get("hebrew_yours", ""),
                "arabic_yours": was.get("arabic_yours", ""),
            }
        )
    for column in THEIRS:
        assert all(column in row for row in rows), column
    return rows, added, dropped


def render(rows: list[dict[str, str]]) -> str:
    buffer = io.StringIO(newline="")
    writer = csv.DictWriter(buffer, fieldnames=COLUMNS, lineterminator="\r\n")
    writer.writeheader()
    writer.writerows(rows)
    return buffer.getvalue()


def main() -> int:
    check = "--check" in sys.argv[1:]
    rows, added, dropped = build()
    text = render(rows)

    if check:
        current = (
            SHEET.read_text(encoding="utf-8-sig") if SHEET.exists() else ""
        )
        if current.replace("\r\n", "\n") == text.replace("\r\n", "\n"):
            print(f"{SHEET.relative_to(ROOT)} matches the app: {len(rows)} rows.")
            return 0
        print(f"{SHEET.relative_to(ROOT)} is behind the app.", file=sys.stderr)
        if added:
            print(f"  never asked for ({len(added)}): {', '.join(added[:20])}"
                  + (" ..." if len(added) > 20 else ""), file=sys.stderr)
        if dropped:
            print(f"  no longer in the app ({len(dropped)})", file=sys.stderr)
        print("  run: python3 tools/gen-translation-sheet.py", file=sys.stderr)
        return 1

    # utf-8-sig so that Excel on a Hebrew or Arabic Windows opens it as UTF-8
    # rather than mangling every non-Latin character in the file.
    with io.open(SHEET, "w", encoding="utf-8-sig", newline="") as handle:
        handle.write(text)

    done = sum(1 for row in rows if answered(row))
    print(f"{SHEET.relative_to(ROOT)}: {len(rows)} rows, {done} answered.")
    if added:
        print(f"  added {len(added)}: {', '.join(added)}")
    if dropped:
        print(f"  no longer in the app ({len(dropped)}): {', '.join(dropped)}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
