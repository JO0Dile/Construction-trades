#!/usr/bin/env python3
"""Find public functions in core/, data/repository/ and data/backup/ that nothing calls.

Four times in this repository a name and a doc comment described something no
code performed:

    "retention is enforced by a scheduled purge"  — nothing scheduled it
    exportSince                                   — no caller
    a payment-application lines query             — no caller
    reportIncident / observeIncidents             — no way to report an accident

The last one is why this file exists. The table, the DAO and the repository
for incidents were all built and nothing could reach any of them, so a
construction safety app had no way to record an accident. Each of the four was
found by tripping over it. This looks for them on purpose.

    python3 tools/find-unused.py            # report, exit 0
    python3 tools/find-unused.py --check    # exit 1 if the count has grown

A finding is not automatically a bug. A repository method waiting for a screen
that has not been written is fine and says so; a method whose comment claims a
behaviour the app does not have is not. The judgement is yours — the point is
to make the list visible rather than let it grow unwatched.

The baseline is the count accepted at the last review. Raise it deliberately,
with a reason, or bring the code back down to it.
"""

from __future__ import annotations

import io
import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
SOURCE = ROOT / "android" / "app" / "src" / "main" / "java" / "il" / "co" / "tradesmanager"
TESTS = ROOT / "android" / "app" / "src" / "test"

# Layers where an uncalled public function means something. UI files are
# excluded: a composable is called by the framework and by preview tooling,
# and a sweep that flagged every screen would be ignored within a week.
# data/backup is in here for the same reason the other two are: it is the
# layer that gets written ahead of the screen that will use it, and a
# backup nothing calls is the worst version of this whole failure -- it
# looks like the app has a backup and does not.
LAYERS = ("/core/", "/data/repository/", "/data/backup/")

# Accepted at the last review. See the module docstring before changing it.
BASELINE = 13

DECLARATION = re.compile(
    r'^\s{0,8}(?:(?:suspend|inline|open|internal)\s+)*fun\s+(?:<[^>]+>\s+)?(?:[\w.]+\.)?(\w+)\s*\(',
    re.M,
)


def main(argv: list[str]) -> int:
    sources = {p: io.open(p, encoding="utf-8").read() for p in SOURCE.rglob("*.kt")}
    everywhere = dict(sources)
    everywhere.update({p: io.open(p, encoding="utf-8").read() for p in TESTS.rglob("*.kt")})

    declared = []
    for path, text in sources.items():
        if not any(layer in str(path) for layer in LAYERS):
            continue
        for match in DECLARATION.finditer(text):
            name = match.group(1)
            line_start = text[max(0, match.start() - 120):match.start()].split("\n")[-1]
            if "override" in line_start or "private" in line_start:
                continue
            declared.append((path, text[:match.start()].count("\n") + 1, name))

    unused = []
    for path, line, name in declared:
        calls = re.compile(r'\b' + re.escape(name) + r'\s*\(')
        uses = sum(
            len(calls.findall(body)) - (1 if other == path else 0)
            for other, body in everywhere.items()
        )
        if uses <= 0:
            unused.append((path, line, name))

    where = ", ".join(layer.strip("/") for layer in LAYERS)
    print(f"{len(declared)} public functions in {where}.")
    print(f"{len(unused)} with no call site anywhere, including tests:\n")
    for path, line, name in sorted(unused, key=lambda row: str(row[0])):
        print(f"  {path.relative_to(ROOT)}:{line}  {name}()")

    if "--check" in argv:
        if len(unused) > BASELINE:
            print(
                f"\nThat is {len(unused) - BASELINE} more than the {BASELINE} accepted at the "
                "last review.\nEither wire the new ones up, delete them, or raise BASELINE "
                "in this file with a reason."
            )
            return 1
        if len(unused) < BASELINE:
            print(f"\nDown to {len(unused)} from {BASELINE}. Lower BASELINE to hold the gain.")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
