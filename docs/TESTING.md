# Getting it running

Three ways in, depending on what hardware you have. The Android ones work on
any computer; iOS genuinely cannot exist without a Mac.

- [On an Android phone](#android--the-path-that-needs-nothing-but-a-phone) — no tools, just the APK
- [On a PC, in an emulator](#android-on-a-pc--emulator) — no phone needed
- [On iOS](#ios--needs-a-mac) — needs a Mac

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

## Android on a PC — emulator

No phone required. Two routes; the first is better if you want to report bugs
back, because it gives you the crash log.

### Android Studio (recommended)

1. Install [Android Studio](https://developer.android.com/studio) — free, and
   it brings the SDK and emulator with it.
2. **Open** the `android/` folder of this repository (not the repository root
   — the Gradle project lives one level down). Let it sync; the first sync
   downloads the SDK and dependencies and takes a while.
3. **Device Manager** → **Create Virtual Device** → pick any phone, e.g.
   Pixel 7 → choose a system image (API 34 or 35) → Download → Finish.
4. Press **Run** (▶). It builds, starts the emulator and installs the app.

You do not need the CI artifact at all this way — you are building from source.

### Or drop the APK onto a running emulator

If you already downloaded the artifact: start any emulator, then **drag the
`app-universal-debug.apk` file onto the emulator window**. It installs.

Same thing from a terminal, which also works for a real phone over USB with
developer mode on:

```bash
adb install -r app-universal-debug.apk
```

### What the emulator does and does not do well

| | |
|---|---|
| Hebrew and Arabic, RTL mirroring | Works exactly as on a phone |
| Larger text, dark mode | Fine |
| Everything offline | Fine — and the emulator can be put in flight mode too |
| **Barcode scanning** | Awkward. The emulator's camera shows a synthetic scene by default. Fix: **Device Manager → edit the AVD (pencil) → Show Advanced Settings → Camera Back → `Webcam0`**, then hold a real barcode up to your laptop's webcam. |
| **GPS check-in stamp** | Set a position in the emulator's **⋯ Extended controls → Location**. |
| **Export share sheet** | An emulator has few share targets. To actually inspect the exported files, pull them (below). |

### Getting the exported CSV and PDF off the device

The export writes to the app's private cache, which is exactly where it should
go. Debug builds are debuggable, so `run-as` can read it — this works on an
emulator and on a real phone alike:

```bash
adb shell run-as il.co.tradesmanager.debug ls cache/exports
adb exec-out run-as il.co.tradesmanager.debug cat cache/exports/inventory-2026-09-04.csv > inventory.csv
```

Open `inventory.csv` in Excel or LibreOffice. Hebrew and Arabic must appear as
readable text; if you see `Ã—Ö¸` the byte-order mark has been lost somewhere and
that is a bug worth reporting.

### Getting a crash log

If something crashes or misbehaves, this is the single most useful thing to
send back:

```bash
adb logcat -v time | grep -i "tradesmanager\|AndroidRuntime"
```

In Android Studio the same thing is the **Logcat** tab at the bottom.

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
