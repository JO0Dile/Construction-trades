# Getting it onto a phone

Two different situations, because the two platforms genuinely differ: an
Android build can be produced by a machine in the cloud and sideloaded, and an
iOS build cannot exist without a Mac.

---

## Android — the path that needs nothing but a phone

Every push builds a debug APK in GitHub Actions. That artifact is the app.

1. Open the repository on GitHub → **Actions** tab.
2. Pick the most recent **CI** run on your branch.
3. Scroll to **Artifacts** at the bottom → download **trades-work-manager-debug-apk**.
4. Unzip it. Inside are the per-ABI APKs and one universal APK. On a phone,
   take **`app-universal-debug.apk`** — it runs on any device. The
   `arm64-v8a` one is smaller and works on essentially every phone sold since
   2017, if you prefer.
5. Move it to the phone (cable, Drive, email to yourself) and tap it.
   Android will ask you to allow installing from that app the first time —
   Settings → *Install unknown apps* → whichever app you opened it from.

The debug build installs as **Trades Work Manager (debug)** with its own
package id (`il.co.tradesmanager.debug`), so it sits alongside a release build
without either overwriting the other.

> The Swift has never been compiled — this repository was built in an
> environment with no Swift toolchain and no Xcode. The Android side is
> compiled and tested by the workflow above on every push.

### Or build it yourself

With Android Studio (Ladybug or newer) or a JDK 17 and the Android SDK:

```bash
cd android
./gradlew :app:assembleDebug
# app/build/outputs/apk/debug/app-universal-debug.apk
./gradlew :app:installDebug     # straight onto a connected phone
```

---

## iOS — needs a Mac

There is no way around this: Apple only builds iOS apps on macOS, and only
Xcode can sign them. The Swift source is complete for the screens listed in the
README, but there is **no `.xcodeproj` in the repository yet**, because
generating a valid one without Xcode is not something to fake.

On a Mac, roughly fifteen minutes:

1. Xcode → **New Project** → iOS → App. SwiftUI, name `TradesManager`,
   bundle id `il.co.tradesmanager`, **deployment target iOS 17** (SwiftData
   and `@Observable` need it).
2. Delete the generated `ContentView.swift` and `…App.swift`.
3. Drag in `ios/TradesManager/` — `App/`, `Model/`, `Localization/`,
   `Shared/`, `Features/`. Choose *Create groups*.
4. Replace the generated `Info.plist` with `ios/TradesManager/Support/Info.plist`,
   and add `Support/PrivacyInfo.xcprivacy` to the target.
5. Add `ios/TradesManager/Resources/en.lproj`, `he.lproj`, `ar.lproj`, and add
   Hebrew and Arabic under **Project → Info → Localizations**.
6. Add `shared/assets/catalog` as a **folder reference** — the blue folder
   option, not *Create groups*. This is what preserves the `catalog/…`
   structure the loader expects, and what keeps one copy of the catalogues
   feeding both apps.
7. ⌘R.

To put it on other people's phones, archive and upload to **TestFlight**; that
needs a paid Apple Developer account. For a ministry or municipality
deployment, **Apple Business Manager Custom Apps** is the route — see
`docs/STORE_COMPLIANCE.md`.

---

## What to actually try

The things worth checking are the ones that are easy to get wrong and that this
app claims to get right:

**Language and direction**
- [ ] Onboarding: pick Hebrew, then Arabic, then English. The whole interface
      should flip direction immediately, with no restart, including the
      navigation bar and the back arrows.
- [ ] Every language names itself in the picker — עברית, العربية, English.
- [ ] Settings → Larger text. Nothing should collide or clip.

**The catalogues**
- [ ] Pick *Electrical* at onboarding and open Inventory: about 33 items, each
      with a real specification, in the language you chose.
- [ ] Search `כבל` in English mode — it should still find the cables, because
      the search index holds every translation.
- [ ] Settings → Reload catalogues, twice. The second run should add **zero**
      items. That is the duplicate guard.
- [ ] Change an item's name, then reload catalogues again. Your name survives.

**Stock**
- [ ] The −/+ buttons move quantity. Take one item to zero and press − again:
      it stays at zero, never negative.
- [ ] Set a low-stock threshold above the quantity; the row gets a *Low* flag
      and Home counts it.

**Safety — the one that matters**
- [ ] Safety → *Isolation and lockout before work*.
- [ ] Answer every check **Done** except one marked *Critical* — answer that
      one **Not done**.
- [ ] The sign-off button stays disabled and the screen says why. That is the
      point: a signature must not be able to override the regulation.
- [ ] Change it to Done; sign-off unlocks.

**Scanning and export**
- [ ] Inventory → scan icon. The reason for the camera appears *before* the
      system prompt.
- [ ] Scan any barcode. If it matches an item's stored code the item opens; if
      not, a new item starts.
- [ ] Inventory → share icon → a PDF and a CSV. Open the CSV on a computer:
      Hebrew and Arabic columns must be readable text, not `Ã—Ö¸`. Open the PDF
      in Hebrew mode: the columns should run right to left.

**Offline**
- [ ] Turn on flight mode and use the whole app. Nothing should fail, hang, or
      show a network error — there is no network call in any of it.

---

## Running the checks without a phone

```bash
python3 tools/gen-strings.py --check          # translations complete & current
cd android && ./gradlew :app:testDebugUnitTest # 40 unit tests
```

The tests cover locale resolution and fallback, the Israeli date/time/currency
formats, time-of-day parsing, CSV quoting and filename reduction, and a suite
that parses every catalogue file through the app's own model types with unknown
keys rejected.
