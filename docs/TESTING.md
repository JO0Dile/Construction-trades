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

## Sending catalogue content, terms and photos

See [CONTENT.md](CONTENT.md) — the formats, the naming rule for photographs,
and why prices should wait for Phase 3.

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

**Money on a job**
- [ ] Open a job → **Money**. Set a contract value (say 50000) and leave VAT at
      18. The summary should show ₪59,000 with VAT.
- [ ] Add a cost: Materials, ₪12,000. The margin drops to ₪38,000.
- [ ] Raise a variation for ₪8,000. Nothing moves yet — it says *awaiting
      decision*, because until the client says yes it changes nothing.
- [ ] **Approve** it. The revised contract becomes ₪58,000 and the margin
      ₪46,000.
- [ ] Raise an invoice. The amount is pre-filled with what is left to bill.
      Mark it paid and *Owed to you* goes to zero.
- [ ] Add a priced item to the job's materials (one with a purchase price in
      stock). It appears as **Committed** without you typing a number twice.

**Toolbox talks** — Safety → the people icon in the top bar
- [ ] The first thing on the screen is *Who needs briefing*, not the log.
      Everybody on the books is on it, marked **Never briefed**.
- [ ] **+** → topic (*Working near the excavation*), a line of notes, Create.
      It opens the register, which says an empty register records nothing.
