#!/usr/bin/env python3
"""Finds entity columns that nothing ever writes a real value into.

The recurring fault in this codebase is not code that crashes. It is code
that compiles, lints, passes every test and does nothing: a column, an index,
a DAO query written against it and a comment explaining what it is for — and
no caller that ever puts anything in it.

  * `projectId` on a check-in was passed null by the only caller there was,
    so every job's timesheet read `WHERE projectId = :projectId` against a
    column of nulls and every labour figure was arithmetic over an empty list.
  * `projectId` on a violation was the same, so a firm running twelve sites
    could not tell one register from another.
  * `signatureStrokes` on three different sign-offs, so a permit, a checklist
    and a toolbox talk were all recorded as signed by nobody.

check-null-args.py catches one shape of this: a named argument passed null at
every call site. This catches a different one: a column whose name appears
nowhere in the app with a value beside it at all — no constructor, no `copy`,
no assignment. Three of those were sitting in the schema when it was written.

Be clear about what it does **not** catch, so nobody trusts it further than it
goes. It searches by column name across the whole app, so a name several
entities share is masked by any one of them using it: `projectId` on a
violation was null in every row ever written, and this would still have called
it filled, because a dozen other places assign a `projectId` a real value.
That case needs to follow the value, which is check-null-args.py's job and
which it cannot do either. Two partial guards, honestly labelled, beat one
that claims to be complete.

Also blind to:

  * a column written only through a raw `@Query("UPDATE ...")`;
  * a column assigned from a variable that is itself always null;
  * a column written only in dead code, which it counts as written.

Run with --check to fail when the count grows past the baseline.
"""

from __future__ import annotations

import argparse
import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
SOURCE = ROOT / "android/app/src/main/java/il/co/tradesmanager"
ENTITIES = SOURCE / "data/local/entity"

# Columns known to be empty on purpose, each with the reason it stays.
ALLOWED = {
    # A lift plan names the appliance in words. The register of plant is a
    # separate feature and the id is the hook for it, not a gap in this one.
    "applianceId",
    # A repeating day-plan block. The column is the hook for a feature nobody
    # has asked for yet; nothing reads it, so nothing is quietly wrong.
    "recurrenceRule",
    # Superseded rather than unfilled. A failed check does carry photographs
    # now, hung off the photo table under Owner.CHECKLIST_FAIL — the same call
    # the snags made, so one bad scaffold tie can be photographed from three
    # angles instead of one. This single-photo pointer is what that replaced.
    # Not dropped, because rebuilding a table on every phone in the country to
    # remove an unused nullable column is the worse half of that trade.
    "photoId",
}

ENTITY_CLASS = re.compile(r"@Entity\b.*?data class (\w+)\s*\(", re.DOTALL)
# `val name: Type?` or `@ColumnInfo(...) val name: Type? = default`
PARAM = re.compile(r"\bval\s+(\w+)\s*:\s*([\w<>, .]+\?)\s*(?:=|,|\)|$)")


def constructor_of(text: str, start: int) -> str:
    """The parameter list only — never the class body.

    Taking the body too is how `val minutesWorked: Long? get() = ...` gets
    reported as a column nothing writes. It is not a column at all.
    """
    depth, index = 1, start
    while depth and index < len(text):
        if text[index] == "(":
            depth += 1
        elif text[index] == ")":
            depth -= 1
        index += 1
    return text[start : index - 1]


def nullable_columns() -> dict[str, set[str]]:
    """Nullable column name -> the entities that declare it."""
    found: dict[str, set[str]] = {}
    for path in sorted(ENTITIES.glob("*.kt")):
        text = path.read_text(encoding="utf-8")
        for match in ENTITY_CLASS.finditer(text):
            params = constructor_of(text, match.end())
            for column in PARAM.finditer(params):
                found.setdefault(column.group(1), set()).add(
                    f"{path.name}:{match.group(1)}"
                )
    return found


def written_somewhere() -> str:
    """Every line of the app that is not an entity declaration."""
    return "\n".join(
        path.read_text(encoding="utf-8")
        for path in sorted(SOURCE.rglob("*.kt"))
        if ENTITIES not in path.parents
    )


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--check", action="store_true", help="fail past the baseline")
    parser.add_argument("--baseline", type=int, default=0)
    args = parser.parse_args()

    columns = nullable_columns()
    body = written_somewhere()

    dead: list[tuple[str, set[str]]] = []
    for name, entities in sorted(columns.items()):
        if name in ALLOWED:
            continue
        assigned = re.findall(rf"\b{re.escape(name)}\s*=\s*([^,\n)]+)", body)
        if not any(value.strip() != "null" for value in assigned):
            dead.append((name, entities))

    print(f"{len(columns)} nullable entity columns.")
    if not dead:
        print("Every one of them is written a real value somewhere.")
        return 0

    print(f"{len(dead)} that nothing anywhere assigns a non-null value:\n")
    for name, entities in dead:
        print(f"  {name:26} {', '.join(sorted(entities))}")
    print(
        "\nA column nothing fills is a feature that looks finished and is not.\n"
        "Fill it, delete it, or add it to ALLOWED with the reason it stays."
    )

    if args.check and len(dead) > args.baseline:
        print(f"\nBaseline is {args.baseline}. This is {len(dead)}.")
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
