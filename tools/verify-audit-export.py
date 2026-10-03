#!/usr/bin/env python3
"""Check an exported audit trail without the app.

The app signs each audit entry against the one before it. This recomputes
those signatures from the exported CSV, so an inspector, an auditor or a court
does not have to take the app's word for its own verdict — which is the only
kind of integrity claim worth anything.

    python3 tools/verify-audit-export.py audit-trail-2026-09-07.csv

Exit status is 0 if the trail verifies and 1 if it does not, so it can be run
from a script.

The CSV is the file to use. The PDF exported alongside it shortens the
signatures to stay readable on A4 and does not carry the fields below.

## What is hashed

SHA-256 over these values, in this order, each prefixed with its length in
UTF-8 bytes and followed by a semicolon:

    previousHash, sequence, entityType, entityId, action,
    actorId, actorName, summary, payloadJson, occurredAtMillis

A field that is absent is written as `-;` instead, which no length-prefixed
value can produce. In the CSV an empty cell for `actorId` or `payloadJson`
means absent: the app never stores an empty string in those columns.

The length prefix counts bytes, not characters, so that no value can be made
to look like the end of one field and the start of the next.

`occurredAtMillis` is what was hashed. The readable "When" column is the same
instant formatted for a person and must not be used here.

The first data row is the app's own verdict at the moment of export, not an
entry. It has no sequence number and is skipped.
"""

from __future__ import annotations

import csv
import hashlib
import io
import sys

GENESIS = "0" * 64


def field(value: str | None) -> bytes:
    if value is None:
        return b"-;"
    raw = value.encode("utf-8")
    return f"{len(raw)}:".encode("ascii") + raw + b";"


def link(row: dict[str, str]) -> str:
    digest = hashlib.sha256()
    for value in (
        row["previousHash"],
        row["No."],
        row["entityType"],
        row["entityId"],
        row["Action"],
        row["actorId"] or None,
        row["Who"],
        row["What happened"],
        row["payloadJson"] or None,
        row["occurredAtMillis"],
    ):
        digest.update(field(value))
    return digest.hexdigest()


def verify(rows: list[dict[str, str]]) -> list[str]:
    """Returns the problems found, oldest first. Empty means it verifies."""
    problems: list[str] = []
    entries = [r for r in rows if r.get("No.", "").strip()]
    signed = [r for r in entries if r.get("Signature", "").strip()]
    unsigned = len(entries) - len(signed)

    if not signed:
        if unsigned:
            print(f"No signed entries. {unsigned} predate the signing and cannot be checked.")
        else:
            print("Nothing to check.")
        return problems

    signed.sort(key=lambda r: int(r["No."]))
    previous = None
    for row in signed:
        number = int(row["No."])
        expected = link(row)
        if expected != row["Signature"]:
            problems.append(
                f"entry {number}: says something other than what was signed "
                f"(expected {expected[:16]}…, carries {row['Signature'][:16]}…)"
            )
        elif previous is not None:
            if number != int(previous["No."]) + 1:
                problems.append(
                    f"entry {number}: an entry between {previous['No.']} and {number} is gone"
                )
            elif row["previousHash"] != previous["Signature"]:
                problems.append(
                    f"entry {number}: does not follow entry {previous['No.']}; "
                    "something was removed or replaced"
                )
        previous = row

    # The first surviving entry points at something that is not in the file.
    # That is the edge of the trail — the start, or where a retention purge
    # cut — and is the one break this cannot tell from a deletion. The app
    # records a PURGE entry saying what it cut, which is where to look.
    first = signed[0]
    if first["previousHash"] != GENESIS:
        print(
            f"Note: the trail starts at entry {first['No.']}, which follows an entry "
            "not in this file. Expect a PURGE entry explaining the cut."
        )
    if unsigned:
        print(f"Note: {unsigned} entries predate the signing and cannot be checked.")
    print(f"Checked {len(signed)} signed entries.")
    return problems


def main(argv: list[str]) -> int:
    if len(argv) != 2:
        print(__doc__)
        return 2
    with io.open(argv[1], encoding="utf-8-sig", newline="") as handle:
        rows = list(csv.DictReader(handle))

    required = {
        "No.", "Who", "Action", "What happened", "Signature",
        "previousHash", "entityType", "entityId", "actorId",
        "payloadJson", "occurredAtMillis",
    }
    missing = required - set(rows[0] if rows else {})
    if missing:
        print(f"This is not a full audit export. Missing columns: {', '.join(sorted(missing))}")
        print("Export again from Settings -> Audit trail and use the CSV, not the PDF.")
        return 2

    problems = verify(rows)
    if problems:
        print("\nThe trail does not add up:")
        for problem in problems:
            print(f"  {problem}")
        return 1
    print("The trail verifies.")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
