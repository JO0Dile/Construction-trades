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
    page;
  * a **string** translated into three languages that no screen on either
    platform shows. Forty-four had built up, including the one sentence that
    should have told somebody a photograph failed to save. Each is a phrase
    a translator is paid for, and more often than not the sign of a message
    somebody meant to show and never wired.

What this cannot see is a handler wired to a button that is never drawn, or a
row whose `clickable` was dropped. Those need a person. This catches the ones
a machine can.
"""
from __future__ import annotations

import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
CATALOGUE = ROOT / "shared/i18n/strings.json"
AUDIT_PHRASES = ROOT / "tools/data/audit-summaries.json"
IOS = ROOT / "ios"
RES = ROOT / "android/app/src/main/res"
MANIFEST = ROOT / "android/app/src/main/AndroidManifest.xml"
GEN_STRINGS = ROOT / "tools/gen-strings.py"
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

    faults += unshown_strings(everything)

    if faults:
        print(f"{len(faults)} things are built and cannot be reached:\n", file=sys.stderr)
        for fault in faults:
            print(f"  {fault}", file=sys.stderr)
        return 1

    routes = len(re.findall(r'const val [A-Z_]+ = "', nav))
    screens = len(re.findall(r"^fun [A-Z]\w*Screen\(", everything, re.M))
    strings = len(json.loads(CATALOGUE.read_text(encoding="utf-8"))["strings"])
    print(f"Every one of {routes} routes, {screens} screens and {strings} strings can be reached.")
    return 0


def unshown_strings(kotlin: str) -> list[str]:
    """Catalogue strings nothing on either platform refers to.

    Android refers to one as `R.string.key` or `R.plurals.key`, or as
    `@string/key` from a resource or the manifest. iOS looks every string up by
    its key as a literal. Audit phrases are generated into the catalogue from
    their own table and checked by gen-audit-strings, so they are left to it.
    """
    catalogue = json.loads(CATALOGUE.read_text(encoding="utf-8"))
    audit = json.loads(AUDIT_PHRASES.read_text(encoding="utf-8"))["phrases"]
    generated = {f"summary_{key}" for key in audit}

    android = set(re.findall(r"R\.(?:string|plurals)\.([a-z0-9_]+)", kotlin))
    xml = "\n".join(
        p.read_text(encoding="utf-8")
        for p in [MANIFEST, *RES.rglob("*.xml")]
        if p.exists() and p.parent.name != "values" and not p.parent.name.startswith("values-")
    )
    android |= set(re.findall(r"@string/([a-z0-9_]+)", xml))
    swift = "\n".join(p.read_text(encoding="utf-8") for p in IOS.rglob("*.swift"))
    ios = set(re.findall(r'"([a-z][a-z0-9_]+)"', swift))
    # The permission sentences iOS shows come from Info.plist, which the
    # generator fills from the catalogue by key: shown, but never by name.
    plist = re.search(r"IOS_INFO_PLIST_KEYS = \{(.*?)\}", GEN_STRINGS.read_text(encoding="utf-8"), re.S)
    if plist:
        ios |= set(re.findall(r':\s*"([a-z][a-z0-9_]+)"', plist.group(1)))

    faults = []
    for section in ("strings", "plurals"):
        for key in catalogue.get(section, {}):
            if key in generated or key in android or key in ios:
                continue
            faults.append(f"{section[:-1]} {key} is translated and shown on no screen")
    return faults


if __name__ == "__main__":
    sys.exit(main())
