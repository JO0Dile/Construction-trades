# Trades Work Manager · מנהל עבודות · مدير أعمال المهن

A field management app for construction and trades work in Israel —
electricians, plumbers, HVAC and refrigeration technicians, painters,
carpenters and general builders. Tools and materials, day planning, projects,
safety sign-off and the audit trail behind all of it, in Hebrew, Arabic and
English, working with no signal.

**Native apps.** Android is Kotlin and Jetpack Compose; iOS is Swift and
SwiftUI. There is no WebView, no PWA, no hybrid shell, and no browser
dependency for anything.

> **This project is meant to live in a repository of its own.** It currently
> sits inside an unrelated one. `tools/extract-standalone-repo.sh` lifts it out
> — with its history, not as a flat copy — into a standalone repo whose root is
> this folder. See [Moving it out](#moving-it-out).

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

iOS needs a Mac: see [`ios/README.md`](ios/README.md) for the fifteen-minute
Xcode set-up, and [`docs/TESTING.md`](docs/TESTING.md) for getting a build onto
an actual phone on either platform.

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

## Moving it out

```bash
./tools/extract-standalone-repo.sh                       # build it locally
./tools/extract-standalone-repo.sh git@github.com:you/trades-work-manager.git
```

The script uses `git subtree split`, so the new repository's history is the
commits that touched this folder — not a single "initial commit" that throws
the rest away. It also rewrites the CI workflow's paths for the new root. The
target repository must be **empty** (no README, no licence).

---

## State of the work

Built, and verified by running:

- the shared catalogues, and the generator and format they depend on
- the Room data layer: 19 entities, seeding with a duplicate guard, stock
  movements, audit trail, encryption
- Compose screens: onboarding, home, inventory list and editor, projects with
  templates, day schedule with check-in, safety checklist runs with sign-off,
  settings
- SwiftUI screens covering the same ground, over SwiftData models that mirror
  the Room schema field for field, with in-app language switching that needs no
  restart
- barcode scanning on both platforms — CameraX with ML Kit on Android,
  AVFoundation on iOS — and CSV + PDF export that survives Excel on Windows and
  mirrors its columns for Hebrew and Arabic
- 40 unit tests, all passing — locale resolution and fallback, Israeli
  date/time/currency formats, time-of-day parsing, CSV quoting and filename
  reduction, and a catalogue integrity suite that parses all 178 items, 84
  checks and 12 templates through the app's own model types with unknown keys
  rejected

Not built, and not pretended otherwise:

- **No `.xcodeproj`** — an Xcode project cannot be generated faithfully without
  Xcode, so the iOS set-up is fifteen manual minutes on a Mac.
- The Swift has never been compiled; this repository was built in an
  environment with no Swift toolchain and no Android SDK. The Kotlin's pure
  layers are compiled and tested, the Android build itself is not.
- Photo capture, the cloud sync implementation behind `SyncEngine`, and the
  on-premise service.
- Hebrew and Arabic terminology review by a native-speaking tradesperson, and a
  VoiceOver/TalkBack accessibility pass.

The safety content carries a warning in the app and in
[`docs/CATALOG_FORMAT.md`](docs/CATALOG_FORMAT.md): the regulation references
are pointers for the site file, not the text of the standard and not legal
advice, and must be reviewed against the current published regulation before
anyone relies on the app for compliance.
