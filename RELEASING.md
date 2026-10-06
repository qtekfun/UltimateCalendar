<!--
SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
SPDX-License-Identifier: GPL-3.0-or-later
-->

# Releasing

Releases are automatic with [release-please](https://github.com/googleapis/release-please). Nobody bumps versions, edits `CHANGELOG.md` or creates tags by hand.

## Versions

- The version lives in `appVersion` in `gradle.properties` (SemVer, or `X.Y.Z-rc.N` for a release candidate), between `x-release-please-start-version` / `x-release-please-end` markers. release-please updates it together with `.release-please-manifest.json` and `CHANGELOG.md`.
- The next version comes from the Conventional Commits merged into `master` (PRs are squash-merged, so it is the **PR title**, validated by `pr-title.yml`):
  - `feat:` → minor (before 1.0.0, also minor), `fix:` / `perf:` → patch, `feat!:` or a `BREAKING CHANGE:` footer → major (minor before 1.0.0).
  - `docs`, `test`, `build`, `ci`, `chore`, `refactor` do not create a release and are hidden from the changelog.
  - To force a version (e.g. a release candidate), merge a commit with the footer `Release-As: 1.0.0-rc.1`.
- The Android version code is derived from `appVersion`, never set by hand: `(MAJOR*10000 + MINOR*100 + PATCH) * 100 + N`, with `N = 99` for a final release. `1.0.0-rc.1` → `1000001`, `1.0.0` → `1000099`: a final always sorts after its release candidates, and nothing depends on dates or the machine (reproducible builds).

## Signing (one time)

Releases are signed with the project's own key, and builds are reproducible: F-Droid builds the same source, checks that its APK matches the one published on GitHub and then ships ours, so users can update from either.

1. Create the key and keep the file and passwords somewhere safe and **backed up** (if it is lost, users must uninstall to update):
   ```sh
   keytool -genkeypair -v -keystore ultimatecalendar-release.jks -alias ultimatecalendar \
     -keyalg RSA -keysize 4096 -validity 10000
   ```
2. Repository secrets (Settings → Secrets and variables → Actions):
   - `UC_KEYSTORE_BASE64`: `base64 -w0 ultimatecalendar-release.jks`
   - `UC_KEYSTORE_PASSWORD`, `UC_KEY_ALIAS` (`ultimatecalendar`), `UC_KEY_PASSWORD`
   - `RELEASE_PLEASE_TOKEN`: a fine-grained personal access token (or GitHub App token) for this repository only, with *Contents* and *Pull requests* read/write. With the default `GITHUB_TOKEN` the release PR would not trigger CI and could never be merged under the ruleset.
3. For F-Droid, the certificate fingerprint (`AllowedAPKSigningKeys`):
   ```sh
   keytool -list -v -keystore ultimatecalendar-release.jks -alias ultimatecalendar | grep SHA256
   ```

Without the `UC_*` variables, `./gradlew assembleRelease` builds an unsigned APK, which is what F-Droid does before comparing.

## How a release happens

1. Every push to `master` runs **Release** (`release-please.yml`), which opens or updates the PR `chore: release X.Y.Z` with the new `appVersion`, manifest and `CHANGELOG.md`.
2. On that PR, add the store summaries for its version code (CI's `store-texts` check fails until they exist):
   `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt` and `es-ES/changelogs/<versionCode>.txt`, at most 500 characters, written for users.
3. Merge the PR when you want to release. release-please tags `vX.Y.Z` and creates the GitHub Release with the changelog; the same workflow then checks the tag against `appVersion`, runs `./gradlew check`, builds the signed APK and attaches `UltimateCalendar-X.Y.Z.apk` and its SHA-256. `-rc.N` versions are marked as pre-releases.
4. F-Droid picks up final tags by itself (`UpdateCheckMode: Tags`, final versions only).

## Screenshots and store assets

`fastlane/.../images/` come from the `Screenshots` UI test with made-up calendars (PLAN T29), never from real data. The icon and feature graphic are produced in T28/T30.

## F-Droid

`fdroid/com.qtekfun.ultimatecalendar.yml` is the metadata to submit to [fdroiddata](https://gitlab.com/fdroid/fdroiddata) after 1.0.0 (PLAN T32). It has no comments because fdroiddata's tools remove them. Before submitting, fill in `commit` (full hash of the release commit) and `AllowedAPKSigningKeys`.
