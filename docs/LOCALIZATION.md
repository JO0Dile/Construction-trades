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
| `android/app/src/main/res/values-ar/strings.xml` | Android | Arabic |
| `ios/.../<lang>.lproj/Localizable.strings` | iOS | UI strings |
| `ios/.../<lang>.lproj/Localizable.stringsdict` | iOS | Plurals |
| `ios/.../<lang>.lproj/InfoPlist.strings` | iOS | Permission purpose strings |

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

## Terminology

The Hebrew and Arabic in the catalogues is written in the terms used on
Israeli sites rather than dictionary translations — מא״ז, פקסגול, שרשורי;
بريزة, أبلكاش. Before the first public release this should still get a review
pass from a native-speaking tradesperson in each language: the words are the
part of this product a user judges in the first ten seconds.
