#!/usr/bin/env python3
"""Fail on invisible characters in source.

A right-to-left mark is not visible and does not show in a diff. Dropped into
a Kotlin string literal — which happens the moment somebody copies a Hebrew or
Arabic phrase out of a browser — it changes what the literal is while leaving
the line looking identical to the one it replaced. A reviewer cannot catch it
and neither can a person staring at the file.

It got in here the other way round, which is how this exists: a set of
`'\\u200E'` escapes were written as escapes and landed in the file as the
characters themselves. The code was correct and unreadable, and one of them
was a byte-order mark, which Android Lint refuses in the middle of a file and
is right to refuse. Lint catches only that one; the other nine are equally
invisible and equally silent, so this catches all of them.

SOURCE ONLY, and deliberately. The shipped catalogue carries direction marks
inside Hebrew item names, put there by whoever typed them, and they are data:
they are what the supplier calls the thing. `core.find.Search` folds them away
at match time, which is the right place to deal with them. Escaping them in a
JSON asset would be editing somebody's product name to suit a checker.

A byte-order mark at the very start of a file is also left alone: the
translation CSVs open with one so that Excel reads them as UTF-8, which is
what a BOM is for. It is a BOM in the *middle* of a file that means something
went wrong.

Run with --check for the same thing; the exit code is the answer either way.
"""

from __future__ import annotations

import pathlib
import subprocess
import sys
import unicodedata

ROOT = pathlib.Path(__file__).resolve().parent.parent

# Where an invisible character is always a mistake rather than content.
SOURCE_SUFFIXES = {".kt", ".kts", ".swift", ".xml", ".java"}

# Cf is Unicode's "format" category: the direction marks, the isolates, the
# joiners, the byte-order mark. Soft hyphen is Cf too and just as invisible.
# Zero-width space is a space by category and invisible by nature, so it is
# named rather than derived.
ALWAYS = {0x200B, 0x2028, 0x2029}


def offending(text: str) -> list[tuple[int, int, str]]:
    found = []
    for number, line in enumerate(text.split("\n"), 1):
        for column, ch in enumerate(line, 1):
            code = ord(ch)
            # A BOM as the very first character of the file is a declaration
            # of encoding, not a stray mark.
            if code == 0xFEFF and number == 1 and column == 1:
                continue
            if code in ALWAYS or unicodedata.category(ch) == "Cf":
                found.append((number, column, ch))
    return found


def main() -> int:
    # --others --exclude-standard as well as --cached, so a file that has been
    # written but not yet added is scanned too. Plain `git ls-files` lists only
    # tracked files, which meant a brand new source file was invisible to this
    # until the moment it was committed — and a new file is exactly where a
    # freshly typed escape turns into a raw character. That is not theoretical:
    # it is how two direction marks reached VerificationTest.kt after this
    # check had been run and had said the tree was clean.
    #
    # --exclude-standard keeps .gitignore honoured, so build output stays out.
    listed = subprocess.run(
        ["git", "ls-files", "--cached", "--others", "--exclude-standard"],
        capture_output=True,
        text=True,
        cwd=ROOT,
        check=True,
    ).stdout.split()

    problems = []
    scanned = 0
    for name in listed:
        path = ROOT / name
        if path.suffix not in SOURCE_SUFFIXES or not path.is_file():
            continue
        try:
            text = path.read_text(encoding="utf-8")
        except UnicodeDecodeError:
            continue
        scanned += 1
        for number, column, ch in offending(text):
            problems.append((name, number, column, ch))

    if not problems:
        print(f"No invisible characters in {scanned} source files.")
        return 0

    print(f"{len(problems)} invisible characters in source:")
    print()
    for name, number, column, ch in problems:
        try:
            label = unicodedata.name(ch)
        except ValueError:
            label = "unnamed"
        print(f"  {name}:{number}:{column}  U+{ord(ch):04X}  {label}")
    print()
    print("Write it as an escape — '\\u200E' — so the next person can see it.")
    return 1


if __name__ == "__main__":
    raise SystemExit(main())
