#!/usr/bin/env python3
"""Runs the migrations on a real SQLite engine, with rows in them.

`check-migration.py` compares the DDL the migrations produce against the DDL
Room expects. That is a comparison of two pieces of text, and it is blind to
two things that a phone is not:

  * whether the statements are valid SQL at all — it only reads CREATE, ALTER
    and DROP, so an INSERT or an UPDATE with a mistake in it is invisible to
    that check and fatal on a device;
  * whether a migration that rebuilds a table carries the rows across.

The second is the dangerous one. SQLite cannot loosen a NOT NULL in place, so
changing one means building the table again, copying the rows, dropping the
original and renaming. Forget a column in the INSERT and the schema still
comes out exactly right — every other check passes — while the data quietly
does not arrive. A site diary that stops in March looks like a database that
worked.

So this replays the blocks in order against an in-memory database, seeding a
row into every table after each step. Any statement that will not run says so,
and any table that comes out of a step with fewer rows than it went in with
says so.

What it deliberately does not do is invent the version-1 schema. Room creates
those tables itself on a fresh install and nothing in this repository records
what they looked like at the time, so statements against them are counted and
skipped rather than guessed at. Every table a migration creates is covered in
full, which is where a rebuild lives.

    python3 tools/run-migrations.py
"""

from __future__ import annotations

import pathlib
import re
import sqlite3
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
MIGRATIONS = ROOT / "android/app/src/main/java/il/co/tradesmanager/data/local/Migrations.kt"

LITERAL = r'"(?:[^"\\]|\\.)*"'
# A Kotlin compile-time concatenation: literals joined by `+` and nothing else.
CHAIN = re.compile(LITERAL + r"(?:\s*\+\s*" + LITERAL + r")*")
COMMENTS = re.compile(r"//[^\n]*|/\*.*?\*/", re.DOTALL)
SQL_BLOCK = re.compile(r"val\s+SQL_(\d+)_(\d+)\s*:\s*List<String>\s*=\s*listOf\(")


def blocks() -> list[tuple[int, int, list[str]]]:
    """Every migration's statements, in the order a phone will run them.

    Ordered by the version each block migrates from rather than by where it
    sits in the file, for the same reason check-migration.py does it: a table
    created at version 2 and rebuilt at version 30 has to be replayed that way
    round, and nothing keeps the file sorted but habit.
    """
    source = MIGRATIONS.read_text(encoding="utf-8")
    found: list[tuple[int, int, list[str]]] = []
    matches = list(SQL_BLOCK.finditer(source))
    for index, match in enumerate(matches):
        # The block ends at the next one, or at the end of the file.
        end = matches[index + 1].start() if index + 1 < len(matches) else len(source)
        body = COMMENTS.sub("", source[match.end():end])
        statements = [
            "".join(
                part[1:-1].encode().decode("unicode_escape")
                for part in re.findall(LITERAL, chain)
            )
            for chain in CHAIN.findall(body)
        ]
        found.append(
            (int(match.group(1)), int(match.group(2)), [s for s in statements if s.strip()])
        )
    return sorted(found, key=lambda block: block[0])


# The table a statement acts on, for the four shapes the migrations use.
TARGET = re.compile(
    r"^\s*(?:"
    r"CREATE\s+TABLE(?:\s+IF\s+NOT\s+EXISTS)?\s+|"
    r"ALTER\s+TABLE\s+|"
    r"DROP\s+TABLE(?:\s+IF\s+EXISTS)?\s+|"
    r"INSERT\s+(?:OR\s+\w+\s+)?INTO\s+|"
    r"UPDATE\s+|"
    r"DELETE\s+FROM\s+|"
    r"CREATE\s+(?:UNIQUE\s+)?INDEX(?:\s+IF\s+NOT\s+EXISTS)?\s+`?[^`\s]+`?\s+ON\s+"
    r")`?([A-Za-z_][A-Za-z0-9_]*)`?",
    re.IGNORECASE,
)


def target_of(statement: str) -> str | None:
    """Which table a statement is about, or None when it does not say."""
    match = TARGET.match(statement)
    return match.group(1) if match else None


