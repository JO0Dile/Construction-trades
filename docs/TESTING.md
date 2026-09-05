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

## Updating a phone that already has it

Settings → App updates → Check for updates. To publish the update people will
see there, see [UPDATES.md](UPDATES.md) — it is one `git tag`, plus three
caveats worth reading before you rely on it.

## Android on a PC — emulator

No phone required. Two routes; the first is better if you want to report bugs
back, because it gives you the crash log.

### Android Studio, step by step

Assumes you have Android Studio installed and have never used it. It is slow
the first time and fast every time after.

#### 1. Get the code onto your computer

Either clone it:

```bash
git clone https://github.com/JO0Dile/Construction-trades.git
```

or, with no Git at all: open
<https://github.com/JO0Dile/Construction-trades> → green **Code** button →
**Download ZIP** → unzip it somewhere.

#### 2. Open the `android` folder — not the top folder

This is the step everyone gets wrong. In Android Studio: **File → Open**, then
select the **`android`** folder *inside* what you just downloaded.

```
Construction-trades/     ← NOT this one
  android/               ← THIS one. Open this.
    app/
    gradle/
    settings.gradle.kts
  ios/
  shared/
```

If you open the top folder instead, Android Studio shows a plain file list
with no Run button and complains it cannot find a Gradle project. If that
happens, just **File → Open** the `android` folder instead.

#### 3. Wait for the first sync

The status bar at the bottom says *Gradle sync in progress*. **This takes 5–15
minutes the first time** — it is downloading the Android SDK and every library
the app uses. It is not frozen. Later syncs take seconds.

While it runs you may get banners at the top:

- *"Install missing SDK components"* / *"Accept licences"* → **accept**.
- *"Android Gradle Plugin upgrade recommended"* → **decline / "Remind me
  later"**. The pinned version is the one CI builds with; upgrading here would
  quietly put your build out of step with it.

#### 4. Create a phone to run it on

1. Open **Device Manager** — the phone icon down the right-hand edge, or
   **View → Tool Windows → Device Manager**.
2. Click **+** → **Create Virtual Device**.
3. Pick any phone (**Pixel 7** is a safe choice) → **Next**.
4. Pick a system image. If it has a **⬇** next to its name, click that first
   and wait for the download. **API 35** or **API 34** are both fine.
5. **Next** → **Finish**.

#### 5. Run it

At the top of the window there is a device dropdown and a green **▶**.

1. Choose your **Pixel 7** in the dropdown.
2. Press **▶**.

It compiles (a few minutes the first time), boots the emulator, installs the
app and opens it. You should land on the onboarding screen asking for a
language and your trades.

#### When it goes wrong

| What you see | What it means |
|---|---|
| No **▶** button, no Gradle | You opened the top folder. Open `android` instead (step 2). |
| *"SDK licences not accepted"* | **Tools → SDK Manager → SDK Tools** tab → accept and install. |
| Emulator never boots, or *"HAXM/WHPX not installed"* | Hardware virtualisation is off. Enable **Intel VT-x / AMD-V** in your BIOS; on Windows also enable *Windows Hypervisor Platform* in *Turn Windows features on or off*. |
| App installs then closes immediately | Open the **Logcat** tab at the bottom and copy the red lines — that is the crash, and it is exactly what to send back. |

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

**Jobs — the ones that are not a template**
- [ ] Projects → **+**. Name it, pick **Lobby** as the type of place, leave
      *Empty project* selected, Create. It should open a job with no tasks and
      no materials — and both sections still visible, each with a **+**.
- [ ] Add materials to it by typing: `מרבטים`, `כבל 1.5`, whatever the job
      needs. Nothing has to be in the catalogue.
- [ ] Now type a couple of letters of something that *is* — matches appear
      underneath as you type, and picking one fills in the name and the unit.
- [ ] Type your own kind of place: **+** → *Something else* → anything. It
      should be accepted exactly as typed, in any language.
- [ ] Settings → Trades → **+**. Add a trade the app does not ship, e.g.
      *Roofing*. It appears in the list marked **Yours**, switched on, and only
      that one has a delete button — the six shipped trades do not.

**Projects as pictures**
- [ ] Photograph a floor plan inside a job (Photos → camera or gallery).
- [ ] Back on Projects, the job's card should now show that plan.
- [ ] Top-right toggle switches between picture cards and a list, and the
      choice survives leaving the app and coming back.

**Updates**
- [ ] Settings → App updates. It shows the version you are running.
- [ ] *Check for updates.* With no newer release published it should say you
      are on the latest — not fail, and not offer you the version you have.

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
