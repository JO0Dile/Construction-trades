#!/usr/bin/env python3
"""A write that can be refused is not called and then forgotten.

Repositories in this app say no by returning a `Result` -- a violation with no
evidence, a trade set for somebody the caller has no standing over, a daily log
signed twice -- or a `Boolean` for the older ones. A screen that calls one as a
bare statement throws the answer away, and a refused write then looks exactly
like a button that does nothing. That is how "the button is broken" reports
start, and it is invisible to the compiler, the linter and every test.

So every call from the UI to a repository function returning `Result` or
`Boolean` has to do something with the answer: chain `.onFailure`, `.fold`,
`.getOrThrow` and the rest, hand it to `recordRefusal()`, or assign, return or
test it. Calls are resolved through `AppContainer` to the repository class that
actually answers them, so two repositories with a function of the same name
cannot be mistaken for each other.
"""
from __future__ import annotations

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SRC = ROOT / "android/app/src/main/java/il/co/tradesmanager"
CONTAINER = SRC / "di/AppContainer.kt"
REPOSITORIES = SRC / "data/repository"
UI = SRC / "ui"

HANDLED = re.compile(
    r"\s*\??\.(onFailure|onSuccess|fold|getOrElse|getOrNull|getOrDefault|getOrThrow|"
    r"exceptionOrNull|isSuccess|isFailure|recordRefusal|also|let|takeIf|takeUnless)\b"
)


def repository_classes() -> dict[str, str]:
    """container property name -> repository class name."""
    text = CONTAINER.read_text(encoding="utf-8")
    found = dict(re.findall(r"val (\w+)\s*=\s*(\w+Repository)\(", text))
    found.update(re.findall(r"val (\w+):\s*(\w+Repository)\b", text))
    return found


def answering_functions() -> dict[str, dict[str, str]]:
    """repository class -> {function name -> 'Result' or 'Boolean'}."""
    out: dict[str, dict[str, str]] = {}
    for path in REPOSITORIES.rglob("*.kt"):
        text = path.read_text(encoding="utf-8")
        for cls in re.finditer(r"^class (\w+Repository)\b", text, re.M):
            fns: dict[str, str] = {}
            for m in re.finditer(
                r"\bfun (\w+)\((?:[^()]|\([^()]*\))*\)\s*:\s*(Result<|Boolean\b)", text
            ):
                fns[m.group(1)] = "Result" if m.group(2).startswith("Result") else "Boolean"
            out[cls.group(1)] = fns
    return out


def call_end(text: str, open_paren: int) -> int | None:
    depth = 0
    for i in range(open_paren, len(text)):
        c = text[i]
        if c == "(":
            depth += 1
        elif c == ")":
            depth -= 1
            if depth == 0:
                return i + 1
    return None


def main() -> int:
    props = repository_classes()
    answers = answering_functions()
    faults: list[str] = []
    checked = 0

    for path in sorted(UI.rglob("*.kt")):
        text = path.read_text(encoding="utf-8")
        for m in re.finditer(r"container\.(\w+)\.(\w+)\(", text):
            cls = props.get(m.group(1))
            kind = answers.get(cls, {}).get(m.group(2)) if cls else None
            if kind is None:
                continue
            checked += 1
            end = call_end(text, m.end() - 1)
            if end is None:
                continue
            after = text[end:end + 60]
            line_start = text.rfind("\n", 0, m.start()) + 1
            before = text[line_start:m.start()]
            if HANDLED.match(after):
                continue
            # Used as a value: assigned, returned, compared, passed on, or the
            # tail of an expression (`?.let { container.x.y() }` is its value).
            if re.search(r"(=|return|\?:|&&|\|\||!|\(|,|if\s*\(|->)\s*$", before.rstrip() + " ") and before.strip():
                continue
            if re.match(r"\s*(==|!=|&&|\|\||\)|,|\?:)", after):
                continue
            line = text.count("\n", 0, m.start()) + 1
            faults.append(
                f"{path.relative_to(ROOT)}:{line}  {m.group(1)}.{m.group(2)}() returns "
                f"{kind} and nothing reads it"
            )

    if faults:
        print(f"{len(faults)} writes can be refused and nobody would know:\n", file=sys.stderr)
        for fault in faults:
            print(f"  {fault}", file=sys.stderr)
        return 1
    print(f"All {checked} calls to a repository write that can say no read its answer.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
