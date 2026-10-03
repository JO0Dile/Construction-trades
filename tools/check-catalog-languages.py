#!/usr/bin/env python3
"""Every piece of shipped catalogue text exists in all three languages.

gen-strings.py already proves this for the app's own strings. It cannot see
the catalogue, which is the larger half of what a user reads: the item names,
the specifications, the checklists, the templates, the stages and the scopes
of work. That content is JSON, it is written by hand, and a block added in
English with the Hebrew and Arabic "to follow" looks exactly like a finished
one in a diff.

It is also invisible in the app. A missing translation does not crash and does
not blank the screen — resolve() falls back to whatever language is there, so
an Arabic-speaking labourer is quietly shown an English sentence in the middle
of an otherwise Arabic list, and nobody who speaks English notices.

So: walk every JSON file under shared/assets, find every localized block — any
object with an "en" string in it — and require a real Hebrew and Arabic value
written in the right alphabet. A value copied across from the English fails,
because a Hebrew field holding Latin letters is an untranslated field with a
key in it.

Blocks with no letters at all in the English, like a bare reference, are left
alone: there is nothing in them to translate.
"""

from __future__ import annotations

import collections
import json
import pathlib
import sys
import unicodedata

ROOT = pathlib.Path(__file__).resolve().parent.parent
ASSETS = ROOT / "shared" / "assets"

# Which alphabet each language has to be written in.
ALPHABETS = {"he": "HEBREW", "ar": "ARABIC"}


def scripts(text: str) -> set[str]:
    found = set()
    for ch in text:
        if not ch.isalpha():
            continue
        name = unicodedata.name(ch, "")
        for language, alphabet in ALPHABETS.items():
            if name.startswith(alphabet):
                found.add(language)
        if name.startswith("LATIN"):
            found.add("latin")
    return found


def walk(node, path: str, where: str, missing, english, counter):
    if isinstance(node, dict) and isinstance(node.get("en"), str):
        counter[0] += 1
        english_text = node["en"]
        for language in ALPHABETS:
            value = node.get(language)
            if not isinstance(value, str) or not value.strip():
                missing[(where, language)].append(path)
            elif any(c.isalpha() for c in english_text) and language not in scripts(value):
                english[(where, language)].append((path, value))
        return
    if isinstance(node, dict):
        for key, value in node.items():
            walk(value, f"{path}.{key}" if path else key, where, missing, english, counter)
    elif isinstance(node, list):
        for index, value in enumerate(node):
            walk(value, f"{path}[{index}]", where, missing, english, counter)


def main() -> int:
    missing = collections.defaultdict(list)
    english = collections.defaultdict(list)
    counter = [0]

    for path in sorted(ASSETS.rglob("*.json")):
        where = str(path.relative_to(ASSETS))
        walk(json.loads(path.read_text(encoding="utf-8")), "", where, missing, english, counter)

    problems = sum(len(rows) for rows in missing.values()) + sum(
        len(rows) for rows in english.values()
    )
    if not problems:
        print(f"All {counter[0]} catalogue text blocks are written in all three languages.")
        return 0

    print(f"{problems} catalogue text blocks are not translated:\n")
    for label, table in (("No value at all", missing), ("Still in English", english)):
        for (where, language) in sorted(table):
            rows = table[(where, language)]
            print(f"  {label} — {where} [{language}]  ({len(rows)})")
            for row in rows[:5]:
                print(f"      {row}")
            if len(rows) > 5:
                print(f"      ... and {len(rows) - 5} more")
    print("\nThe worksheets in docs/translation/ are where the real wording goes.")
    return 1


if __name__ == "__main__":
    sys.exit(main())
