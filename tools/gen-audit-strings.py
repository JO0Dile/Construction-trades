#!/usr/bin/env python3
"""Keeps the audit phrases, the string catalogue and the Kotlin keys in step.

An audit summary is stored as a key and its arguments (core/audit/Summary.kt)
and turned into words when somebody reads it. Three things have to agree for
that to work, and nothing in the compiler checks any of them:

  tools/data/audit-summaries.json   the phrase, in three languages
  shared/i18n/strings.json          audit_<key>, which the screens look up
  core/audit/Summaries.kt           the constant the repositories write

A phrase missing from the catalogue renders as the raw key on somebody's
screen — "tw_released" in the middle of a Hebrew audit trail. A placeholder
that is in the English and not in the Arabic silently drops the reference the
line is about. Both are exactly as invisible as the fault this whole change
exists to fix, so both fail here.

    tools/gen-audit-strings.py            write the catalogue entries
    tools/gen-audit-strings.py --check    verify, for CI
"""

from __future__ import annotations

import argparse
import json
import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
TABLE = ROOT / "tools" / "data" / "audit-summaries.json"
CATALOGUE = ROOT / "shared" / "i18n" / "strings.json"
KEYS = ROOT / "android/app/src/main/java/il/co/tradesmanager/core/audit/Summaries.kt"
RESOLVER = ROOT / "android/app/src/main/java/il/co/tradesmanager/ui/audit/SummaryText.kt"
ACTIONS = ROOT / "android/app/src/main/java/il/co/tradesmanager/data/repository/AuditTrail.kt"
ACTION_LABELS = ROOT / "android/app/src/main/java/il/co/tradesmanager/ui/audit/AuditActionLabel.kt"

PREFIX = "summary_"
LANGUAGES = ("en", "he", "ar")
KEY_SHAPE = re.compile(r"[a-z][a-z0-9_]*\Z")


def placeholders(text: str) -> set[str]:
    return set(re.findall(r"%\d+\$s", text))


def wanted(phrases: dict) -> dict:
    return {
        PREFIX + key: {lang: phrase[lang] for lang in LANGUAGES}
        for key, phrase in phrases.items()
    }


def faults(phrases: dict, catalogue: dict) -> list[str]:
    strings = catalogue["strings"]
    found = []

    for key, phrase in sorted(phrases.items()):
        if not KEY_SHAPE.match(key):
            found.append(f"{key}: not a usable key — lower case, digits and underscores only")
        expected = {"%%%d$s" % (i + 1) for i in range(len(phrase["arguments"]))}
        for lang in LANGUAGES:
            text = phrase.get(lang, "")
            if not text.strip():
                found.append(f"{key}: no {lang}")
                continue
            got = placeholders(text)
            if got != expected:
                found.append(
                    f"{key} [{lang}]: takes {sorted(expected)} but the text uses {sorted(got)}"
                )

        name = PREFIX + key
        if name not in strings:
            found.append(f"{key}: {name} is not in the string catalogue")
        else:
            for lang in LANGUAGES:
                if strings[name].get(lang) != phrase[lang]:
                    found.append(f"{key}: {name} [{lang}] has drifted from the phrase table")

    # A catalogue entry with no phrase behind it is a translation nobody can
    # reach, which is how the last set of these came to sit unused.
    for name in strings:
        if name.startswith(PREFIX) and name[len(PREFIX):] not in phrases:
            found.append(f"{name} is in the catalogue with no phrase behind it")

    # Every phrase needs a constant to write it and a branch to read it, or it
    # is a translated sentence nothing can ever produce or show.
    #
    # The constant is declared with the key as a literal; the resolver refers
    # to it by name, because a resolver full of bare strings is how a key and
    # its branch drift apart in the first place.
    for path, what, shape in (
        (KEYS, "no constant in Summaries.kt", lambda key: f'"{key}"'),
        (RESOLVER, "nothing in SummaryText.kt renders it", lambda key: f"Summaries.{key.upper()}"),
    ):
        if not path.exists():
            found.append(f"{path.relative_to(ROOT)} is missing")
            continue
        text = path.read_text(encoding="utf-8")
        for key in sorted(phrases):
            if shape(key) not in text:
                found.append(f"{key}: {what}")
    found += action_faults()
    found += borrowed_faults(catalogue)
    return found


def borrowed_faults(catalogue: dict) -> list[str]:
    """The statuses a summary borrows from the screens still exist.

    A summary can carry a status — "PO-12: part delivered" — and rather than
    translating those words a second time the renderer points at the string
    the plant register and the orders screen already show. That keeps the
    register and the screen it came from saying the same thing, and it means a
    rename in the catalogue silently turns the audit trail back into
    "part_received" unless something checks. This is that something.
    """
    found = []
    text = RESOLVER.read_text(encoding="utf-8")
    borrowed = re.findall(r'"([a-z][a-z0-9_]*)" -> R\.string\.([a-z][a-z0-9_]*)', text)
    if not borrowed:
        return ["SummaryText.kt: no borrowed status labels found — has the shape changed?"]
    for key, name in borrowed:
        if key != name:
            found.append(f"borrowed status {key}: points at R.string.{name}; keep the two the same")
        if name not in catalogue["strings"]:
            found.append(f"borrowed status {key}: R.string.{name} is not in the catalogue")
    return found


def action_faults() -> list[str]:
    """Every audit action has a word for it.

    The label falls back to the constant, so an action nobody translated
    prints as STOCK_CHANGE. That is not hypothetical — it is what a
    Hebrew-speaking site manager was shown, for two releases, while the
    translations sat in the catalogue unused. The fallback is right (a row
    from a newer version should still say something) and it is exactly why
    nothing fails when a new action is added.
    """
    found = []
    actions = re.findall(r'const val (\w+) = "\1"', ACTIONS.read_text(encoding="utf-8"))
    if not actions:
        return ["AuditTrail.kt: no action constants found — has the shape changed?"]
    labels = ACTION_LABELS.read_text(encoding="utf-8")
    for action in actions:
        if f"Action.{action}" not in labels:
            found.append(f"audit action {action}: no word for it in AuditActionLabel.kt")
    return found


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args()

    phrases = json.loads(TABLE.read_text(encoding="utf-8"))["phrases"]
    catalogue = json.loads(CATALOGUE.read_text(encoding="utf-8"))

    if not args.check:
        catalogue["strings"].update(wanted(phrases))
        CATALOGUE.write_text(
            json.dumps(catalogue, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
        )
        print(f"Wrote {len(phrases)} audit phrases into the string catalogue.")
        print("Now run tools/gen-strings.py to regenerate the platform resources.")
        return 0

    found = faults(phrases, catalogue)
    if found:
        print(f"{len(found)} problems with the audit phrases:\n")
        for fault in found:
            print(" ", fault)
        return 1
    print(
        f"All {len(phrases)} audit phrases are translated and wired, "
        "every audit action has a word for it, and every borrowed status resolves."
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())