def migration_owned(chain: list[tuple[int, int, list[str]]]) -> set[str]:
    """Every table the migrations create for themselves.

    Anything else came with version 1, built by Room from the entities of the
    day. Replaying an ALTER against a table this run never created would fail
    for a reason that says nothing about the migration.
    """
    created: set[str] = set()
    for _, _, statements in chain:
        for statement in statements:
            if re.match(r"\s*CREATE\s+TABLE", statement, re.IGNORECASE):
                name = target_of(statement)
                if name:
                    created.add(name)
    return created


def tables(db: sqlite3.Connection) -> list[str]:
    rows = db.execute(
        "SELECT name FROM sqlite_master WHERE type = 'table' AND name NOT LIKE 'sqlite_%'"
    )
    return sorted(name for (name,) in rows)


def counts(db: sqlite3.Connection) -> dict[str, int]:
    return {name: db.execute(f"SELECT COUNT(*) FROM `{name}`").fetchone()[0] for name in tables(db)}


def seed(db: sqlite3.Connection, stamp: str) -> None:
    """One synthetic row in every table, so a later rebuild has data to lose.

    `INSERT OR IGNORE`, because a unique index is a legitimate reason for a
    row not to land and not a reason to fail the run. The counts are asserted
    as "no fewer than before", which is the property that matters, rather than
    "exactly one more", which a unique index would break.
    """
    for name in tables(db):
        columns = db.execute(f"PRAGMA table_info(`{name}`)").fetchall()
        if not columns:
            continue
        values = []
        for index, (_, column, declared, _notnull, _default, _pk) in enumerate(columns):
            kind = (declared or "TEXT").upper()
            if "INT" in kind:
                values.append(hash((stamp, name, column)) % 1_000_000)
            elif "REAL" in kind or "FLOA" in kind or "DOUB" in kind:
                values.append(1.5)
            elif "BLOB" in kind:
                values.append(b"\x00")
            else:
                values.append(f"{stamp}-{name}-{column}-{index}")
        placeholders = ", ".join("?" for _ in columns)
        names = ", ".join(f"`{row[1]}`" for row in columns)
        db.execute(f"INSERT OR IGNORE INTO `{name}` ({names}) VALUES ({placeholders})", values)


def main() -> int:
    chain = blocks()
    if not chain:
        print(f"No SQL_x_y blocks in {MIGRATIONS.name} — nothing to run.")
        return 0

    owned = migration_owned(chain)
    db = sqlite3.connect(":memory:")
    problems: list[str] = []
    before: dict[str, int] = {}
    skipped = 0

    for start, end, statements in chain:
        for statement in statements:
            target = target_of(statement)
            if target is not None and target not in owned:
                skipped += 1
                continue
            try:
                db.execute(statement)
            except sqlite3.Error as error:
                problems.append(
                    f"Migration {start} to {end} will not run:\n"
                    f"    {error}\n"
                    f"    {statement[:200]}"
                )
        after = counts(db)
        for name, was in before.items():
            if name not in after:
                # Dropped and not renamed back. Legitimate when the table is
                # genuinely gone; loud when it is a rebuild missing its rename.
                problems.append(
                    f"Migration {start} to {end} leaves no table `{name}`.\n"
                    f"    It held {was} rows before this step."
                )
            elif after[name] < was:
                problems.append(
                    f"Migration {start} to {end} loses rows from `{name}`: "
                    f"{was} before, {after[name]} after.\n"
                    f"    A rebuild that forgets a column in its INSERT looks "
                    f"exactly like this and passes every other check."
                )
        seed(db, f"v{end}")
        before = counts(db)

    stray = [name for name in tables(db) if name.endswith("_new") or name.endswith("_old")]
    if stray:
        problems.append(
            f"A rebuild left its working table behind: {', '.join(stray)}.\n"
            f"    Room validates the schema it finds, and an extra table is a "
            f"schema it does not expect."
        )

    if problems:
        print("The migration chain does not survive a real SQLite engine.\n")
        for problem in problems:
            print(f"  {problem}\n")
        return 1

    ran = sum(len(s) for _, _, s in chain) - skipped
    print(
        f"The migrations run: {len(chain)} steps, {ran} statements, "
        f"{len(tables(db))} tables, no rows lost."
    )
    print(
        f"{skipped} statements skipped: they alter tables Room creates itself "
        f"at version 1, whose original shape this repository does not record."
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())
