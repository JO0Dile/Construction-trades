#!/usr/bin/env python3
"""Proves each migration creates exactly the tables Room expects.

Room re-validates the whole schema the first time the database opens after a
migration. If a hand-written CREATE TABLE differs from the one Room generates —
a column type, a nullability, the name of an index — it throws, and every
person who already has the app gets a crash on launch instead of an upgrade.
Nothing else in the build catches that: it compiles, it lints, it passes the
unit tests, and it dies on the device.

So this compares the two directly. Room's generated `AppDatabase_Impl` contains
the authoritative DDL for every table; `Migrations.kt` contains what the
migration will actually run. For every table a migration creates, the two
statements must be identical once whitespace is normalised.

Run after a build that has generated the KSP output:

    ./gradlew :app:kspDebugKotlin && python3 tools/check-migration.py
"""

from __future__ import annotations

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
MIGRATIONS = ROOT / "android/app/src/main/java/il/co/tradesmanager/data/local/Migrations.kt"
GENERATED = ROOT / "android/app/build/generated/ksp"

# "CREATE TABLE IF NOT EXISTS `accounts` (...)" -> accounts
TABLE_NAME = re.compile(r"CREATE\s+TABLE\s+IF\s+NOT\s+EXISTS\s+`([^`]+)`", re.IGNORECASE)
INDEX_NAME = re.compile(r"CREATE\s+(?:UNIQUE\s+)?INDEX\s+IF\s+NOT\s+EXISTS\s+`([^`]+)`", re.IGNORECASE)


def normalise(sql: str) -> str:
    """Whitespace and trailing semicolons are not part of the schema."""
    return re.sub(r"\s+", " ", sql).strip().rstrip(";")


LITERAL = r'"(?:[^"\\]|\\.)*"'
# A Kotlin compile-time concatenation: literals joined by `+` and nothing else.
CHAIN = re.compile(LITERAL + r"(?:\s*\+\s*" + LITERAL + r")*")
COMMENTS = re.compile(r"//[^\n]*|/\*.*?\*/", re.DOTALL)


def kotlin_statements(source: str) -> list[str]:
    """Every SQL statement in Migrations.kt, with Kotlin's `+` joins resolved."""
    statements: list[str] = []
    for chain in CHAIN.findall(COMMENTS.sub("", source)):
        joined = "".join(
            part[1:-1].encode().decode("unicode_escape")
            for part in re.findall(LITERAL, chain)
        )
        if TABLE_NAME.match(joined.strip()) or INDEX_NAME.match(joined.strip()):
            statements.append(normalise(joined))
    return statements


def generated_statements() -> dict[str, str]:
    """Table/index name -> the DDL Room generates for it."""
    impls = list(GENERATED.rglob("AppDatabase_Impl.*"))
    if not impls:
        sys.exit(
            f"No AppDatabase_Impl found under {GENERATED}.\n"
            "Run a build first:  cd android && ./gradlew :app:kspDebugKotlin"
        )

    found: dict[str, str] = {}
    for impl in impls:
        body = impl.read_text(encoding="utf-8")
        for raw in re.findall(r'"((?:[^"\\]|\\.)*)"', body):
            text = normalise(raw.encode().decode("unicode_escape"))
            match = TABLE_NAME.match(text) or INDEX_NAME.match(text)
            if match:
                found[match.group(1)] = text
    return found


def main() -> int:
    migration_sql = kotlin_statements(MIGRATIONS.read_text(encoding="utf-8"))
    if not migration_sql:
        print("No CREATE statements in Migrations.kt — nothing to check.")
        return 0

    expected = generated_statements()
    problems: list[str] = []

    for statement in migration_sql:
        match = TABLE_NAME.match(statement) or INDEX_NAME.match(statement)
        if not match:
            continue
        name = match.group(1)
        room = expected.get(name)
        if room is None:
            problems.append(
                f"{name}: the migration creates it, but no entity does.\n"
                f"    migration: {statement}"
            )
        elif room != statement:
            problems.append(
                f"{name}: the migration and the entity disagree.\n"
                f"    migration: {statement}\n"
                f"    room:      {room}"
            )

    if problems:
        print("Migration SQL does not match the schema Room expects.\n")
        print(
            "Room revalidates on the first open after migrating, so this would "
            "crash on launch\nfor everyone who already has the app.\n"
        )
        for problem in problems:
            print(f"  {problem}\n")
        return 1

    checked = ", ".join(
        sorted(
            (TABLE_NAME.match(s) or INDEX_NAME.match(s)).group(1)
            for s in migration_sql
            if TABLE_NAME.match(s) or INDEX_NAME.match(s)
        )
    )
    print(f"Migration SQL matches the schema Room expects ({checked}).")
    return 0


if __name__ == "__main__":
    sys.exit(main())
