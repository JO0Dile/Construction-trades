# Trades Work Manager · מנהל עבודות · مدير أعمال المهن

A field management app for construction and trades work in Israel —
electricians, plumbers, HVAC and refrigeration technicians, painters,
carpenters and general builders. Tools and materials, day planning, projects,
safety sign-off and the audit trail behind all of it, in Hebrew, Arabic and
English, working with no signal.

**Native apps.** Android is Kotlin and Jetpack Compose; iOS is Swift and
SwiftUI. There is no WebView, no PWA, no hybrid shell, and no browser
dependency for anything.

> **© 2026 JO0Dile. All rights reserved.** This is not open source. Reading
> this repository grants no licence to copy, modify or distribute any part of
> it — see [`LICENSE`](LICENSE). The open-source libraries it is built on keep
> their own licences and are unaffected.

---

## Get it on a phone

**[Download the latest APK](https://github.com/JO0Dile/Construction-trades/releases/latest)**
— open that on the phone, take `app-universal-debug.apk`, and tap it. Android
will ask once whether to allow installing from this app; that permission is
only used for this app's own updates.

`app-universal-debug.apk` runs on every phone. The `arm64-v8a` and
`armeabi-v7a` files beside it are smaller builds for one chip each — take the
universal one unless you know which you want.

After that the app updates itself: **Settings → App updates → Check for
updates**. There is no need to come back here.

> Install **over** an existing copy rather than uninstalling first. The app
> migrates its database forward and keeps your jobs, stock and photos; an
> uninstall throws them away.

Building it yourself, running it on an emulator, and getting a crash log are
all in **[docs/TESTING.md](docs/TESTING.md)**. Publishing a new version is one
`git tag` — **[docs/UPDATES.md](docs/UPDATES.md)**.

---

## What makes it usable on day one

The app ships with the catalogues already written:

| | |
|---|---|
| Trades | 22 — from electrical, plumbing and HVAC to gypsum, stonework, tiling, plastering, aluminium, rebar, waterproofing, firefighting, lifts and landscape |
| Tools and materials | 527, every one named in Hebrew, Arabic and English, and every one with a specification |
| Work breakdown | 36 scopes of work across 6 stages, each carrying the term the crew says as well as the one the contract says |
| Safety checklists | 47, holding 201 checks, one for every trade, each citing the Israeli regulation or standard it comes from |
| Project templates | 24, whose material lines all resolve to real catalogue items |

Pick your trades during onboarding and the lists are there. Add your own items
at any time; a catalogue refresh never touches them, and never creates a second
copy of something you have been counting.

---

## Layout

```
shared/
  assets/catalog/       the catalogues — bundled unchanged by BOTH apps
  i18n/strings.json     the only place UI text is written
android/                Gradle + Kotlin + Compose  (namespace il.co.tradesmanager)
ios/                    Swift + SwiftUI + SwiftData, Info.plist, privacy manifest
tools/gen-strings.py    strings.json -> strings.xml + .strings/.stringsdict
docs/
  TESTING.md            how to get a build onto an actual phone
  STORE_COMPLIANCE.md   Google Play and App Store rules, mapped to this code
  LOCALIZATION.md       how to add a language without touching code
  CATALOG_FORMAT.md     the catalogue file format and its invariants
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

## Taking this on

Buying it, or being handed it? **[`docs/HANDOVER.md`](docs/HANDOVER.md)** is
the one to read first: what runs today at no cost, what is prepared and
deliberately switched off — SMS codes and billing — with the exact steps and
real costs to switch each on, and the short list of things that need a person
rather than any more code.

## State of the work

Every push builds the app, runs the tests and runs twenty-eight checks. Nothing
below is a claim about what was intended — it is what the build proves.

**Built and verified:**

- 22 trades, **527 catalogue items**, 201 safety checks and 24 project
  templates, all parsed through the app's own model types with unknown keys
  rejected
- **1,468 interface strings and 20 plural rules in Hebrew, Arabic and
  English**, with every catalogue block trilingual too. The build fails if one
  language goes missing, if the English is pasted into another, or if an
  English sentence is written into the audit register
- a Room data layer: **59 entities, 34 migrations** replayed end to end on
  every push with a check that no rebuild loses its rows, plus seeding with a
  duplicate guard, stock movements, a tamper-evident audit trail and SQLCipher
- the five lenses as Compose screens. The SwiftUI app covers the first
  version of the ground — inventory, projects, schedule, safety checklists,
  the scanner, export — over 14 SwiftData models; it has not kept pace with
  the 59 Android tables, and everything built since (money, people, the site
  registers, the roll call, heat and pre-use checks, the equipment and
  visitor logs) is Android only
- barcode scanning on both platforms, and CSV + PDF export that survives Excel
  on Windows and mirrors its columns for Hebrew and Arabic
- **753 unit tests, all passing**, and a release bundle built through R8 on
  every push — because a debug build does not minify, and something Room,
  SQLCipher or kotlinx.serialization loads reflectively should not first go
  missing on the day of a store submission

**Not built, and not pretended otherwise:**

- **No server.** Sync, cross-firm confidentiality as an access rule rather than
  a display rule, and anchoring the audit chain somewhere the holder of the
  phone does not control all wait on it. See
  [`docs/SERVER.md`](docs/SERVER.md).
- **No billing.** The plans are decided and readable in Settings; nothing is
  sold. An entitlement kept on the device can be set by whoever holds the
  device, so there is nothing to enforce yet —
  [`docs/PRICING.md`](docs/PRICING.md) says what has to exist first.
- **No `.xcodeproj`**, and the Swift has never been compiled: this repository
  is worked on in a container with no Swift toolchain. The iOS set-up is
  fifteen manual minutes on a Mac. The Android build *is* compiled, linted and
  tested on every push.
- **112 catalogue items still have no photograph** — everything else has one.
  The list, with a brief for each, is in
  [`docs/PHOTOS.md`](docs/PHOTOS.md).
- Hebrew and Arabic **terminology review by a native-speaking tradesperson**.
  The translations are complete and the build keeps them that way; whether the
  words are the ones used on a site is a different claim, and not yet made.
- A VoiceOver/TalkBack pass. Every icon in the app carries a content
  description and no icon-only button is unlabelled, but that has been checked
  by reading the source, not by listening to it.

The safety content carries a warning in the app and in
[`docs/CATALOG_FORMAT.md`](docs/CATALOG_FORMAT.md): the regulation references
are pointers for the site file, not the text of the standard and not legal
advice, and must be reviewed against the current published regulation before
anyone relies on the app for compliance.
