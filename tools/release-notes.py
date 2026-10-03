#!/usr/bin/env python3
"""The notes that say what each update does, in the three languages.

shared/assets/releases/notes.json is written by hand, one entry per version,
newest first, every point in English, Hebrew and Arabic. The app ships it and
shows what changed after an update; the release workflow attaches a copy to
the release so a phone being offered an update can say what it is about to
get -- every version from the one it has to the one on offer, not just the
last.

    tools/release-notes.py --check
        CI. Fails when the version in android/app/build.gradle.kts has no
        entry, when any point is missing a language, or when the file is out
        of order. An update nobody wrote notes for is an update the people
        installing it are told nothing about.

    tools/release-notes.py --release v0.25.0 OUT_DIR
        The release workflow. Writes OUT_DIR/release-notes.json (the file,
        attached as an asset) and OUT_DIR/release-body.md (the text GitHub
        shows, with this version's points in all three languages).
"""

from __future__ import annotations

import json
import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
NOTES = ROOT / "shared" / "assets" / "releases" / "notes.json"
GRADLE = ROOT / "android" / "app" / "build.gradle.kts"
LANGUAGES = ("en", "he", "ar")
HEADINGS = {"en": "What's new", "he": "מה חדש", "ar": "ما الجديد"}
INSTALL = {
    "en": "Install `app-universal-debug.apk` below — it runs on any phone. A phone that already has the app is offered this build by **Settings → Check for updates**, with everything that changed since the version it has.",
    "he": "התקינו את `app-universal-debug.apk` — הוא מתאים לכל טלפון. במכשיר שהאפליקציה כבר מותקנת בו, **הגדרות ← בדיקת עדכונים** תציע את הגרסה הזו, עם כל מה שהשתנה מאז הגרסה שמותקנת בו.",
    "ar": "ثبّت `app-universal-debug.apk` — يعمل على أي هاتف. إن كان التطبيق مثبّتًا مسبقًا، **الإعدادات ← التحقق من التحديثات** ستعرض هذه النسخة، مع كل ما تغيّر منذ النسخة المثبّتة.",
}


def version_key(text: str) -> tuple[int, ...]:
    core = text.strip().lstrip("vV").split("-")[0]
    return tuple(int(re.sub(r"\D", "", part) or 0) for part in core.split("."))


def app_version() -> str:
    match = re.search(r'versionName\s*=\s*"([^"]+)"', GRADLE.read_text(encoding="utf-8"))
    if not match:
        sys.exit("No versionName in build.gradle.kts.")
    return match.group(1)


def load() -> list[dict]:
    return json.loads(NOTES.read_text(encoding="utf-8"))["versions"]


def faults(entries: list[dict], version: str) -> list[str]:
    found = []
    seen = set()
    keys = []
    for entry in entries:
        name = entry.get("version", "")
        if not name:
            found.append("an entry has no version")
            continue
        if name in seen:
            found.append(f"{name}: listed twice")
        seen.add(name)
        keys.append(version_key(name))
        points = entry.get("points", [])
        if not points:
            found.append(f"{name}: no points — say what the update does")
        for index, point in enumerate(points, 1):
            for language in LANGUAGES:
                if not str(point.get(language, "")).strip():
                    found.append(f"{name} point {index}: no {language}")
            extra = set(point) - set(LANGUAGES)
            if extra:
                found.append(f"{name} point {index}: unknown language {sorted(extra)}")
    if keys != sorted(keys, reverse=True):
        found.append("versions are not newest first")
    if version not in seen:
        found.append(
            f"{version} (build.gradle.kts) has no entry. Write what this update does, "
            "in English, Hebrew and Arabic, at the top of shared/assets/releases/notes.json."
        )
    return found


def body(entry: dict) -> str:
    lines = []
    for language in LANGUAGES:
        lines.append(f"## {HEADINGS[language]} — {entry['version']}")
        lines.append("")
        lines += [f"- {point[language].strip()}" for point in entry["points"]]
        lines.append("")
    for language in LANGUAGES:
        lines.append(INSTALL[language])
        lines.append("")
    return "\n".join(lines)


def main(argv: list[str]) -> int:
    entries = load()
    if argv[:1] == ["--check"]:
        problems = faults(entries, app_version())
        if problems:
            print("Release notes are incomplete:", file=sys.stderr)
            for problem in problems:
                print(f"  {problem}", file=sys.stderr)
            return 1
        print(f"{len(entries)} versions have notes in all three languages, {app_version()} among them.")
        return 0
    if argv[:1] == ["--release"] and len(argv) == 3:
        tag, out = argv[1], pathlib.Path(argv[2])
        name = tag.lstrip("vV")
        problems = faults(entries, name)
        if problems:
            print("\n".join(problems), file=sys.stderr)
            return 1
        entry = next(e for e in entries if e["version"] == name)
        out.mkdir(parents=True, exist_ok=True)
        # Only versions up to the one being released: a release cut from an
        # older commit must not describe a future it does not contain.
        shipped = [e for e in entries if version_key(e["version"]) <= version_key(name)]
        (out / "release-notes.json").write_text(
            json.dumps({"versions": shipped}, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
        )
        (out / "release-body.md").write_text(body(entry), encoding="utf-8")
        print(f"Wrote notes for {name} ({len(shipped)} versions in the attached file).")
        return 0
    print(__doc__, file=sys.stderr)
    return 2


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
