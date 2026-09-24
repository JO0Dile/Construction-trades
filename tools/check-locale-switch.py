#!/usr/bin/env python3
"""Choosing a language in the app has to change the whole app.

It did not, for twenty-four versions. The activity listed `locale` and
`layoutDirection` in android:configChanges, which tells Android "I will handle
a language change myself" -- so picking Hebrew never rebuilt the screen. The
catalogue text, which the app looks up itself, switched; every word Android
looks up stayed in English. Ninety per cent of a Hebrew phone was English,
reported four times, and nothing on this machine could see it, because the
translations were all present and correct in the APK.

Checked here, because each of these is one line of XML nobody rereads:

  * no activity handles locale or layoutDirection changes itself
  * AppCompat's autoStoreLocales service is declared, or on Android 12 and
    below the choice is forgotten when the process dies
  * every activity is an AppCompatActivity, which is what applies the choice
  * every language in locales_config.xml survives resourceConfigurations,
    with "iw" beside "he" for the libraries that still use the old code
  * the app's own Hebrew is filed under "iw" as well as "he". Android turns a
    phone's "he" into "iw" before looking anything up and does not treat the
    two as one language, so values-he alone is never chosen. Fixing the
    first three still left every word of the app English on a Hebrew phone;
    a test running Android's own resource lookup is what found it.
"""

from __future__ import annotations

import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
APP = ROOT / "android" / "app"
MANIFEST = APP / "src" / "main" / "AndroidManifest.xml"
RES = APP / "src" / "main" / "res"
LOCALES = RES / "xml" / "locales_config.xml"
GRADLE = APP / "build.gradle.kts"
SOURCES = APP / "src" / "main" / "java"
ANDROID = "{http://schemas.android.com/apk/res/android}"

# The code Android's resource lookup asks for, for the languages Java renamed.
LEGACY_CODES = {"he": "iw", "id": "in", "yi": "ji"}


def main() -> int:
    import xml.etree.ElementTree as ET

    problems: list[str] = []
    manifest = ET.parse(MANIFEST).getroot()
    application = manifest.find("application")

    activities = application.findall("activity")
    for activity in activities:
        name = activity.get(ANDROID + "name", "?")
        handled = set((activity.get(ANDROID + "configChanges") or "").split("|"))
        for change in ("locale", "layoutDirection"):
            if change in handled:
                problems.append(
                    f"{name} lists '{change}' in android:configChanges, so a language "
                    "change never rebuilds the screen and the app stays in English."
                )

    stored = False
    for service in application.findall("service"):
        if service.get(ANDROID + "name") == "androidx.appcompat.app.AppLocalesMetadataHolderService":
            for meta in service.findall("meta-data"):
                if meta.get(ANDROID + "name") == "autoStoreLocales" and meta.get(ANDROID + "value") == "true":
                    stored = True
    if not stored:
        problems.append(
            "AppLocalesMetadataHolderService with autoStoreLocales=true is missing: on Android 12 "
            "and below the chosen language is forgotten when the app restarts."
        )

    for activity in activities:
        simple = activity.get(ANDROID + "name", "").split(".")[-1]
        found = list(SOURCES.rglob(f"{simple}.kt"))
        if not found:
            problems.append(f"{simple}: declared in the manifest, no source file found")
            continue
        text = found[0].read_text(encoding="utf-8")
        if not re.search(rf"class\s+{simple}\s*(\([^)]*\))?\s*:\s*AppCompatActivity\b", text):
            problems.append(f"{simple} is not an AppCompatActivity, which is what applies the chosen language")

    shipped = [
        element.get(ANDROID + "name")
        for element in ET.parse(LOCALES).getroot().findall("locale")
    ]
    match = re.search(r"resourceConfigurations\s*\+=\s*listOf\(([^)]*)\)", GRADLE.read_text(encoding="utf-8"))
    kept = set(re.findall(r'"([^"]+)"', match.group(1))) if match else set()
    for tag in shipped:
        if tag not in kept:
            problems.append(f"'{tag}' is offered in locales_config.xml and stripped by resourceConfigurations")
    if "he" in shipped and "iw" not in kept:
        problems.append(
            "'iw' is missing from resourceConfigurations: the AndroidX libraries file their Hebrew "
            "under it, and without it their strings stay English on a Hebrew phone."
        )

    for tag in shipped:
        language = tag.split("-")[0]
        if language not in LEGACY_CODES:
            continue
        legacy = LEGACY_CODES[language]
        modern_dir = RES / f"values-{language}"
        legacy_dir = RES / f"values-{legacy}"
        for modern in sorted(modern_dir.glob("*.xml")):
            twin = legacy_dir / modern.name
            if not twin.is_file():
                problems.append(
                    f"values-{legacy}/{modern.name} is missing: Android looks up '{language}' as "
                    f"'{legacy}', so the words in values-{language}/{modern.name} are never shown."
                )
            elif twin.read_bytes() != modern.read_bytes():
                problems.append(
                    f"values-{legacy}/{modern.name} differs from values-{language}/{modern.name}: "
                    "run tools/gen-strings.py."
                )

    if problems:
        print("Switching language would not switch the app:", file=sys.stderr)
        for problem in problems:
            print(f"  {problem}", file=sys.stderr)
        return 1
    print(f"A language change rebuilds all {len(activities)} activities and is remembered: {', '.join(shipped)}.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