- [ ] Sign the crew in from the chips — one tap each. Type a name for somebody
      who is not on the books (a subcontractor's lad) and add them too.
- [ ] Tap the same crew member's chip twice: they cannot be added twice, and
      the register does not claim a crew of nine when six turned up.
- [ ] Go back. The people you signed in have dropped off *Who needs briefing*.
      Anybody you did not sign in is still on it.

**Permits to work** — Safety → the clipboard icon in the top bar
- [ ] **+** → *Hot work*. Describe the job, name who is doing it, Create.
      It opens showing four precautions, none ticked, and **Issue the permit**
      is greyed out with a line saying why.
- [ ] Tick three of the four. Still greyed out. Tick the fourth: now it is live.
- [ ] Issue it for **4 hours**. The row now shows a coloured **Live** state,
      and the precautions can no longer be un-ticked — it is a signed record.
- [ ] The chip counts down on its own. In the last hour it stops saying *Live*
      and starts saying how many minutes are left, without you touching the
      screen. This is the part worth watching: leave the list open.
- [ ] Raise a second permit and issue it for 4 hours starting **Tomorrow
      07:00**. It reads *Not started yet* and does not authorise anything.
- [ ] **Sign the permit back** on the first one, with a note. It moves to
      *Signed back* and drops below the live ones.
- [ ] If you can, change the phone's clock forward past a live permit's end
      time. It must go red and say *work must stop* — and still let you sign
      it back, because the work stopping and the area being checked are two
      different events.

**Stamped photographs**
- [ ] Photograph something inside a job. The picture should come back with the
      date, time, coordinates and your name burned into a dark band along the
      bottom.
- [ ] Take one in **portrait**. The stamp must be along the bottom of an
      upright picture — if the photo comes out sideways, the rotation handling
      is wrong and that is the bug to report.
- [ ] Export the job sheet, or send the photo to yourself. The stamp travels
      with the file. That is the entire point: an emailed photo arrives as a
      picture of a wall unless the date came with it.
- [ ] Add your own photograph or a picture of your ID in Settings or the
      induction. Those are **not** stamped, deliberately.
- [ ] Somewhere with no GPS fix (a basement), the stamp should still carry the
      date and your name rather than a blank line.

**The daily log** — open a job → *Daily log*
- [ ] It opens on today with a date at the top and *Not signed*.
- [ ] The top half counts what the app already recorded. Do something on the
      job — tick a task off, book a delivery in, hold a toolbox talk — then
      come back. The count should have gone up **without you typing anything**.
- [ ] On a job where nothing has been recorded today it says so plainly rather
      than showing six zeros and leaving you to work it out.
- [ ] Fill in the weather, the headcount and how the day went. Leave the screen
      and come back: it is still there. There is no Save button on purpose.
- [ ] Sign it. Everything above goes read-only and there is no way to edit it —
      that is the point of signing, not an oversight.
- [ ] Reopen an older day from the list at the bottom. A signed one stays
      locked.

**Snags** — Safety → the checklist icon in the top bar
- [ ] **+** → describe a defect, say where it is, who has to fix it, and leave
      *Holds up handover* on. Create.
- [ ] The banner at the top says handover is still held up, and counts one
      outstanding.
- [ ] Photograph the defect under *What is wrong*. It should appear as the
      thumbnail on the list row.
- [ ] Tap **I have put this right**. The snag moves to *Says fixed — not
      checked* — and is **still counted as outstanding**. That is the whole
      point: a claim is not a completion.
- [ ] Photograph the repair under *What was done*, then **Accepted — close it**.
      Now the count drops and the banner goes green.
- [ ] Raise another, tap *I have put this right*, then **Not done — send it
      back**. It reads *Sent back* in red and can be claimed again.
- [ ] Raise one with *Holds up handover* switched **off**. It stays on the
      outstanding count but the banner still says nothing is holding up
      handover — a scuff is real work that does not stop a building.
- [ ] Give one a fix-by date in the past. It goes red and says how many days
      late. Mark it fixed but do not check it: it is **still late**.

**Tickets**
- [ ] People → tap anyone → **Add a ticket**. Pick *Work at height*, set an
      expiry a couple of weeks out.
- [ ] Their row in People goes amber and says the ticket needs attention.
- [ ] Set one to a date in the past — the row goes red and says *Expired*.
- [ ] Type `31/02/2027` as an expiry. It should refuse rather than quietly
      storing 3 March.

**The dashboard across jobs**
- [ ] Home should now show *Owed to you* and *Margin · across all jobs* — but
      only if your role reads Money. Sign in as the worker: those tiles are
      gone, and so is nothing else they need.
- [ ] Give a job a due date in the past and set it Active. An **Overdue** tile
      appears. Clear it and the tile disappears — it only shows when it means
      something.

**Signing in — no more list of everybody**
- [ ] Settings → **Sign out**. You should get a form with two fields, not a
      list of every account on the phone. That list was showing your whole
      crew to whoever picked the device up.
- [ ] Sign in with the **username** you were given. Then sign out and do it
      again with the **ID number** instead — both work, same password.
- [ ] Type a username that does not exist. The error must not tell you whether
      it was the name or the password that was wrong.
- [ ] Two people can be called Hammam. Add a second member with the same
      display name but a different username, and check both can sign in.
- [ ] An account with **no** password is opened by leaving the field blank —
      and by nothing else. Type a random password against one and it refuses.

**The safety induction — the gate before the app**
- [ ] Add a member as **On the tools**, give them a username and password, then
      sign out and sign in as them. You should land on *Before you start*, not
      on the app.
- [ ] There is no skip, no Later, and no back. The only way in is through.
- [ ] The continue button is dead until you have **scrolled to the bottom**
      *and* signed. Sign without scrolling — still dead. Scroll without
      signing — still dead.
- [ ] A single tap on the signature pad is not a signature. Draw a real stroke.
- [ ] Kill the app half way through the induction and reopen it. You come back
      to the induction, not to the inside of the app.
- [ ] The worker induction is one page: hard hat, hi-vis, boots, gloves, eyes,
      ears, stop if it is not safe, know where things are.
- [ ] Now sign out and sign in as the **owner or a manager**. Their induction
      has a second section — permits, toolbox talks, certificates, incidents,
      emergencies — because they are about to enforce it on everyone else.
- [ ] Add your photograph and a picture of your ID. Both optional: a cracked
      camera must not lock somebody out of the app they need for work.
- [ ] Sign in again afterwards. The induction does not come back — it is signed
      once, and the date is on the account.

**Accounts and roles — the new gate**
- [ ] First launch after installing this build: it asks who is using the
      device. Pick **A company**, name it, name yourself, set a 4-digit
      passcode. You land in the app as **Owner** and see all six tabs.
- [ ] Settings → *People* tab → **+**. Add someone as **Finance**, give them a
      different passcode.
- [ ] Settings → **Sign out**, then sign in as that Finance person.
- [ ] They should have **no Schedule tab** — finance sees Money everywhere and
      Plan nowhere. Open a job: the tasks section is gone, materials are there
      but read-only (no **+**, no delete), photos are visible but cannot be
      added.
- [ ] Sign back in as the owner. Try to remove yourself in People — you can't,
      and there is no button to do it. Sign out is in Settings instead.
- [ ] Wrong passcode says so and does not let you in.

**Upgrading, not reinstalling** — this is the one worth doing carefully
- [ ] Do **not** uninstall the old build first. Install this APK over it.
- [ ] It should open with your existing stock, jobs and photos intact, then ask
      who is using the device. If it crashes on launch, that is a migration
      bug — tell me and do not clear the app's data, the crash log is worth
      more than the recovery.
- [ ] The database is at version 11, so a device coming from the first build
      runs **ten** migrations back to back. CI proves each one's SQL matches
      what the app expects; only a real upgrade proves they run in order, on
      real data, on a real phone.

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
