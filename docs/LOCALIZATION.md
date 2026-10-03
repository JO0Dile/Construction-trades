# Adding a language

The product promises Hebrew, Arabic and English now, and at least four more
later "without code changes". This is how that promise is kept, and what a
translator actually has to do.

## The one source

Every user-facing string in both apps comes from:

```
shared/i18n/strings.json
```

Nothing else. `tools/gen-strings.py` turns it into:

| Generated file | Platform | Contains |
|---|---|---|
| `android/app/src/main/res/values/strings.xml` | Android | English (the source language) |
| `android/app/src/main/res/values-he/strings.xml` | Android | Hebrew |
| `android/app/src/main/res/values-iw/strings.xml` | Android | Hebrew again, under the code Android looks it up by |
| `android/app/src/main/res/values-ar/strings.xml` | Android | Arabic |
| `ios/.../<lang>.lproj/Localizable.strings` | iOS | UI strings |
| `ios/.../<lang>.lproj/Localizable.stringsdict` | iOS | Plurals |
| `ios/.../<lang>.lproj/InfoPlist.strings` | iOS | Permission purpose strings |

Hebrew is written twice on Android on purpose. Android turns a phone's
`he` into the old code `iw` before it looks a word up, and does not treat
the two as one language, so a `values-he` folder on its own is never
chosen. The same goes for Indonesian (`id`/`in`) and Yiddish (`yi`/`ji`)
if they are ever added; the generator writes both folders for all three,
and `tools/check-locale-switch.py` fails the build if they differ.

Never edit a generated file. The generator rewrites it, and a hand edit is
lost silently — which is why every generated file carries a banner saying so.

## What a translator does

1. Open `shared/i18n/strings.json`.
2. Add the language code to `"languages"`.
3. Add one key per entry under `"strings"`. For example, for Russian:

   ```json
   "action_save": { "en": "Save", "he": "שמירה", "ar": "حفظ", "ru": "Сохранить" }
   ```

4. Under `"plurals"`, add the language with the plural categories its grammar
   uses. Russian needs `one`, `few`, `many`, `other`; Hebrew uses `one`, `two`,
   `many`, `other`; Arabic uses `zero`, `one`, `two`, `few`, `many`, `other`.
   English needs only `one` and `other`. The generator writes whatever
   categories are present, so nothing here is hard-coded to three languages.

5. Run the generator:

   ```bash
   python3 tools/gen-strings.py
   ```

   It refuses to write anything if a string is missing a translation, and names
   every gap. `--check` reports whether the generated files are current without
   writing, which is what CI should run.

## What a developer does — once, per language

Two registrations, both one line, both data:

- `android/app/src/main/res/xml/locales_config.xml` — add `<locale android:name="ru" />`
- `android/app/build.gradle.kts` — add the code to `resourceConfigurations`
- `ios/TradesManager/Support/Info.plist` — add the code to `CFBundleLocalizations`

That is the whole change. In particular:

- The **language picker** is built from `locales_config.xml` at runtime by
  `AppLanguages.supported()`, and shows each language's name *in itself*
  ("Русский", not "Russian"), so it grows on its own.
- **Layout direction** comes from `TextUtils.getLayoutDirectionFromLocale`, so
  adding Persian or Urdu mirrors the UI with no list of RTL languages to
  maintain anywhere in the code.
- **Dates, numbers and currency** come from `java.text` / `Foundation` for the
  active locale (`core/i18n/Formats.kt`, `ios/.../Formats.swift`).
- **Switching language in-app** is `AppCompatDelegate.setApplicationLocales`,
  which hands the choice to the system picker on Android 13+ and persists it
  itself below that. The activity is recreated, so every string, date and
  layout changes at once.

## Catalogue content is localised too — and separately

Tool names, specifications, safety checks and project templates are **data**,
not resources: they live in `shared/assets/catalog` as JSON maps keyed by
language, and both apps read the same files.

```json
"names": { "en": "RCD 40 A / 30 mA", "he": "מפסק פחת 40 אמפר / 30 מ״א", "ar": "قاطع تسرّب أرضي 40 أمبير / 30 مللي أمبير" }
```

Adding a language to the catalogues means adding that key to each entry. Until
it is added, `resolve()` falls back — exact language, then the base of a
regional tag, then English, then any translation present — so a partly
translated catalogue shows a usable row rather than a blank one. That fallback
chain is covered by `LocalizedTextTest`.

`CatalogIntegrityTest` fails the build if any item is missing a name or a
specification in a shipped language, so a half-translated catalogue cannot be
released by accident. When you add a fourth language to `languages`, add it to
that test's `languages` list too — that is the deliberate moment where you
decide the catalogue must be complete in it.

## The third place: what the audit trail says

There is a third body of text, and it is the one that was English for longest
because neither of the checks above could see it.

An audit summary is written by one person and read by another. A labourer
signs an induction on an Arabic phone, the site manager reads the trail in
Hebrew, and the export goes to an inspector in English. A sentence built at
the moment of the write can only be in one of those, so the register stores a
**key and its arguments** and the words are chosen when somebody looks:

```
permit_issued|PTW-14|Yossi
```

The words live in `tools/data/audit-summaries.json`, in the same three
languages as everything else:

```json
"permit_issued": {
  "arguments": ["reference", "name"],
  "en": "%1$s issued to %2$s",
  "he": "%1$s הונפק ל%2$s",
  "ar": "%1$s صدر لـ%2$s"
}
```

The arguments are data — a reference, a name, a number — and are never
translated. An argument marked with `Summary.nest` is itself a key, which is
how a role or a status arrives in the reader's language instead of as
`SITE_MANAGER` in the middle of a Hebrew sentence.

**Adding a phrase** means adding it to that file, running
`tools/gen-audit-strings.py`, and then adding a constant in
`core/audit/Summaries.kt` and a branch in `ui/audit/SummaryText.kt`.
`tools/gen-audit-strings.py --check` fails until all four exist, because a
phrase with no translation reaches a Hebrew screen as the key itself —
`tw_released`, in the middle of an audit trail.

**Never translate a phrase by editing the generated string catalogue.** The
phrase table is the source; the generator overwrites `summary_*` from it.

Rows written before any of this exist as English prose and are left exactly as
they are. An audit trail whose history gets tidied up is not an audit trail,
so anything that does not parse as a key is printed as it was stored.

## What the build refuses to let you forget

| Check | What it catches |
|---|---|
| `gen-strings.py --check` | A string added in one language and not the other two |
| `check-catalog-languages.py` | A catalogue block with no Hebrew or Arabic, or with the English pasted into one of them |
| `check-audit-summaries.py` | An English sentence written into the audit register |
| `gen-audit-strings.py --check` | A phrase missing a translation, a constant or a branch; an audit action with no word for it |
| `check-invisibles.py` | A direction mark hiding in a source file |

None of these can fail at compile time. The wrong version builds, runs, and
writes a perfectly good English sentence onto somebody's Hebrew screen.

## Terminology

The Hebrew and Arabic in the catalogues is written in the terms used on
Israeli sites rather than dictionary translations — מא״ז, פקסגול, שרשורי;
بريزة, أبلكاش. Before the first public release this should still get a review
pass from a native-speaking tradesperson in each language: the words are the
part of this product a user judges in the first ten seconds.
