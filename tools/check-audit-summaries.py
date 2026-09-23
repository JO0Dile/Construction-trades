#!/usr/bin/env python3
"""No English sentences written into the audit register.

An audit summary is stored once and read later, by somebody who may not share
a language with whoever wrote it. A labourer signs an induction on an Arabic
phone; the site manager reads the trail in Hebrew; the export goes to an
inspector in English. One stored sentence cannot serve all three, so a summary
is stored as a key and its arguments (core/audit/Summary.kt) and turned into
words when it is read.

This finds the ones that slipped back to prose. It takes the summary argument
of every audit.record call, removes the interpolations — those are data: a
reference, a name, a number, and they stay as they were typed — and fails if
English words are left behind in the fixed part.

Nothing here can be caught by the compiler or by a test: the wrong version
builds, runs, and writes a perfectly good English sentence into the register.
"""

from __future__ import annotations

import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
SOURCE = ROOT / "android" / "app" / "src" / "main" / "java" / "il" / "co" / "tradesmanager"

CALL = re.compile(r"\baudit\.record\s*\(")
INTERPOLATION = re.compile(r"\$\{[^{}]*(?:\{[^{}]*\}[^{}]*)*\}|\$\w+")
ENGLISH = re.compile(r"[A-Za-z]{2}")


def arguments(text: str, opening: int) -> str:
    depth, i, out = 0, opening, []
    while i < len(text):
        c = text[i]
        if c == "(":
            depth += 1
            if depth == 1:
                i += 1
                continue
        elif c == ")":
            depth -= 1
            if depth == 0:
                break
        out.append(c)
        i += 1
    return "".join(out)


def top_level(args: str) -> list[str]:
    parts, depth, current, quote = [], 0, [], None
    for c in args:
        if quote:
            current.append(c)
            if c == quote and (len(current) < 2 or current[-2] != "\\"):
                quote = None
            continue
        if c in "\"'":
            quote = c
            current.append(c)
            continue
        if c in "([{":
            depth += 1
        elif c in ")]}":
            depth -= 1
        if c == "," and depth == 0:
            parts.append("".join(current).strip())
            current = []
        else:
            current.append(c)
    if "".join(current).strip():
        parts.append("".join(current).strip())
    return parts


def summary_of(parts: list[str]) -> str | None:
    for part in parts:
        if part.startswith("summary ="):
            return part[len("summary ="):].strip()
    # The fifth positional argument, when nobody named it.
    if len(parts) >= 5 and "=" not in parts[4].split("(")[0]:
        return parts[4].strip()
    return None


def prose_in(expression: str) -> list[str]:
    """The English left in the fixed part of a summary expression."""
    left = []
    for literal in re.findall(r'"((?:[^"\\]|\\.)*)"', expression):
        fixed = INTERPOLATION.sub("", literal).strip()
        if ENGLISH.search(fixed):
            left.append(fixed)
    return left


def main() -> int:
    problems, checked = [], 0
    for path in sorted(SOURCE.rglob("*.kt")):
        text = path.read_text(encoding="utf-8")
        for match in CALL.finditer(text):
            expression = summary_of(top_level(arguments(text, match.end() - 1)))
            if expression is None:
                continue
            checked += 1
            prose = prose_in(expression)
            if prose:
                line = text[: match.start()].count("\n") + 1
                problems.append((path.relative_to(ROOT), line, prose))

    if problems:
        print(f"{len(problems)} audit summaries are English sentences:\n")
        for where, line, prose in problems:
            print(f"  {where}:{line}")
            for phrase in prose:
                print(f"      {phrase!r}")
        print(
            "\nStore a key and its arguments instead — Summary.of(Summaries.X, ...) —"
            "\nso the register can be read in the language of whoever opens it."
        )
        return 1
    print(f"None of the {checked} audit summaries hard-code an English sentence.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
