#!/usr/bin/env python3
"""Every Compose state delegate has the import that makes `by` work.

    val items by viewModel.items.collectAsStateWithLifecycle()

reads a State<T> through a property delegate, and that only compiles if
`androidx.compose.runtime.getValue` is imported. A `var` written back to needs
`setValue` as well. Both are invisible in review — the delegate line looks
exactly like every other one in the file — and the compiler's message names a
type rather than a missing import:

    Type 'androidx.compose.runtime.State<kotlin.Int>' has no method
    'getValue(Nothing?, KProperty0<*>)', so it cannot serve as a delegate.

Gradle cannot be run in the container this repository is usually worked on, so
a missing import costs a full CI round trip to discover. This is that round
trip in about a second.

Only Compose's own delegates are looked at. `by lazy`, `by viewModels()` and
class delegation are somebody else's `getValue` and are left alone.
"""

from __future__ import annotations

import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
SOURCE = ROOT / "android" / "app" / "src"

GET_VALUE = "import androidx.compose.runtime.getValue"
SET_VALUE = "import androidx.compose.runtime.setValue"

# The right-hand sides that produce a Compose State. Anchored on the `by` so a
# mention in a comment or a string does not count.
DELEGATE = re.compile(
    r"^\s*(?P<kind>val|var)\s+\w+\s+by\s+(?P<rhs>.*)$",
)
COMPOSE_RHS = re.compile(
    r"\b("
    r"remember|rememberSaveable|rememberCoroutineScope|mutableStateOf|"
    r"mutableIntStateOf|mutableLongStateOf|mutableFloatStateOf|mutableDoubleStateOf|"
    r"mutableStateListOf|mutableStateMapOf|"
    r"collectAsState|collectAsStateWithLifecycle|"
    r"animateFloatAsState|animateDpAsState|animateColorAsState|"
    r"observeAsState|derivedStateOf|produceState"
    r")\b",
)


def strip_comments(text: str) -> str:
    """Blank out comments and strings so only code is matched."""
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
        else:
            out.append(text[i])
            i += 1
    return "".join(out)


def faults(path: pathlib.Path) -> list[str]:
    raw = path.read_text(encoding="utf-8")
    if "androidx.compose" not in raw:
        return []
    code = strip_comments(raw)
    has_get, has_set = GET_VALUE in raw, SET_VALUE in raw

    found = []
    for number, line in enumerate(code.splitlines(), start=1):
        match = DELEGATE.match(line)
        if not match or not COMPOSE_RHS.search(match.group("rhs")):
            continue
        where = f"{path.relative_to(ROOT)}:{number}"
        if not has_get:
            found.append(f"{where}  needs `{GET_VALUE}`")
        if match.group("kind") == "var" and not has_set:
            found.append(f"{where}  needs `{SET_VALUE}`")
    return found


def main() -> int:
    files = sorted(SOURCE.rglob("*.kt"))
    problems = [fault for path in files for fault in faults(path)]
    if problems:
        print(f"{len(problems)} Compose delegates would not compile:\n")
        for problem in problems:
            print(" ", problem)
        print("\n`by` on a Compose State needs the getValue import; a `var` needs setValue too.")
        return 1
    print(f"Every Compose state delegate in {len(files)} files has its import.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
