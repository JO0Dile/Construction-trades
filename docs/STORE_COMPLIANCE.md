# Store compliance — Google Play and the App Store

The app ships to both stores. Their rules overlap but are not the same, and a
few of them shape the *code*, not just the listing. This document records which
rule each decision answers, so a reviewer's question has an answer in the
repository rather than in someone's memory.

Store policies change. Everything marked **verify** below is a policy whose
current text must be re-read at submission time; everything else is a property
of this codebase that you can check by reading it.

---

## 1. What is already true of this codebase

| Requirement | Where it is satisfied |
|---|---|
| Native app, not a web wrapper | `android/` is Kotlin + Jetpack Compose; there is no `WebView` in the source tree. `ios/` is Swift. |
| Works offline | Every screen reads and writes Room. `SyncEngine` defaults to `NoOpSyncEngine`; no code path blocks on the network. |
| Permissions explained before they are requested | `ui/components/PermissionGate.kt` shows the reason first; iOS purpose strings are in `Info.plist` and localised in `*.lproj/InfoPlist.strings`. |
| Permissions are optional where the feature is optional | Camera and location are `required="false"` in the manifest and absent from `UIRequiredDeviceCapabilities`. A refused location permission still records the check-in. |
| No background location | Only `ACCESS_COARSE_LOCATION` / `ACCESS_FINE_LOCATION`; no `ACCESS_BACKGROUND_LOCATION`, and no foreground service. |
| No broad package visibility | The manifest's `<queries>` names only the speech-recognition intent. `QUERY_ALL_PACKAGES` is not requested. |
| Account and data deletion, in-app | Settings → "Delete my account and data" calls `SettingsViewModel.deleteEverything`, which clears every table and every preference. |
| No automatic cloud backup of personal data | `android:allowBackup="false"` plus `res/xml/data_extraction_rules.xml` excludes every domain. |
| Encryption at rest | SQLCipher AES-256 with a key held in the Android Keystore (`core/security/DatabaseKey.kt`). |
| Target API level | `targetSdk 35`, `minSdk 26`. |
| 64-bit | ABI splits build `arm64-v8a` and `x86_64` alongside `armeabi-v7a`. |
| Privacy manifest | `ios/TradesManager/Support/PrivacyInfo.xcprivacy`, with a reason code for each required-reason API. |
| Localised store-facing text | Hebrew, Arabic and English generated from one source by `tools/gen-strings.py`. |
| Right-to-left | `android:supportsRtl="true"`; layouts use start/end semantics and `Arrangement.SpaceBetween` rather than hard-coded left/right. |

---

## 2. Google Play

### Policy items that need a decision or a filing

- **Data safety form.** For the offline build the honest answers are: no data
  collected, no data shared, data is encrypted in transit (n/a — nothing is in
  transit) and at rest, and the user can request deletion. If a build enables
  cloud sync, the form must be redone: names, photos, and approximate location
  then leave the device.
- **Account deletion policy.** Play requires apps that let a user create an
  account to offer deletion both in-app *and* through a web URL that does not
  require the app. The in-app half is built; the web half is a hosting task for
  the publisher and must exist before the listing goes live.
- **Privacy policy URL.** Required in the listing and linked from Settings.
  Must be available in Hebrew, Arabic and English.
- **Content rating questionnaire.** Business/productivity, no user-generated
  content shared publicly, no ads.
- **App signing.** Use Play App Signing. Keep the upload key out of the
  repository — `android/.gitignore` already excludes `*.keystore` and
  `keystore.properties`.
- **AAB, not APK.** `./gradlew :app:bundleRelease`.
- **verify — target API deadline.** Play raises the minimum `targetSdk` for
  updates each year. Re-check before each release.

### Permissions the listing will be asked about

| Permission | Why | If refused |
|---|---|---|
| `CAMERA` | Item and site photos, barcode scanning | Those features are unavailable; everything else works |
| `ACCESS_COARSE/FINE_LOCATION` | Optional GPS stamp on check-ins, site photos, incident reports | Check-in is still recorded, without a stamp |
| `RECORD_AUDIO` | Hebrew/Arabic dictation of task notes | Type the note instead |
| `POST_NOTIFICATIONS` | Task reminders, low-stock alerts | No reminders |
| `INTERNET`, `ACCESS_NETWORK_STATE` | Optional sync only | The app is unaffected |

None of these is a Play "sensitive permission" requiring a declaration form,
because the app requests no background location, no SMS/call log, no
all-files access, and no accessibility-service API.

### Media

The app writes photos to its own private storage and never reads the shared
media store, so it requests no `READ_MEDIA_*` permission. If a "pick from
gallery" feature is added later, use the Android Photo Picker, which needs no
permission and keeps this section true.

