#!/usr/bin/env python3
"""Every kind of thing the audit trail records must be classified by lens.

The trail is what the "what changed" feed is built from, and the feed is
filtered so that somebody sees only changes to things they may read at all.
An entity type nobody has classified is withheld from everybody, which is the
safe direction and also a silent one: the feature simply never mentions that
kind of change, and nobody finds out for a release or two.

So this reads the entity types out of the source and checks each one against
`Changes.LENS_OF` in core. Adding a new register now fails here, in a second,
rather than shipping a feed with a hole in it.
"""
from __future__ import annotations

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "android/app/src/main/java/il/co/tradesmanager"
CHANGES = SOURCE / "core/access/Changes.kt"

# audit.record("photo", …) and record(entityType = "photo", …)
LITERAL = re.compile(r'\brecord\(\s*(?:entityType\s*=\s*)?"([a-z_]+)"')
NAMED = re.compile(r'entityType\s*=\s*"([a-z_]+)"')
# audit.record(ENTITY, …) and record(SNAG, …) -- the name, to be resolved below
SYMBOL = re.compile(r'\brecord\(\s*(?:entityType\s*=\s*)?([A-Z][A-Z_]*)\s*,')
# const val ENTITY = "membership"
CONSTANT = re.compile(r'\bconst val ([A-Z][A-Z_]*)\s*=\s*"([a-z_]+)"')
# The entries of the map in Changes.kt: "photo" to Lens.EVIDENCE
CLASSIFIED = re.compile(r'"([a-z_]+)"\s+to\s+')


def recorded_types() -> set[str]:
    """Every string this app passes as an audit entity type.

    A constant is counted only when it is actually passed to `record`, and
    resolved in the file that passes it. Sweeping up every lower-case
    `const val` in a repository instead was picking up PhotoRepository's
    MediaType.IMAGE and demanding a lens for "image", which is not a kind of
    thing that happens to a job.
    """
    found: set[str] = set()
    for path in SOURCE.rglob("*.kt"):
        text = path.read_text(encoding="utf-8")
        if "record(" not in text:
            continue
        found.update(LITERAL.findall(text))
        found.update(NAMED.findall(text))
        constants = dict(CONSTANT.findall(text))
        for name in SYMBOL.findall(text):
            value = constants.get(name)
            if value is not None:
                found.add(value)
    return found


def classified_types() -> set[str]:
    if not CHANGES.is_file():
        print(f"No {CHANGES.relative_to(ROOT)}", file=sys.stderr)
        return set()
    return set(CLASSIFIED.findall(CHANGES.read_text(encoding="utf-8")))


def main() -> int:
    recorded = recorded_types()
    classified = classified_types()

    unclassified = sorted(recorded - classified)
    stale = sorted(classified - recorded)

    problems = []
    if unclassified:
        problems.append(
            "These are written to the audit trail and no lens covers them, so\n"
            "  the change feed will never show them to anybody:\n"
            + "".join(f"    {name}\n" for name in unclassified)
        )
    if stale:
        problems.append(
            "These are classified and nothing records them any more:\n"
            + "".join(f"    {name}\n" for name in stale)
        )

    if problems:
        print("\n".join(problems))
        return 1
    print(f"All {len(recorded)} audited entity types are classified by lens.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
