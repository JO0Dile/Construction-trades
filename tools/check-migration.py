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
# "ALTER TABLE `accounts` ADD COLUMN `idNumber` TEXT" -> (accounts, idNumber, TEXT)
ADD_COLUMN = re.compile(
    r"ALTER\s+TABLE\s+`([^`]+)`\s+ADD\s+(?:COLUMN\s+)?`([^`]+)`\s*(.*)$",
    re.IGNORECASE,
)


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
        stripped = joined.strip()
        if TABLE_NAME.match(stripped) or INDEX_NAME.match(stripped) or ADD_COLUMN.match(stripped):
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


def split_columns(create_table: str) -> dict[str, str]:
    """Column name -> its definition, out of a Room CREATE TABLE statement.

    A column added by ALTER TABLE has to end up identical to the one Room
    generates from the entity, or the schema check on the next open fails just
    as loudly as a mismatched CREATE TABLE would.
    """
    body = create_table[create_table.index("(") + 1 : create_table.rindex(")")]
    parts: list[str] = []
    depth = 0
    current = ""
    for char in body:
        if char == "(":
            depth += 1
        elif char == ")":
            depth -= 1
        if char == "," and depth == 0:
            parts.append(current)
            current = ""
        else:
            current += char
    parts.append(current)

    columns: dict[str, str] = {}
    for part in parts:
        piece = part.strip()
        match = re.match(r"`([^`]+)`\s*(.*)$", piece)
        if match:
            columns[match.group(1)] = normalise(match.group(2))
    return columns


def check_added_column(statement: str, expected: dict[str, str]) -> str | None:
    """Verifies one ALTER TABLE ADD COLUMN against the entity's own DDL."""
    match = ADD_COLUMN.match(statement)
    if not match:
        return None
    table, column, definition = match.group(1), match.group(2), normalise(match.group(3))

    create = expected.get(table)
    if create is None:
        return f"{table}.{column}: the migration alters a table no entity declares."

    room = split_columns(create).get(column)
    if room is None:
        return (
            f"{table}.{column}: the migration adds it, but the entity has no such column.\n"
            f"    migration: {definition}"
        )
    if room != definition:
        return (
            f"{table}.{column}: the migration and the entity disagree.\n"
            f"    migration: {definition}\n"
            f"    room:      {room}"
        )
    # SQLite cannot add a NOT NULL column without a DEFAULT, and Room does not
    # know about a default the entity never declared. Catching it here beats
    # catching it as a crash on launch.
    if "NOT NULL" in definition.upper() and "DEFAULT" not in definition.upper():
        return (
            f"{table}.{column}: SQLite refuses a NOT NULL column with no DEFAULT.\n"
            f"    Make the property nullable, or declare a matching "
            f"@ColumnInfo(defaultValue = ...)."
        )
    return None


def main() -> int:
    migration_sql = kotlin_statements(MIGRATIONS.read_text(encoding="utf-8"))
    if not migration_sql:
        print("No CREATE or ALTER statements in Migrations.kt — nothing to check.")
        return 0

    expected = generated_statements()
    problems: list[str] = []

    for statement in migration_sql:
        if ADD_COLUMN.match(statement):
            problem = check_added_column(statement, expected)
            if problem:
                problems.append(problem)
            continue

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

    names: set[str] = set()
    for statement in migration_sql:
        created = TABLE_NAME.match(statement) or INDEX_NAME.match(statement)
        if created:
            names.add(created.group(1))
            continue
        altered = ADD_COLUMN.match(statement)
        if altered:
            names.add(f"{altered.group(1)}.{altered.group(2)}")
    checked = ", ".join(sorted(names))
    print(f"Migration SQL matches the schema Room expects ({checked}).")
    return 0


if __name__ == "__main__":
    sys.exit(main())
