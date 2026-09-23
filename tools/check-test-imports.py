#!/usr/bin/env python3
"""Every assertion a test calls is one it imported.

`assertTrue(...)` with no `import org.junit.Assert.assertTrue` does not
compile, and the line looks exactly like the twenty around it that do. The
compiler catches it — but Gradle does not run in the container this repository
is usually worked on, so catching it there costs a full round trip through CI
to be told about a missing import.

This is that round trip, in about a second. It is the same fault
check-compose-delegates.py exists for, in the other source set.

Only JUnit's own assertions are looked at, by the name they are called with.
A helper a test defines itself, or one it reaches through a qualified name, is
somebody else's import.
"""

from __future__ import annotations

import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
TESTS = ROOT / "android" / "app" / "src" / "test"

# The assertions JUnit 4 offers on org.junit.Assert.
JUNIT = {
    "assertEquals", "assertNotEquals", "assertTrue", "assertFalse", "assertNull",
    "assertNotNull", "assertSame", "assertNotSame", "assertArrayEquals", "fail",
    "assertThat",
}
CALL = re.compile(r"\b(" + "|".join(sorted(JUNIT)) + r")\s*\(")


def strip(text: str) -> str:
    """Blank out comments and strings, so a mention in prose does not count."""
    out, i, n = [], 0, len(text)
    while i < n:
        if text.startswith("//", i):
            j = text.find("\n", i)
            i = n if j < 0 else j
        elif text.startswith("/*", i):
            j = text.find("*/", i + 2)
            j = n if j < 0 else j + 2
            out.append("\n" * text.count("\n", i, j))
            i = j
        elif text.startswith('"""', i):
            j = text.find('"""', i + 3)
            j = n if j < 0 else j + 3
            out.append("\n" * text.count("\n", i, j))
            i = j
        elif text[i] == '"':
            i += 1
            while i < n and text[i] != '"':
                i += 2 if text[i] == "\\" else 1
            i += 1
        elif text[i] == "`":
            j = text.find("`", i + 1)
            i = n if j < 0 else j + 1
        else:
            out.append(text[i])
            i += 1
    return "".join(out)


def faults(path: pathlib.Path) -> list[str]:
    raw = path.read_text(encoding="utf-8")
    imported = set(re.findall(r"import\s+org\.junit\.Assert\.(\w+)", raw))
    if "import org.junit.Assert\n" in raw or "import org.junit.Assert.*" in raw:
        return []
    code = strip(raw)
    found = []
    for number, line in enumerate(code.splitlines(), start=1):
        for match in CALL.finditer(line):
            name = match.group(1)
            # Reached through a qualified name, so it needs no import.
            start = match.start()
            if start > 0 and line[start - 1] == ".":
                continue
            if name not in imported:
                found.append(
                    f"{path.relative_to(ROOT)}:{number}  {name} is called but not imported"
                )
    return sorted(set(found))


def main() -> int:
    files = sorted(TESTS.rglob("*.kt"))
    problems = [fault for path in files for fault in faults(path)]
    if problems:
        print(f"{len(problems)} assertions are called without their import:\n")
        for problem in problems:
            print(" ", problem)
        print("\nAdd `import org.junit.Assert.<name>` — it will not compile without it.")
        return 1
    print(f"Every JUnit assertion called in {len(files)} test files is imported.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
