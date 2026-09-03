# Trades Work Manager · מנהל עבודות · مدير أعمال المهن

A field management app for construction and trades work in Israel —
electricians, plumbers, HVAC and refrigeration technicians, painters,
carpenters and general builders. Tools and materials, day planning, projects,
safety sign-off and the audit trail behind all of it, in Hebrew, Arabic and
English, working with no signal.

**Native apps.** Android is Kotlin and Jetpack Compose; iOS is Swift. There is
no WebView, no PWA, no hybrid shell, and no browser dependency for anything.

---

## What makes it usable on day one

The app ships with the catalogues already written:

| | |
|---|---|
| Trades | 6 — electrical, plumbing, HVAC & refrigeration, painting, carpentry, general construction |
| Tools and materials | 178, each with a name **and** a specification in Hebrew, Arabic and English |
| Safety checklists | 19, holding 84 checks, each citing the Israeli regulation or standard it comes from |
| Project templates | 12, whose material lines all resolve to real catalogue items |

Pick your trades during onboarding and the lists are there. Add your own items
at any time; a catalogue refresh never touches them, and never creates a second
copy of something you have been counting.

---

## Layout

```
tradesmanager/
  shared/
    assets/catalog/     the catalogues — bundled unchanged by BOTH apps
    i18n/strings.json   the only place UI text is written
  android/              Gradle + Kotlin + Compose  (namespace il.co.tradesmanager)
  ios/                  Swift shared layer, Info.plist, privacy manifest
  tools/gen-strings.py  strings.json -> strings.xml + .strings/.stringsdict
  docs/
    STORE_COMPLIANCE.md Google Play and App Store rules, mapped to this code
    LOCALIZATION.md     how to add a language without touching code
    CATALOG_FORMAT.md   the catalogue file format and its invariants
```

`shared/` is not a convention, it is the mechanism. Gradle merges
`shared/assets` straight into the APK; Xcode adds the same directory as a
folder reference. A corrected cable specification cannot land on one platform
and be forgotten on the other.

---

## Building

Android needs the Android SDK (API 35) and JDK 17:

```bash
cd android
./gradlew :app:assembleDebug        # APK
./gradlew :app:bundleRelease        # AAB for Play
./gradlew :app:testDebugUnitTest    # unit tests
```

iOS: see [`ios/README.md`](ios/README.md). The Swift files are the shared layer
and the store configuration; the SwiftUI screens are not written yet.

---

## How it is put together

**Offline-first, not offline-tolerant.** Every screen reads and writes the
local Room database. `SyncEngine` is an interface whose default implementation
does nothing, so the offline build and the on-premise government build are
different implementations rather than different apps. No code path blocks on a
network.

**Localisation is data, not code.** Adding a language is a translation job:
add the code and its strings to `shared/i18n/strings.json`, run the generator,
and register the code in two files. The language picker builds itself from
`locales_config.xml` and shows each language in itself; layout direction comes
from the platform, so adding Persian mirrors the UI with no list of RTL
languages anywhere in the source. Catalogue text is a map keyed by language
with a fallback chain, so a fourth language needs no database migration.

**Things the app will not let you do.** Stock cannot change without a movement
row and an audit entry, because both are written on the same code path as the
quantity. A safety checklist cannot be signed while a critical check is failed
or unanswered, and that block is recomputed from the answers rather than held
as a flag a screen could clear. The audit log has insert and read methods and
one retention purge, which records its own purge.

**Encrypted, but not brittle.** The database is SQLCipher AES-256 with a key
generated on the device and held in the Android Keystore. If the native library
cannot load, the app opens unencrypted and says so in Settings, rather than
refusing to start on a site with no signal.

---

## State of the work

Built and tested:

- the shared catalogues, and the generator and format they depend on
- the Room data layer: 19 entities, seeding with a duplicate guard, stock
  movements, audit trail, encryption
- Compose screens: onboarding, home, inventory list and editor, projects with
  templates, day schedule with check-in, safety checklist runs with sign-off,
  settings
- 30 unit tests, all passing — locale resolution and fallback, Israeli
  date/time/currency formats, time-of-day parsing, and the catalogue integrity
  suite that parses all 178 items, 84 checks and 12 templates through the app's
  own model types with unknown keys rejected

Not built yet, and honestly not started:

- barcode scanning, photo capture and the export/report generation are wired
  as dependencies and permissions but have no screens
- the cloud sync implementation behind `SyncEngine`, and the on-premise service
- iOS screens
- Hebrew and Arabic terminology review by a native-speaking tradesperson, and a
  VoiceOver/TalkBack accessibility pass

The safety content carries a warning in the app and in
[`docs/CATALOG_FORMAT.md`](docs/CATALOG_FORMAT.md): the regulation references
are pointers for the site file, not the text of the standard and not legal
advice, and must be reviewed against the current published regulation before
anyone relies on the app for compliance.
