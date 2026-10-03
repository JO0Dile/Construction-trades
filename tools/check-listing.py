#!/usr/bin/env python3
"""Check the store listing copy against the limits the stores enforce.

A listing that is one character over is not rejected politely at upload; it is
rejected on submission day, in a hurry, by somebody who did not write it. The
numbers are Google Play's, and they count characters rather than bytes — which
matters here, because two of the three languages are not Latin and every
by-eye estimate of their length is wrong.

    python3 tools/check-listing.py          # report
    python3 tools/check-listing.py --check  # same; the exit code is the answer

Screenshots and the feature graphic are not checked: one is a set of PNG
dimensions this cannot know without the files, and the other is checked by
tools/check-store-art.py.
"""

from __future__ import annotations

import json
import pathlib
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
LISTING = ROOT / "docs" / "store" / "listing.json"

# Google Play, per language.
LIMITS = {"title": 30, "short": 80, "full": 4000}

# Play's own language codes, so what is here can be pasted straight in.
EXPECTED = {"en-US", "iw-IL", "ar"}


def main() -> int:
    data = json.loads(LISTING.read_text(encoding="utf-8"))
    languages = data["languages"]

    problems = []
    missing = EXPECTED - set(languages)
    if missing:
        problems.append(f"no listing for {', '.join(sorted(missing))}")

    for code, entry in sorted(languages.items()):
        for field, limit in LIMITS.items():
            text = entry.get(field, "")
            if not text.strip():
                problems.append(f"{code}: {field} is empty")
            elif len(text) > limit:
                problems.append(
                    f"{code}: {field} is {len(text)} characters, {len(text) - limit} over {limit}"
                )

    # The same string in two languages means one of them was never translated.
    for field in LIMITS:
        seen: dict[str, str] = {}
        for code, entry in sorted(languages.items()):
            text = entry.get(field, "")
            if text in seen:
                problems.append(f"{field} is identical in {seen[text]} and {code}")
            seen[text] = code

    if problems:
        print(f"{len(problems)} problem(s) with the store listing:")
        for problem in problems:
            print(f"  {problem}")
        return 1

    print(f"Listing copy is within Play's limits in {len(languages)} languages.")
    for code, entry in sorted(languages.items()):
        print(f"  {code:6} title {len(entry['title']):3}  short {len(entry['short']):3}  "
              f"full {len(entry['full']):5}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
