# iOS target

The App Store half of the product: a SwiftUI app over SwiftData, plus the
store-facing configuration and the shared content layer that must agree with
Android exactly.

## What is in this folder

```
ios/TradesManager/
  App/             entry point, root tabs, theme
  Model/           SwiftData models, DataStore, settings, TimeOfDay
  Localization/    instant in-app language switching
  Shared/          Swift mirror of the shared content layer
    LocalizedText.swift   the same resolve() fallback chain as Kotlin
    Catalog.swift         Codable models for shared/assets/catalog
    CatalogSource.swift   bundled catalogue, preferring a newer downloaded one
    Formats.swift         ₪, dd/MM/yyyy, 24-hour, metric
  Features/        Onboarding, Home, Inventory, Projects, Schedule, Safety,
                   Settings, Scanner, Export
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

## Two decisions worth knowing about

**Deployment target is iOS 17**, because SwiftData and `@Observable` need it.
That covers the iPhone XS and later. If older phones turn out to matter on
site, the persistence layer is the only part that has to change — the screens
and the shared layer do not care.

**Language switching does not restart the app.** iOS has no equivalent of
`AppCompatDelegate.setApplicationLocales`, and the usual workaround — write
`AppleLanguages` and ask the user to relaunch — is unacceptable on a building
site. So `Localization` resolves strings through the chosen language's `.lproj`
bundle and pushes `locale` and `layoutDirection` into the environment, and
`@Observable` re-renders the tree. The picker lists whatever `.lproj` folders
are in the bundle, so a new translation appears with no code change.

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

- **The Xcode project itself.** There is no `.xcodeproj` here; generating a
  valid one without Xcode is not something to fake. The set-up above is the
  whole of it.
- Photo capture and attachment.
- The sync implementation behind the Android `SyncEngine` interface, and the
  on-premise service.
- File protection: set `NSPersistentStoreFileProtectionKey` to
  `.completeUnlessOpen` on the SwiftData store, which is the iOS answer to the
  SQLCipher layer on Android. The requirement is encryption at rest, not a
  particular library.
- A VoiceOver pass in all three languages.

## Before submitting

Read `../docs/STORE_COMPLIANCE.md`. The items that most often stop a first
submission for an app like this one are the export-compliance answer (the app
does use non-exempt encryption), the privacy manifest matching the build, and
Hebrew and Arabic screenshots actually taken in those languages.
