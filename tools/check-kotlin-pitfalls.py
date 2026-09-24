#!/usr/bin/env python3
"""Kotlin the compiler will reject, caught before it has to.

Gradle does not run in the container this repository is usually worked on,
so every compile error costs a full round trip through CI to find out about.
The two here have both cost one:

  * a second `companion object` in a class that already has one -- easy to
    add when a constant is needed near the bottom of a long file, and fatal
    ("Only one companion object is allowed per class");
  * `catch (_: SomeException)`, which reads as idiomatic and is not accepted
    by the Kotlin this project builds with;
  * `stringResource(...)` inside the lambda of a function that is not
    inline -- `joinToString { }`, the sort selectors, `lazy { }`. Composable
    calls are allowed inside `map { }` because `map` is inline, and the two
    look identical on the page.

The same reasoning as check-compose-delegates and check-test-imports: not a
style rule, a compile error found in a second instead of a quarter of an hour.
"""
from __future__ import annotations

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SOURCES = [ROOT / "android/app/src/main/java", ROOT / "android/app/src/test/java"]

CLASS = re.compile(r"\b(?:class|object|interface)\s+\w+")
COMPANION = re.compile(r"\bcompanion\s+object\b")
UNDERSCORE_CATCH = re.compile(r"\bcatch\s*\(\s*_\s*:")
# Higher-order functions whose lambda is not inline, or is crossinline: a
# composable call inside one does not compile.
NOT_INLINE = re.compile(
    r"\.(joinToString|sortedBy|sortedByDescending|thenBy|thenByDescending)\s*(\([^()]*\))?\s*\{"
    r"|\b(compareBy|compareByDescending|lazy)\s*(<[^>]*>)?\s*\{"
)
COMPOSABLE_CALL = re.compile(r"\b(stringResource|pluralStringResource|painterResource)\s*\(")


def lambda_body(text: str, open_brace: int) -> str:
    """The text between a lambda's braces, matched on the stripped source."""
    depth = 0
    for index in range(open_brace, len(text)):
        if text[index] == "{":
            depth += 1
        elif text[index] == "}":
            depth -= 1
            if depth == 0:
                return text[open_brace + 1:index]
    return text[open_brace + 1:]


def strip(text: str) -> str:
    """Comments and string contents blanked, so braces and keywords in them do not count."""
    # Blanked rather than removed, and newlines kept, so a line number reported
    # afterwards is still the line in the file.
    text = re.sub(r"/\*.*?\*/", lambda m: re.sub(r"[^\n]", " ", m.group(0)), text, flags=re.S)
    text = re.sub(r"//[^\n]*", "", text)
    text = re.sub(r'"""(?:.|\n)*?"""', lambda m: '""' + "\n" * m.group(0).count("\n"), text)
    text = re.sub(r'"(?:\\.|[^"\\\n])*"', '""', text)
    return text


def companions_per_class(text: str) -> list[int]:
    """Line numbers of every companion object after the first in the same class body."""
    faults: list[int] = []
    # A stack of open braces; each records whether it opened a class-like body
    # and how many companions have been seen directly inside it.
    stack: list[list] = []
    pending_class = False
    i = 0
    while i < len(text):
        m_class = CLASS.match(text, i)
        m_comp = COMPANION.match(text, i)
        if m_comp:
            owner = next((frame for frame in reversed(stack) if frame[0]), None)
            # Only count a companion directly inside the class body, not in a nested one.
            if stack and stack[-1][0]:
                stack[-1][1] += 1
                if stack[-1][1] > 1:
                    faults.append(text.count("\n", 0, i) + 1)
            pending_class = True  # the companion's own body is class-like
            i = m_comp.end()
            continue
        if m_class:
            pending_class = True
            i = m_class.end()
            continue
        c = text[i]
        if c == "{":
            stack.append([pending_class, 0])
            pending_class = False
        elif c == "}":
            if stack:
                stack.pop()
        elif c == "\n":
            pass
        elif c in ";=":
            # `class X(val a: Int)` with no body, or an expression after `=`.
            pending_class = False if c == ";" else pending_class
        i += 1
    return faults


def main() -> int:
    faults: list[str] = []
    files = 0
    for root in SOURCES:
        for path in sorted(root.rglob("*.kt")):
            files += 1
            raw = path.read_text(encoding="utf-8")
            text = strip(raw)
            rel = path.relative_to(ROOT)
            for line in companions_per_class(text):
                faults.append(f"{rel}:{line}  a second companion object in the same class")
            for m in UNDERSCORE_CATCH.finditer(text):
                line = text.count("\n", 0, m.start()) + 1
                faults.append(f"{rel}:{line}  catch (_: ...) does not compile here; name the parameter")
            for m in NOT_INLINE.finditer(text):
                body = lambda_body(text, m.end() - 1)
                if COMPOSABLE_CALL.search(body):
                    line = text.count("\n", 0, m.start()) + 1
                    name = m.group(1) or m.group(3)
                    faults.append(
                        f"{rel}:{line}  a composable call inside {name} {{ }}, which is not inline; "
                        "look the strings up in map { } first"
                    )
    if faults:
        print(f"{len(faults)} Kotlin mistakes the compiler will reject:\n", file=sys.stderr)
        for fault in faults:
            print(f"  {fault}", file=sys.stderr)
        return 1
    print(f"No companion, catch-parameter or non-inline composable mistakes in {files} Kotlin files.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
