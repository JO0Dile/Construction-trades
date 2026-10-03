#!/usr/bin/env python3
"""Keep the catalogue translation sheets in step with the catalogue.

Three worksheets in docs/translation/ are made from shared/assets/catalog/:

    items.csv                  every item the app can stock
    safety-and-templates.csv   checklist titles, safety checks, job templates
    work-breakdown.csv         trades, stages, phases and scopes of work

They were written once, by hand, and the catalogue kept growing after them:
thirty hand tools went into the app that no translator was ever shown. This
rebuilds them from the catalogue, in catalogue order, and carries across
every column a person fills in, keyed on the row's id. Nothing anybody typed
is ever lost: a row for something the catalogue has since dropped goes only
if nobody answered it.

Run it after changing the catalogue:

    python3 tools/gen-catalog-sheets.py

CI runs it with --check, which changes nothing and fails if a sheet is
behind the catalogue.
"""

from __future__ import annotations

import csv
import io
import json
import pathlib
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
CATALOG = ROOT / "shared" / "assets" / "catalog"
SHEETS = ROOT / "docs" / "translation"

CRITICAL = "CRITICAL — blocks sign-off"
BEFORE_WORK = "must be done before work"


def load(path: str) -> dict:
    return json.loads((CATALOG / path).read_text(encoding="utf-8"))


def text(block: dict | None, language: str) -> str:
    return (block or {}).get(language, "")


def drafts(block: dict | None) -> dict[str, str]:
    return {
        "english": text(block, "en"),
        "hebrew_draft": text(block, "he"),
        "arabic_draft": text(block, "ar"),
    }


def items(manifest: dict) -> list[dict[str, str]]:
    rows = []
    for trade in manifest["trades"]:
        for item in load(trade["itemsFile"])["items"]:
            rows.append(
                {
                    "trade": trade["names"]["en"],
                    "id": item["id"],
                    "kind": item["kind"],
                    "category": item["category"],
                    **drafts(item["names"]),
                }
            )
    return rows


def safety_and_templates(manifest: dict) -> list[dict[str, str]]:
    rows = []
    for trade in manifest["trades"]:
        checks, templates = [], []
        name = trade["names"]["en"]
        if trade.get("safetyFile"):
            for checklist in load(trade["safetyFile"])["checklists"]:
                checks.append(
                    {
                        "trade": name,
                        "kind": "checklist title",
                        "id": checklist["id"],
                        "context": BEFORE_WORK
                        if checklist.get("mandatoryBeforeWork")
                        else "",
                        **drafts(checklist["titles"]),
                    }
                )
                for check in checklist["items"]:
                    checks.append(
                        {
                            "trade": name,
                            "kind": "safety check",
                            "id": check["id"],
                            "context": CRITICAL if check.get("critical") else "",
                            **drafts(check["texts"]),
                        }
                    )
        if trade.get("templatesFile"):
            for template in load(trade["templatesFile"])["templates"]:
                for kind, block in (
                    ("template name", template["names"]),
                    ("template description", template.get("descriptions")),
                ):
                    templates.append(
                        {
                            "trade": name,
                            "kind": kind,
                            "id": template["id"],
                            "context": "",
                            **drafts(block),
                        }
                    )
                for task in sorted(template["tasks"], key=lambda t: t["order"]):
                    templates.append(
                        {
                            "trade": name,
                            "kind": "template task",
                            "id": task["id"],
                            "context": f"step {task['order']}",
                            **drafts(task["titles"]),
                        }
                    )
        rows += checks + templates
    return rows


def work_breakdown(manifest: dict) -> list[dict[str, str]]:
    scopes = load(manifest["scopesFile"])
    rows = []
    for kind, entries in (
        ("stage", scopes["stages"]),
        ("phase", scopes["phases"]),
        ("scope", scopes["scopes"]),
        ("trade", manifest["trades"]),
    ):
        for entry in entries:
            rows.append(
                {
                    "type": kind,
                    "id": entry["id"],
                    **drafts(entry["names"]),
                    "street_arabic": text(entry.get("colloquial"), "ar"),
                }
            )
    return rows


# file, how a row is told apart from the others, what the app writes, what a
# person writes, and how the rows are made.
SHEET_SPECS = (
    (
        "items.csv",
        ("id",),
        ["trade", "id", "kind", "category", "english", "hebrew_draft", "arabic_draft"],
        ["hebrew_yours", "arabic_yours", "image_note"],
        items,
    ),
    (
        "safety-and-templates.csv",
        ("kind", "id"),
        ["trade", "kind", "id", "context", "english", "hebrew_draft", "arabic_draft"],
        ["hebrew_yours", "arabic_yours"],
        safety_and_templates,
    ),
    (
        "work-breakdown.csv",
        ("type", "id"),
        ["type", "id", "english", "hebrew_draft", "arabic_draft", "street_arabic"],
        ["hebrew_yours", "arabic_yours", "street_yours"],
        work_breakdown,
    ),
)


def build(name, key, ours, theirs, make, manifest):
    path = SHEETS / name
    existing: dict[tuple, dict[str, str]] = {}
    if path.exists():
        with io.open(path, encoding="utf-8-sig", newline="") as handle:
            for row in csv.DictReader(handle):
                existing[tuple(row[k] for k in key)] = row

    rows, seen = [], set()
    for fresh in make(manifest):
        identity = tuple(fresh[k] for k in key)
        assert identity not in seen, f"{name}: {identity} appears twice"
        seen.add(identity)
        was = existing.get(identity, {})
        rows.append({**fresh, **{column: was.get(column, "") for column in theirs}})

    added = [identity for identity in seen if identity not in existing]
    gone = [identity for identity in existing if identity not in seen]
    kept = [
        existing[identity]
        for identity in gone
        if any(existing[identity].get(column, "").strip() for column in theirs)
    ]
    return path, ours + theirs, rows + kept, added, gone


def render(columns, rows) -> str:
    buffer = io.StringIO(newline="")
    writer = csv.DictWriter(buffer, fieldnames=columns, lineterminator="\r\n")
    writer.writeheader()
    writer.writerows(rows)
    return buffer.getvalue()


def main() -> int:
    check = "--check" in sys.argv[1:]
    manifest = load("manifest.json")
    behind = 0
    for spec in SHEET_SPECS:
        path, columns, rows, added, gone = build(*spec, manifest)
        wanted = render(columns, rows)
        where = path.relative_to(ROOT)
        if check:
            current = path.read_text(encoding="utf-8-sig") if path.exists() else ""
            if current.replace("\r\n", "\n") == wanted.replace("\r\n", "\n"):
                print(f"{where} matches the catalogue: {len(rows)} rows.")
                continue
            behind += 1
            print(f"{where} is behind the catalogue.", file=sys.stderr)
            if added:
                shown = ", ".join("/".join(i) for i in added[:10])
                more = " ..." if len(added) > 10 else ""
                print(f"  never asked for ({len(added)}): {shown}{more}", file=sys.stderr)
            if gone:
                print(f"  no longer in the catalogue ({len(gone)})", file=sys.stderr)
            continue
        # utf-8-sig so that Excel on a Hebrew or Arabic Windows opens it as
        # UTF-8 rather than mangling every non-Latin character in the file.
        with io.open(path, "w", encoding="utf-8-sig", newline="") as handle:
            handle.write(wanted)
        print(f"{where}: {len(rows)} rows, {len(added)} added, {len(gone)} dropped.")
    if behind:
        print("  run: python3 tools/gen-catalog-sheets.py", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
