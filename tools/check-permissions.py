#!/usr/bin/env python3
"""The permissions the store forms are filled in from are the ones the app holds.

docs/STORE_COMPLIANCE.md carries a table of Android permissions, and it says
in as many words to answer Google Play's Data safety form from that table. For
a long time the table listed five: camera, location, microphone, notifications
and network. Three of those were never in the manifest. They were in the plan,
the plan changed, and the table did not.

Nothing catches that. The app builds, runs and asks for nothing it should not
— the damage happens off the machine, when somebody careful fills in a form
from a document that has quietly gone out of date and declares that the app
collects a worker's location. Play treats an inaccurate Data safety
declaration as a policy matter, not a typo, and the person who filled it in
did exactly what the documentation told them to.

So: the table and the manifest must name the same permissions. Nothing else
about the table is checked — why a permission is wanted, and what happens when
somebody refuses it, are for a person to write.
"""

from __future__ import annotations

import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
MANIFEST = ROOT / "android/app/src/main/AndroidManifest.xml"
DOC = ROOT / "docs/STORE_COMPLIANCE.md"

HEADING = "**Android — what the manifest declares, and all it declares:**"


def declared() -> set[str]:
    text = MANIFEST.read_text(encoding="utf-8")
    return set(re.findall(r'<uses-permission\s+android:name="([^"]+)"', text))


def documented() -> tuple[set[str], str | None]:
    text = DOC.read_text(encoding="utf-8")
    if HEADING not in text:
        return set(), f"{DOC.name}: the Android permission table's heading has moved or changed"
    after = text.split(HEADING, 1)[1]
    # The table runs until the first blank line that is not part of it.
    rows = []
    for line in after.splitlines():
        if line.startswith("|"):
            rows.append(line)
        elif rows:
            break
    found = set()
    for row in rows:
        found.update(re.findall(r"`(android\.permission\.[A-Z_]+)`", row))
    if not found:
        return set(), f"{DOC.name}: no permissions found in the Android table"
    return found, None


def main() -> int:
    manifest = declared()
    doc, fault = documented()
    problems = [fault] if fault else []

    for missing in sorted(manifest - doc):
        problems.append(
            f"{missing} is in AndroidManifest.xml and not in the table — "
            "a store form filled in from the table would under-declare it"
        )
    for extra in sorted(doc - manifest):
        problems.append(
            f"{extra} is in the table and not in AndroidManifest.xml — "
            "a store form filled in from the table would declare a permission the app does not hold"
        )

    if problems:
        print(f"{len(problems)} problems with the documented permissions:\n")
        for problem in problems:
            print(" ", problem)
        print(f"\nThe table is in {DOC.relative_to(ROOT)}, under:\n  {HEADING}")
        return 1

    print(f"The {len(manifest)} permissions in the manifest are the {len(doc)} the store forms name.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
