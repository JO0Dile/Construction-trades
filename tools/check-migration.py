#!/usr/bin/env python3
"""Proves the migrations end up with exactly the schema Room expects.

Room revalidates the whole schema the first time the database opens after
migrating. If the tables a migration leaves behind differ from the ones Room
generates from the entities — a column type, a nullability, the name of an
index — it throws, and every person who already has the app gets a crash on
launch instead of an upgrade. Nothing else in the build catches that: it
compiles, it lints, it passes the unit tests, and it dies on the device.

So this replays the migrations and compares the result. `Migrations.kt` says
what will run; Room's generated `AppDatabase_Impl` holds the authoritative DDL.
A table is checked as it ends up, not as any one migration leaves it: version 2
created `accounts` with nine columns and version 8 adds four more, and both
statements are right. Comparing either against the entity on its own is not.

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

TABLE_NAME = re.compile(r"CREATE\s+TABLE\s+IF\s+NOT\s+EXISTS\s+`([^`]+)`", re.IGNORECASE)
INDEX_NAME = re.compile(r"CREATE\s+(?:UNIQUE\s+)?INDEX\s+IF\s+NOT\s+EXISTS\s+`([^`]+)`", re.IGNORECASE)
# "ALTER TABLE `accounts` ADD COLUMN `idNumber` TEXT" -> (accounts, idNumber, TEXT)
ADD_COLUMN = re.compile(
    r"ALTER\s+TABLE\s+`([^`]+)`\s+ADD\s+(?:COLUMN\s+)?`([^`]+)`\s*(.*)$",
    re.IGNORECASE,
)
# "val SQL_7_8: List<String> = listOf(" -> (7, 8)
SQL_BLOCK = re.compile(r"val\s+SQL_(\d+)_(\d+)\s*:\s*List<String>\s*=\s*listOf\(")


def normalise(sql: str) -> str:
    """Whitespace and trailing semicolons are not part of the schema."""
    return re.sub(r"\s+", " ", sql).strip().rstrip(";")


LITERAL = r'"(?:[^"\\]|\\.)*"'
# A Kotlin compile-time concatenation: literals joined by `+` and nothing else.
CHAIN = re.compile(LITERAL + r"(?:\s*\+\s*" + LITERAL + r")*")
COMMENTS = re.compile(r"//[^\n]*|/\*.*?\*/", re.DOTALL)


def statements_in(source: str) -> list[str]:
    """Every SQL statement in a chunk of Kotlin, with `+` joins resolved."""
    found: list[str] = []
    for chain in CHAIN.findall(COMMENTS.sub("", source)):
        joined = "".join(
            part[1:-1].encode().decode("unicode_escape")
            for part in re.findall(LITERAL, chain)
        )
        stripped = joined.strip()
        if TABLE_NAME.match(stripped) or INDEX_NAME.match(stripped) or ADD_COLUMN.match(stripped):
            found.append(normalise(joined))
    return found


def migration_statements(source: str) -> list[str]:
    """Every statement, in the order the migrations will actually run them.

    Ordered by the version each SQL_x_y block migrates from, rather than by
    where it happens to sit in the file. A table created at version 2 and
    altered at version 8 has to be replayed in that order, and relying on
    somebody keeping the file sorted is the sort of assumption that holds until
    the day it does not.
    """
    blocks: list[tuple[int, str]] = []
    matches = list(SQL_BLOCK.finditer(source))
    for index, match in enumerate(matches):
        end = matches[index + 1].start() if index + 1 < len(matches) else len(source)
        blocks.append((int(match.group(1)), source[match.end():end]))

    ordered: list[str] = []
    for _, body in sorted(blocks, key=lambda pair: pair[0]):
        ordered.extend(statements_in(body))
    return ordered


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


def split_body(create_table: str) -> tuple[dict[str, str], list[str]]:
    """A CREATE TABLE's columns, and its table-level constraints.

    Returns ({column name: definition}, [PRIMARY KEY(...), FOREIGN KEY(...)]).
    Splitting on top-level commas only, so the commas inside PRIMARY KEY(`a`,
    `b`) do not tear a constraint in half.
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
    constraints: list[str] = []
    for part in parts:
        piece = part.strip()
        if not piece:
            continue
        match = re.match(r"`([^`]+)`\s*(.*)$", piece)
        if match:
            columns[match.group(1)] = normalise(match.group(2))
        else:
            constraints.append(normalise(piece))
    return columns, constraints


