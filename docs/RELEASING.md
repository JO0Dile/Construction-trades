# Putting it on Google Play and the App Store

## What the stores do, and what they do not

They distribute. Google Play and the App Store host the file people download
and take the payment. **Neither runs a server for you.** There is no backend,
no database and no compute on their side. Once installed, the app runs on the
phone.

That is fine, because this app does not need a server. Everything is in a
Room database on the device and works with no signal, which is the point on a
site. You can publish to both stores today at no hosting cost.

A server becomes necessary only for the things that genuinely require one:

- More than one firm sharing a job, with the confidentiality rules enforced
  rather than displayed — today they are display rules on one device, and
  `docs/SERVER.md` says so rather than counting them as done.
- Sync between devices, and backup.
- Anchoring the head of the audit chain somewhere the holder of the phone
  does not control, which is the one thing `docs/AUDIT.md` says the chain
  cannot do alone.

None of those is needed to launch.

## Making the repository private

Worth doing, and it costs three things. None is fatal; all three are silent
if nobody looks for them first.

**1. Every phone stops getting updates.** `Settings → Check for updates` asks
`api.github.com` for the latest release with no token in the app — it cannot
carry one, because an APK is a file anybody can open and read. A private
repository answers an anonymous request with 404, which the app reports as
"no update available". Nobody sees an error; the updates just stop.

**2. The download link stops working** for anyone you send it to who is not
on the repository.

**3. The privacy policy URL 404s**, and Play's listing field wants one that
resolves for anybody.

### The way round all three

Keep **two** repositories:

| | holds | visibility |
|---|---|---|
| `Construction-trades` | the source, the catalogue, the docs | **private** |
| a second repo, e.g. `trades-releases` | the published APKs and `PRIVACY.md`, nothing else | public |

Then point the app at the public one — two lines, no Kotlin:

```kotlin
// android/app/build.gradle.kts
"\"https://api.github.com/repos/<owner>/trades-releases/releases/latest\"",
"\"https://github.com/<owner>/trades-releases/releases/latest\"",
```

and change `release.yml` to publish there. A binary somebody can install is
already a binary they hold; publishing it publicly gives away nothing the
source does not, and the source is what is worth protecting.

The alternative — one private repository and updates by hand — works, but
"four different versions on one site" is exactly the problem the update check
was written to stop.

### What private does not protect

An APK is not a secret. Anybody holding one can decompile it, and R8 renames
symbols without hiding logic. Private stops casual copying of the source and
makes theft provable; it is not a technical barrier. The barrier is the
licence, the catalogue nobody else has written, and being first.

## The upload key

Google Play signs the app people install with a key it holds. You sign the
upload with your own key, and **if you lose it you cannot ship an update to
the same listing.** Back it up somewhere that is not this repository and not
the laptop that made it.

Make one:

```
keytool -genkeypair -v -keystore upload.jks -keyalg RSA -keysize 4096 \
        -validity 10000 -alias upload
```

Then tell Gradle where it is, in `~/.gradle/gradle.properties` — outside the
repository, so it is never committed:

```
TM_KEYSTORE=/absolute/path/to/upload.jks
TM_KEYSTORE_PASSWORD=...
TM_KEY_ALIAS=upload
TM_KEY_PASSWORD=...
```

With those set, `./gradlew :app:bundleRelease` produces a signed
`app/build/outputs/bundle/release/app-release.aab` to upload.

Without them it still builds, unsigned. That is deliberate: CI builds the
release bundle on every push so that R8 stripping something Room, SQLCipher or
kotlinx.serialization loads by name fails on the push that caused it rather
than on the day of a submission. Signing is the last step before upload, not a
prerequisite for knowing the build works.

**The committed `debug.keystore` is not this key** and must never be used for
a store release. It exists so the direct-download builds can update each
other; its password is the well-known Android debug one and it protects
nothing.

## What Play asks for that is not code

- **App Bundle**, not APK. `bundleRelease`, which CI now builds.
- **Data safety form.** The app collects ID numbers, photographs and
  signatures and keeps them on the device. `legal/` holds the privacy notice
  the form has to agree with.
- **Target API level.** Currently 35. Play raises the floor each year.
- **No self-update.** The release build sets `SELF_UPDATE=false`: an app that
  installs its own APK is Device and Network Abuse under Play policy, and is
  not permitted on iOS at all. Only the sideloaded build checks for updates.

## What is not ready

Nothing here has run on a physical phone yet, and no release build has been
installed anywhere — CI proves it compiles and survives minification, which
is not the same as proving it runs. Do that before submitting.
