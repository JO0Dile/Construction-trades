# Shipping an update to people who already have the app

The app is passed around as a downloaded APK long before it is on a store.
Telling everyone to go to the repository page each time is how a site ends up
running four different versions, so the app checks for its own updates:
**Settings → App updates → Check for updates**.

## Publishing a version

Bump `versionCode` and `versionName` in `android/app/build.gradle.kts` first,
then either:

**From a phone, or anywhere with a browser.** Actions → **Release** → *Run
workflow* → type the version → go. No terminal, no clone. This is the route
worth knowing, because the person who needs to ship a fix is usually the one
holding a phone on a site.

**From a terminal.**

```bash
git tag v0.3.0
git push origin v0.3.0
```

Both end up in the same place: the workflow creates the tag if it does not
exist, builds the APKs and attaches them to a release named after the version.

`.github/workflows/release.yml` builds the APKs and attaches them to a GitHub
release named after the tag. Installed copies compare their own version against
that tag, so `v0.3.0` is offered to anyone on `0.2.0` and to nobody on `0.3.0`.

Bump both numbers in `android/app/build.gradle.kts`:

| Field         | What it is                                    |
| ------------- | --------------------------------------------- |
| `versionCode` | An integer Android compares. Must go **up**.   |
| `versionName` | What people see, and what the tag must match.  |

A tag that does not parse as a version (`latest`, `test`) is ignored by the
check rather than offered to everyone — see `UpdateVersionTest`.

## Saying what the update does

Every version gets an entry at the top of
[`shared/assets/releases/notes.json`](../shared/assets/releases/notes.json):
a few short points, each written in English, Hebrew and Arabic, in the words
somebody on a site would use. **The build fails until it is there** —
`tools/release-notes.py --check` compares it with `versionName` — so an
update nobody described cannot be shipped.

The one file does three jobs:

- **Before installing.** The release carries a copy (`release-notes.json`).
  A phone offered the update lists every version between the one it has and
  the one on offer, newest first, in its own language. Somebody going from
  0.17.0 to 0.20.0 sees 0.18, 0.19 and 0.20, not only the last.
- **After installing.** The first time the app opens on a new version it
  shows *What's new* — again everything since the version that phone last
  showed — and then not again. Nothing is shown on a first install.
- **Any time.** Settings → About → *What's new* lists the whole history.

The release page on GitHub is written from the same entry, in all three
languages, by the release workflow.

## "Could not check for updates"

Two different things used to say this, and only one of them was true.

GitHub answers **404** for a repository that has published no releases at all,
which at the HTTP level is indistinguishable from a repository it will not
show us. The app treated both as a failure and told people to check their
signal — so a perfectly working phone on full 4G was told to find better
reception, when the real answer was "nobody has published a version yet".

Those are now separate. No releases published says so; a genuine network
failure still says try again with a signal. If you see the first one, the fix
is to publish a release, not to move nearer a window.

## Three things that will bite

**The repository has to be public** — it currently is, so the check works as
shipped. Keep it that way or the button stops working. The app carries no
GitHub token, on purpose: anything shipped inside an APK can be pulled back out of it. An
anonymous request to a private repository's release feed gets a 404, so the
check reports "could not check for updates" and nothing else happens. If the
repository stays private, the alternatives are to host the APK and a small JSON
file somewhere public (change `RELEASES_API` in `build.gradle.kts`) or to
distribute through Firebase App Distribution or Play internal testing.

**The signing key must never change.** Android refuses to install an APK over
one signed with a different key — the user has to uninstall first, which wipes
their data. `android/app/debug.keystore` is committed for exactly this reason:
Gradle's generated debug key differs per machine, so two CI runners would
produce two mutually uninstallable APKs. That committed key protects nothing
(its password is the well-known `android`), and it is **not** the key a Play or
App Store release is signed with. When you do publish to a store, generate a
real upload key, keep it out of the repository, and never lose it.

**This cannot ship to the stores.** A Google Play build that downloads and
installs its own APK violates the Device and Network Abuse policy, and iOS does
not permit it at any level. So there are two builds, and the difference is not
cosmetic:

| Build                            | Self-update | `REQUEST_INSTALL_PACKAGES` |
| -------------------------------- | ----------- | -------------------------- |
| `assembleDebug` — the sideloaded one | yes     | declared, in `src/debug/`  |
| `assembleRelease` — the store one    | no      | not declared at all        |

`BuildConfig.SELF_UPDATE` gates the whole Settings section and every line of
download code, and the permission lives in `src/debug/AndroidManifest.xml`, so
a store build has nothing to review and nothing to justify. If you ever want a
minified, release-signed build that *does* self-update, give it its own build
type with both switches turned back on — do not turn them on in `release`.

## What the user sees

1. **Check for updates** — one request to the release feed.
2. If there is a newer version: its name, its release notes, and a warning that
   Android is about to ask permission.
3. **Download and install** — the APK lands in the app's cache.
4. **Install** — Android's own installer opens and asks the user to confirm.
   Nothing is ever installed silently. On Android 8 and up the "install unknown
   apps" permission is granted per app, so first time round the button sends
   them to that settings screen instead.
