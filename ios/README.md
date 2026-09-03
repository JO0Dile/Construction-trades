# iOS target

The App Store half of the product. What is here is the part that must agree
with Android exactly — the shared content layer and the store-facing
configuration. The SwiftUI screens are not written yet.

## What is in this folder

```
ios/TradesManager/
  Shared/          Swift mirror of the shared content layer
    LocalizedText.swift   the same resolve() fallback chain as Kotlin
    Catalog.swift         Codable models for shared/assets/catalog
    CatalogSource.swift   bundled catalogue, preferring a newer downloaded one
    Formats.swift         ₪, dd/MM/yyyy, 24-hour, metric
  Resources/
    en.lproj/ he.lproj/ ar.lproj/   generated — do not edit
      Localizable.strings
      Localizable.stringsdict
      InfoPlist.strings
  Support/
    Info.plist            permission purpose strings, localisations, export flag
    PrivacyInfo.xcprivacy Apple's required privacy manifest
```

These Swift files have **not been compiled**: this repository was developed in
an environment with no Swift toolchain and no Xcode. They are written against
the same JSON that `CatalogIntegrityTest` validates, and the Kotlin equivalents
of the same logic are covered by passing tests, but expect to fix compiler
diagnostics on first build.

## Creating the Xcode project

1. New App, SwiftUI lifecycle, bundle id `il.co.tradesmanager`, deployment
   target iOS 15 or later.
2. Replace the generated `Info.plist` with `Support/Info.plist`.
3. Add `Support/PrivacyInfo.xcprivacy` to the app target's resources.
4. Add `Shared/*.swift` to the target.
5. Add `Resources/*.lproj` as **localisation** folders (Xcode picks these up as
   the app's localisations once the language is listed in the project's
   Localizations and in `CFBundleLocalizations`).
6. Add `../../shared/assets/catalog` as a **folder reference** (blue folder),
   not a group. That preserves the `catalog/…` directory structure inside the
   bundle, which is what `CatalogSource` expects and what keeps the two
   platforms reading identical paths.

Do not copy the catalogue into `ios/`. One copy, read by both apps, is the
whole point: a correction to a cable specification must not be able to land on
Android and be forgotten on iOS.

## Regenerating the localisations

```bash
python3 ../tools/gen-strings.py
```

Run this from the repository, not from Xcode. Never edit a `.strings`,
`.stringsdict` or `InfoPlist.strings` file directly — the generator overwrites
them, and the Android resources would silently drift out of step.

## What still has to be built

- SwiftUI screens matching the Android set: onboarding, home, inventory,
  projects, schedule, safety, settings.
- Local persistence. SwiftData or Core Data with `NSPersistentStoreFileProtectionKey`
  set to `.completeUnlessOpen` is the iOS answer to the SQLCipher layer on
  Android; the requirement is encryption at rest, not a particular library.
- The seeding rules, which are behaviour and not data, and must be ported
  faithfully: reference data is separate from the user's own stock, and a
  catalogue id already stocked is never stocked twice.
- Sign-off blocking: a critical safety check that is failed or unanswered must
  block the signature on iOS exactly as it does on Android.

## Before submitting

Read `../docs/STORE_COMPLIANCE.md`. The items that most often stop a first
submission for an app like this one are the export-compliance answer (the app
does use non-exempt encryption), the privacy manifest matching the build, and
Hebrew and Arabic screenshots actually taken in those languages.
