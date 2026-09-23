#!/usr/bin/env python3
"""Nothing is built into this app that nobody can reach.

The dominant fault in this codebase is not a crash. It is a feature that
compiles, lints, passes every test and cannot be got at: a register with no
row to tap, a button whose handler was never wired, a screen with no route to
it. Each one looked finished in review and did nothing on the phone, and the
only way any of them was ever found was somebody using the app.

Three kinds are checkable from the source, and all three have shipped here:

  * a **route** declared in `Routes` that nothing navigates to -- either the
    constant itself is never passed to `navigate`, or the helper that builds
    its path (`Routes.snagDetail(id)`) is never called;
  * a **screen** composable nothing calls;
  * a **view model action** no screen invokes. `ViolationsViewModel.openDraft`
    sat like this for weeks, which is why a half-written violation could never
    be reopened, finished or cancelled by anybody once the officer left the
    page.

What this cannot see is a handler wired to a button that is never drawn, or a
row whose `clickable` was dropped. Those need a person. This catches the ones
a machine can.
"""
from __future__ import annotations

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SRC = ROOT / "android/app/src/main/java/il/co/tradesmanager"
NAV = SRC / "ui/nav/AppNavHost.kt"

# Actions a view model exposes that nothing is expected to call: lifecycle
# overrides, and anything a screen reaches through a property instead.
NOT_ACTIONS = {"onCleared"}


def main() -> int:
    if not NAV.exists():
        print(f"{NAV.relative_to(ROOT)} is missing", file=sys.stderr)
        return 1

    sources = {p: p.read_text(encoding="utf-8") for p in SRC.rglob("*.kt")}
    everything = "\n".join(sources.values())
    nav = NAV.read_text(encoding="utf-8")

    faults: list[str] = []

    # A helper reads `fun snagDetail(snagId: String) = "$SNAG_DETAIL/$snagId"`,
    # so the constant it builds on is what ties the two together.
    helpers = dict(re.findall(r'fun (\w+)\([^)]*\) = "\$([A-Z_]+)', nav))
    for name, path in re.findall(r'const val ([A-Z_]+) = "([^"]+)"', nav):
        helper = next((h for h, route in helpers.items() if route == name), None)
        if helper:
            if not re.search(r"Routes\.%s\(" % helper, everything):
                faults.append(
                    f'Routes.{helper}() builds the path to "{path}" and nothing calls it'
                )
        elif len(re.findall(r"Routes\.%s\b" % name, everything)) <= 1:
            faults.append(f'Routes.{name} ("{path}") is declared and never navigated to')

    # A screen composable nothing calls. One occurrence is its own declaration.
    for path, text in sorted(sources.items()):
        for match in re.finditer(r"^fun ([A-Z]\w*Screen)\(", text, re.M):
            name = match.group(1)
            if len(re.findall(r"\b%s\(" % name, everything)) <= 1:
                faults.append(f"{name} in {path.relative_to(ROOT)} is never called")

    # A view model action no screen invokes, by reference or by call.
    for path, text in sorted(sources.items()):
        if not path.name.endswith("ViewModel.kt"):
            continue
        others = "\n".join(t for p, t in sources.items() if p != path)
        for match in re.finditer(r"^    fun (\w+)\(", text, re.M):
            action = match.group(1)
            if action in NOT_ACTIONS:
                continue
            # Called as viewModel.act(), passed as viewModel::act, or used by
            # another function inside the same view model.
            referenced = re.search(r"[.:]{1,2}%s\b" % action, others) or re.search(
                r"\b%s\s*\(" % action, text.replace(match.group(0), "", 1)
            )
            if not referenced:
                faults.append(f"{path.stem}.{action}() is never called from any screen")

    if faults:
        print(f"{len(faults)} things are built and cannot be reached:\n", file=sys.stderr)
        for fault in faults:
            print(f"  {fault}", file=sys.stderr)
        return 1

    routes = len(re.findall(r'const val [A-Z_]+ = "', nav))
    screens = len(re.findall(r"^fun [A-Z]\w*Screen\(", everything, re.M))
    print(f"Every one of {routes} routes and {screens} screens can be reached.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