def replay(statements: list[str]) -> tuple[dict[str, str], dict[str, list[tuple[str, str]]], list[str]]:
    """What the migrations do, gathered per table."""
    created: dict[str, str] = {}
    added: dict[str, list[tuple[str, str]]] = {}
    indexes: list[str] = []

    for statement in statements:
        table = TABLE_NAME.match(statement)
        if table:
            created.setdefault(table.group(1), statement)
            continue
        index = INDEX_NAME.match(statement)
        if index:
            indexes.append(statement)
            continue
        alter = ADD_COLUMN.match(statement)
        if alter:
            added.setdefault(alter.group(1), []).append(
                (alter.group(2), normalise(alter.group(3)))
            )
    return created, added, indexes


def check_table(name: str, create: str, additions: list[tuple[str, str]], room: str) -> list[str]:
    """Compares a table as the migrations leave it against the entity's DDL."""
    problems: list[str] = []
    columns, constraints = split_body(create)
    for column, definition in additions:
        columns[column] = definition

    room_columns, room_constraints = split_body(room)

    for column in sorted(set(columns) | set(room_columns)):
        ours = columns.get(column)
        theirs = room_columns.get(column)
        if ours is None:
            problems.append(
                f"{name}.{column}: the entity has this column and no migration creates or adds it.\n"
                f"    room: {theirs}"
            )
        elif theirs is None:
            problems.append(
                f"{name}.{column}: the migrations create it, but the entity has no such column.\n"
                f"    migration: {ours}"
            )
        elif ours != theirs:
            problems.append(
                f"{name}.{column}: the migrations and the entity disagree.\n"
                f"    migration: {ours}\n"
                f"    room:      {theirs}"
            )

    if constraints != room_constraints:
        problems.append(
            f"{name}: table constraints differ.\n"
            f"    migration: {constraints}\n"
            f"    room:      {room_constraints}"
        )
    return problems


def check_added_column(table: str, column: str, definition: str, room: str) -> list[str]:
    """One ALTER on a table no migration created — it came with version 1."""
    room_columns, _ = split_body(room)
    theirs = room_columns.get(column)
    if theirs is None:
        return [
            f"{table}.{column}: the migration adds it, but the entity has no such column.\n"
            f"    migration: {definition}"
        ]
    if theirs != definition:
        return [
            f"{table}.{column}: the migration and the entity disagree.\n"
            f"    migration: {definition}\n"
            f"    room:      {theirs}"
        ]
    return []


def main() -> int:
    source = MIGRATIONS.read_text(encoding="utf-8")
    statements = migration_statements(source)
    if not statements:
        print("No CREATE or ALTER statements in Migrations.kt — nothing to check.")
        return 0

    expected = generated_statements()
    created, added, indexes = replay(statements)
    problems: list[str] = []
    checked: set[str] = set()

    for name, create in created.items():
        room = expected.get(name)
        if room is None:
            problems.append(f"{name}: the migrations create it, but no entity does.")
            continue
        problems.extend(check_table(name, create, added.get(name, []), room))
        checked.add(name)

    for table, additions in added.items():
        if table in created:
            continue
        room = expected.get(table)
        if room is None:
            problems.append(f"{table}: the migrations alter a table no entity declares.")
            continue
        for column, definition in additions:
            problems.extend(check_added_column(table, column, definition, room))
            # SQLite cannot add a NOT NULL column without a DEFAULT, and Room
            # does not know about a default the entity never declared. Catching
            # it here beats catching it as a crash on launch.
            upper = definition.upper()
            if "NOT NULL" in upper and "DEFAULT" not in upper:
                problems.append(
                    f"{table}.{column}: SQLite refuses a NOT NULL column with no DEFAULT.\n"
                    f"    Make the property nullable, or declare a matching "
                    f"@ColumnInfo(defaultValue = ...)."
                )
            checked.add(f"{table}.{column}")

    for statement in indexes:
        name = INDEX_NAME.match(statement).group(1)
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
        checked.add(name)

    if problems:
        print("Migration SQL does not match the schema Room expects.\n")
        print(
            "Room revalidates on the first open after migrating, so this would "
            "crash on launch\nfor everyone who already has the app.\n"
        )
        for problem in problems:
            print(f"  {problem}\n")
        return 1

    print(f"Migration SQL matches the schema Room expects ({', '.join(sorted(checked))}).")
    return 0


if __name__ == "__main__":
    sys.exit(main())