---

## 3. App Store

### Guidelines that bear on this app

- **2.1 App Completeness / 2.3 Accurate Metadata.** Reviewers must be able to
  reach every feature. Supply a demo account or, better, note in App Review
  Notes that the app needs no account: it onboards straight into full offline
  function.
- **4.2 Minimum Functionality.** Satisfied by being a real native app with
  substantial offline function; the pre-loaded catalogues are the answer to
  "what does it do before the user types anything".
- **5.1.1 Data Collection and Storage.** Purpose strings are present and
  localised. The privacy manifest declares no tracking and no collection for
  the offline build.
- **5.1.1(v) Account Deletion.** If the app ever offers account creation, the
  in-app deletion built into Settings is the required path. The offline build
  creates no account.
- **4.8 Login Services.** Only relevant if third-party or social sign-in is
  added. Enterprise/education SSO where the user must use an account their
  employer already issued (Active Directory, a ministry account) falls in the
  exemption — but **verify** the current wording before relying on it. Adding
  Google or Facebook sign-in would oblige you to offer Sign in with Apple too.
- **3.1.1 In-App Purchase.** Selling app functionality to consumers on iOS must
  go through IAP. Two routes avoid that for this product's actual market:
  - **Apple Business Manager Custom Apps** for a ministry, municipality or
    company deployment, which is also the natural home for an on-premise build.
  - **Apple Developer Enterprise Program** for purely internal distribution
    within one organisation.
  **verify** the commercial model against 3.1 before submitting a paid tier.
- **Export compliance.** `ITSAppUsesNonExemptEncryption` is `true` because the
  app encrypts its database with AES-256. That is a deliberate, conservative
  declaration: it routes the submission through App Store Connect's
  export-compliance questions instead of skipping them. Completing the US
  self-classification report (or obtaining an ERN) is a legal filing for the
  publisher, not a build setting.
- **App Privacy "nutrition label".** Must match `PrivacyInfo.xcprivacy`. For the
  offline build: "Data Not Collected".
- **Localised listing.** Hebrew, Arabic and English screenshots and
  descriptions. Hebrew and Arabic screenshots must be taken with the device in
  that language so the mirrored layout is visible.

### When the privacy manifest has to change

`PrivacyInfo.xcprivacy` describes the build it ships in. Update it before
submitting a build that:

- enables cloud sync (names, photos and coarse location then leave the device);
- adds crash reporting or analytics — Firebase Crashlytics, mentioned in the
  original brief, collects crash data and identifiers and brings its own
  privacy manifest that must be merged;
- adds any third-party SDK on Apple's list of SDKs that require a manifest and
  a signature.

---

## 4. Shared obligations, Israel

- **Privacy Protection Law, 5741-1981**, and the Privacy Protection (Data
  Security) Regulations, 5777-2017. The app's answers: personal data is
  encrypted at rest, is not transmitted by default, has an in-app deletion
  path, and every change is recorded in a log the app cannot rewrite
  (`AuditDao` has insert and read methods and a single retention purge, which
  logs itself).
- **Equal Rights for Persons with Disabilities Act** and its service
  accessibility regulations. What the code does: a "Larger text" setting that
  scales the whole type ramp, Material 3 contrast pairs, touch targets at the
  platform minimum, and content descriptions on every icon-only control. What
  is still owed: a VoiceOver and TalkBack pass in all three languages, and an
  accessibility statement in the listing.
- **Data residency for government deployments.** The on-premise build is the
  answer: `SyncEngine` is an interface precisely so a ministry installation can
  point at its own service, or use none at all.

---

## 5. Pre-submission checklist

Run these before either store:

```bash
# Content and translations are complete and consistent
python3 tools/gen-strings.py --check

# The catalogues parse, are trilingual, and every template line resolves
cd android && ./gradlew :app:testDebugUnitTest

# Release artefacts
./gradlew :app:bundleRelease        # Play
./gradlew :app:assembleRelease      # sideload / MDM
```

Then, by hand:

- [ ] Walk the app in Hebrew, Arabic and English, checking mirrored layout and
      that no string is cut off at the larger text setting.
- [ ] Confirm every permission prompt is preceded by its reason screen.
- [ ] Confirm Settings → delete really empties the database (re-open the app;
      it should return to onboarding).
- [ ] Fill in the Play Data safety form and the App Store privacy answers from
      section 2 and 3 above, not from memory.
- [ ] Check the privacy policy and terms are reachable and translated.
- [ ] Re-read the two **verify** items: Play's current `targetSdk` minimum and
      Apple's current 4.8 and 3.1.1 wording.
