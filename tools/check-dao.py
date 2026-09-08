#!/usr/bin/env python3
"""Catch DAO declarations that KSP would reject, before CI has to.

Two mistakes cost a whole CI cycle each, because Gradle cannot run here:

  * a Room annotation that does not sit immediately above its function --
    slip a KDoc or a second annotation in between and the query silently
    binds to the wrong function, which is how `byIdNumber` ended up
    declaring it returned COUNT(*).
  * an abstract DAO method with no Room annotation at all, which is the
    other half of the same slip.

Neither is a style question: both fail `kspDebugKotlin` outright.
"""
from __future__ import annotations

import re
import sys
from pathlib import Path

ROOM = {
    "Query", "Insert", "Update", "Delete", "Upsert", "RawQuery",
    "Transaction", "Ignore",
}
# Transaction may pair with another annotation or wrap a concrete method, and
# Ignore removes a method from Room's view, so neither one alone is a binding.
BINDING = ROOM - {"Transaction", "Ignore"}

DAO_DIR = Path(__file__).resolve().parents[1] / (
    "android/app/src/main/java/il/co/tradesmanager/data/local/dao"
)

FUN = re.compile(r"^\s*(?:@[\w.]+\s+)*(?:suspend\s+)?fun\s+(\w+)")
ANNOTATION = re.compile(r"^\s*@([\w.]+)")


def short(name: str) -> str:
    """`androidx.room.Upsert` and `Upsert` are the same annotation."""
    return name.rsplit(".", 1)[-1]


def annotations_and_funs(text: str) -> list[tuple[int, str, str]]:
    """Walk a DAO body, pairing each annotation block with what follows it.

    Returns (line number, kind, name) where kind is "annotation", "fun" (an
    abstract method Room has to implement) or "body" (a concrete default
    method, which Room leaves alone). Multi-line annotations and signatures
    are collapsed by counting parentheses, so a triple-quoted query spanning
    six lines still reads as one entry.
    """
    out: list[tuple[int, str, str]] = []
    lines = text.splitlines()
    i = 0
    while i < len(lines):
        line = lines[i]
        stripped = line.strip()
        if stripped.startswith("/*") or stripped.startswith("*") or stripped.startswith("//"):
            i += 1
            continue
        m = ANNOTATION.match(line)
        if m and not FUN.match(line):
            name = m.group(1)
            depth = line.count("(") - line.count(")")
            start = i
            while depth > 0 and i + 1 < len(lines):
                i += 1
                depth += lines[i].count("(") - lines[i].count(")")
            out.append((start + 1, "annotation", short(name)))
            i += 1
            continue
        f = FUN.match(line)
        if f:
            for name in re.findall(r"@([\w.]+)", line.split("fun")[0]):
                out.append((i + 1, "annotation", short(name)))
            start = i
            signature = line
            depth = line.count("(") - line.count(")")
            while depth > 0 and i + 1 < len(lines):
                i += 1
                signature += lines[i]
                depth += lines[i].count("(") - lines[i].count(")")
            tail = signature.split(")")[-1]
            concrete = "{" in tail or "=" in tail
            out.append((start + 1, "body" if concrete else "fun", f.group(1)))
        i += 1
    return out


def check(path: Path) -> list[str]:
    text = path.read_text(encoding="utf-8")
    if "@Dao" not in text:
        return []
    problems: list[str] = []
    pending: list[tuple[int, str]] = []
    for line_no, kind, name in annotations_and_funs(text):
        if kind == "annotation":
            if name in ROOM:
                pending.append((line_no, name))
            # A non-Room annotation between a query and its function is
            # harmless -- @RewriteQueriesToDropUnusedColumns lives there.
            continue
        bound = {n for _, n in pending}
        if kind == "body":
            # A method with a body implements itself; @Transaction alone is
            # the usual and correct annotation for one.
            pending = []
            continue
        if not bound & BINDING and "Ignore" not in bound:
            problems.append(
                f"{path.name}:{line_no}: `{name}` has no Room annotation"
            )
        if len(bound & BINDING) > 1:
            problems.append(
                f"{path.name}:{line_no}: `{name}` carries "
                f"{sorted(bound & BINDING)} -- Room allows one"
            )
        pending = []
    for line_no, name in pending:
        if name in BINDING:
            problems.append(
                f"{path.name}:{line_no}: @{name} is not followed by a function"
            )
    return problems


def main() -> int:
    if not DAO_DIR.is_dir():
        print(f"No DAO directory at {DAO_DIR}", file=sys.stderr)
        return 2
    problems: list[str] = []
    for path in sorted(DAO_DIR.glob("*.kt")):
        problems.extend(check(path))
    if problems:
        print("Room would reject these declarations:")
        for line in problems:
            print(f"  {line}")
        return 1
    print(f"All DAO methods in {DAO_DIR.name}/ are annotated exactly once.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
