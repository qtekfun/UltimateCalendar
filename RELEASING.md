<!--
SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
SPDX-License-Identifier: GPL-3.0-or-later
-->

# Releasing

Releases are manual and tag-driven, the same way as UltimateDeck: nothing opens release PRs for you and no extra token is needed.

## Versions

- The version lives in one place: `appVersion` in `gradle.properties`, as SemVer (`1.2.3`), or `1.2.3-rc.N` for a release candidate.
- The Android version code is derived from it, never set by hand: `(MAJOR*10000 + MINOR*100 + PATCH) * 100 + N`, with `N = 99` for a final release. So `1.0.0-rc.1` is `1000001` and `1.0.0` is `1000099`: a final version always sorts after its release candidates, and nothing depends on dates or the machine (reproducible builds).
- Before 1.0.0 the app is `0.x`.

## Signing (one time)

Releases are signed with the project's own key, and the builds are reproducible: F-Droid builds the same source, checks that its APK matches the one published on GitHub and then ships ours, so users can update from either.

1. Create the key and keep the file and passwords somewhere safe and **backed up** (if it is lost, users must uninstall to update):
   ```sh
   keytool -genkeypair -v -keystore ultimatecalendar-release.jks -alias ultimatecalendar \
     -keyalg RSA -keysize 4096 -validity 10000
   ```
2. Repository secrets (Settings → Secrets and variables → Actions):
   - `UC_KEYSTORE_BASE64`: `base64 -w0 ultimatecalendar-release.jks`
   - `UC_KEYSTORE_PASSWORD`, `UC_KEY_ALIAS` (`ultimatecalendar`), `UC_KEY_PASSWORD`
3. For F-Droid, the certificate fingerprint (`AllowedAPKSigningKeys`):
   ```sh
   keytool -list -v -keystore ultimatecalendar-release.jks -alias ultimatecalendar | grep SHA256
   ```

Without the `UC_*` variables, `./gradlew assembleRelease` builds an unsigned APK, which is what F-Droid does before comparing.

## Making a release

1. Create the branch `release/X.Y.Z` from `master`.
2. Move the `[Unreleased]` notes in `CHANGELOG.md` under `## [X.Y.Z] - YYYY-MM-DD`.
3. Set `appVersion=X.Y.Z` in `gradle.properties`.
4. Add the store summaries for the new version code, written for users and at most 500 characters each: `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt` and `es-ES/changelogs/<versionCode>.txt`. CI's `store-texts` check requires them on `release/*` branches.
5. Open the PR `chore: release X.Y.Z` and merge it (squash) once the checks pass.
6. Tag the merge commit and push the tag (only the author does this):
   ```sh
   git switch master && git pull
   git tag vX.Y.Z && git push origin vX.Y.Z
   ```
7. The **Release** workflow checks that the tag matches `appVersion`, runs `./gradlew check`, builds the signed APK and publishes a GitHub Release with the notes of that version, the APK `UltimateCalendar-X.Y.Z.apk` and its SHA-256. Release candidates (`-rc.N`) are marked as pre-releases.
8. F-Droid picks up final tags by itself (`UpdateCheckMode: Tags`, final versions only).

## Screenshots and store assets

`fastlane/.../images/` come from the `Screenshots` UI test with made-up calendars (PLAN T29), never from real data.

## F-Droid

`fdroid/com.qtekfun.ultimatecalendar.yml` is the metadata to submit to [fdroiddata](https://gitlab.com/fdroid/fdroiddata) after 1.0.0 (PLAN T32). It has no comments because fdroiddata's tools remove them. Before submitting, fill in `commit` (full hash of the release commit); `AllowedAPKSigningKeys` already has the fingerprint of the release key.
