#!/usr/bin/env python3
"""Every Kotlin file closes what it opens: comments, braces, brackets.

A one-line comment that lost its closing */ in an edit turned the rest of
AppContainer into a comment. The compiler found it, but only after Gradle
had started a daemon and run KSP -- ten minutes into CI, and after a release
had been planned on the commit. This finds the same thing in a second, and
before anything else runs.

It is a count, not a parser. String and character literals and comments are
removed first, so a brace inside a string or a comment does not count, then
/* and */, { and }, ( and ), [ and ] must pair up. It does not know Kotlin's
grammar and does not try to; it only knows that an unbalanced file cannot
compile.
"""

from __future__ import annotations

import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
SOURCES = [ROOT / "android" / "app" / "src"]

RAW_STRING = re.compile(r'"""[\s\S]*?"""')
STRING = re.compile(r'"(?:\\.|[^"\\\n])*"')
CHAR = re.compile(r"'(?:\\.|[^'\\\n])'")
BLOCK_COMMENT = re.compile(r"/\*[\s\S]*?\*/")
LINE_COMMENT = re.compile(r"//[^\n]*")
PAIRS = (("{", "}"), ("(", ")"), ("[", "]"))


def problems_in(text: str) -> list[str]:
    stripped = CHAR.sub("''", STRING.sub('""', RAW_STRING.sub('""', text)))
    found = []
    opened, closed = stripped.count("/*"), stripped.count("*/")
    if opened != closed:
        found.append(f"{opened} comment(s) opened with /* and {closed} closed with */")
        return found
    code = LINE_COMMENT.sub("", BLOCK_COMMENT.sub("", stripped))
    for left, right in PAIRS:
        difference = code.count(left) - code.count(right)
        if difference:
            found.append(f"{abs(difference)} more '{left if difference > 0 else right}' than '{right if difference > 0 else left}'")
    return found


def main() -> int:
    files = sorted(f for root in SOURCES for f in root.rglob("*.kt"))
    bad = {}
    for file in files:
        found = problems_in(file.read_text(encoding="utf-8"))
        if found:
            bad[file] = found
    if bad:
        print("These Kotlin files do not close what they open:", file=sys.stderr)
        for file, found in bad.items():
            print(f"  {file.relative_to(ROOT)}: {'; '.join(found)}", file=sys.stderr)
        return 1
    print(f"All {len(files)} Kotlin files close every comment, brace and bracket they open.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
