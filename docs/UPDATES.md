# Shipping an update to people who already have the app

The app is passed around as a downloaded APK long before it is on a store.
Telling everyone to go to the repository page each time is how a site ends up
running four different versions, so the app checks for its own updates:
**Settings → App updates → Check for updates**.

## Publishing a version

```bash
# Bump versionCode and versionName in android/app/build.gradle.kts first.
git tag v0.3.0
git push origin v0.3.0
```

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

## Three things that will bite

**The repository has to be public.** The app carries no GitHub token, on
purpose: anything shipped inside an APK can be pulled back out of it. An
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
