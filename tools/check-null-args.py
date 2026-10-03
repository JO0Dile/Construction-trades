#!/usr/bin/env python3
"""Find named arguments that are only ever passed null.

`find-unused.py` catches a function nothing calls. This catches the other half
of the same fault, which turned out to be far more common: a function that *is*
called, by code that always hands it nothing.

All of these were live in this repository at once, and every one compiled,
linted and passed the tests:

    signatureStrokes = null   the permit to work, the safety checklist and the
                              toolbox talk register -- the three documents a
                              site is actually asked to produce -- carried a
                              typed name and no signature, while a working
                              SignaturePad sat two screens away
    projectId = null          on the only check-in path, against a timesheet
                              that reads WHERE projectId = :projectId, so every
                              job's labour was arithmetic over an empty list
    latitude/longitude = null a location prompt that asked the worker every
                              shift and discarded the answer

A parameter given a column, a name and a doc comment, and never once given a
value, is indistinguishable at compile time from one that works. The only
signal is that nobody ever passes it anything.

What this does not catch, and the limit is worth knowing before trusting it:
the name has to be dead *everywhere*. `signatureStrokes` was, so this finds
it. `projectId = null` on the check-in was the more expensive bug of the two
and this would have walked straight past it, because `projectId` is passed a
real value in dozens of other calls and hides in the crowd. Catching that
needs matching each argument to the function it is actually passed to, which
needs a Kotlin parser rather than a page of regular expressions. So: a cheap
net with a known hole, not a proof.

    python3 tools/check-null-args.py            # report, exit 0
    python3 tools/check-null-args.py --check    # exit 1 if the count has grown

Not every finding is a bug, and the common false positive is honest: a record
being *created* has fields it cannot have yet -- `approvedAt = null` on a lift
plan nobody has approved is correct, and so is `completedAt = null`. Those are
initial states, not missing wiring. The question to ask of each finding is
whether a person could have supplied it and was never asked.
"""

from __future__ import annotations

import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
SOURCE = ROOT / "android" / "app" / "src" / "main" / "java" / "il" / "co" / "tradesmanager"

# The count accepted at the last review. Raise it deliberately, with a reason.
# One argument per line is the house style and also what this can read: a
# `copy(a = x, b = y)` folded onto one line hides both from it. That cost a
# false positive the first time it ran, which is a fair price for not needing
# a parser.
BASELINE = 5

# `name = null,` or `name = null)` as a named argument.
NULL_ARG = re.compile(r"^\s*(\w+)\s*=\s*null\s*[,)]?\s*$")
# `name = <anything else>` as a named argument, on its own line.
VALUE_ARG = re.compile(r"^\s*(\w+)\s*=\s*(?!null\s*[,)]?\s*$)\S.*$")
# A parameter in a declaration: `name: Type = default`. Its own default of null
# is not a call site and must not count as one either way.
DECLARATION = re.compile(r"^\s*(?:/\*\*.*)?(\w+)\s*:\s*[\w<>?., ]+(\s*=.*)?,?\s*$")

# Ending in At/On is a moment that has not happened yet; ending in a plural or
# starting with `is` is rarely a field somebody fills in. These are the shapes
# that produced only honest findings when the tool was written, and skipping
# them keeps the list short enough to read.
NOT_INTERESTING = re.compile(r"(At|On)$|^(is|has|can)[A-Z]")


# `var thing by remember { ... }` or `var thing = ...`: a local somebody
# assigns, not an argument somebody passes. `assigning = null` closing a dialog
# is the same three characters as a parameter nobody fills in, and only the
# declaration tells them apart.
LOCAL_VAR = re.compile(r"^\s*var\s+(\w+)\b", re.M)


def named_arguments(text: str) -> tuple[set[str], set[str]]:
    """Parameter names passed null, and names passed something else."""
    nulls: set[str] = set()
    values: set[str] = set()
    locals_here = set(LOCAL_VAR.findall(text))
    for line in text.splitlines():
        stripped = line.strip()
        if stripped.startswith(("*", "//", "/*")):
            continue
        null_match = NULL_ARG.match(line)
        if null_match:
            if null_match.group(1) not in locals_here:
                nulls.add(null_match.group(1))
            continue
        # A declaration is not a call. `foo: String? = null` would otherwise
        # read as a call site passing null, and every optional parameter in the
        # codebase would be a finding.
        if DECLARATION.match(line):
            continue
        value_match = VALUE_ARG.match(line)
        if value_match:
            values.add(value_match.group(1))
    return nulls, values


def main(argv: list[str]) -> int:
    always_null: set[str] = set()
    ever_valued: set[str] = set()
    where: dict[str, list[str]] = {}

    for path in sorted(SOURCE.rglob("*.kt")):
        text = path.read_text(encoding="utf-8")
        nulls, values = named_arguments(text)
        ever_valued |= values
        for name in nulls:
            always_null.add(name)
            where.setdefault(name, []).append(str(path.relative_to(ROOT)))

    findings = sorted(
        name for name in always_null - ever_valued if not NOT_INTERESTING.search(name)
    )

    print(f"{len(findings)} named arguments are passed null everywhere and a value nowhere:\n")
    for name in findings:
        files = where[name]
        shown = files[0] if len(files) == 1 else f"{files[0]} and {len(files) - 1} more"
        print(f"  {name:<28} {shown}")

    if "--check" in argv:
        if len(findings) > BASELINE:
            print(
                f"\nThat is {len(findings) - BASELINE} more than the {BASELINE} accepted at "
                "the last review.\nEither pass it something, delete it, or raise BASELINE in "
                "this file with a reason."
            )
            return 1
        if len(findings) < BASELINE:
            print(f"\nDown to {len(findings)} from {BASELINE}. Lower BASELINE to hold the gain.")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
