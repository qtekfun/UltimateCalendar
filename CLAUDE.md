# UltimateCalendar — instructions for Claude Code

<!-- SPDX-FileCopyrightText: 2026 UltimateCalendar contributors -->
<!-- SPDX-License-Identifier: GPL-3.0-or-later -->

Android calendar with the **Google Calendar** interface and the invitations of **Apple Calendar**, on top of the Android calendar provider (Google, DAVx5…) and, later, its own CalDAV. Offline, free software (GPLv3), final destination F-Droid. Sister of [UltimateTasks](https://github.com/qtekfun/UltimateTasks) and [UltimateDeck](https://github.com/qtekfun/UltimateDeck): their common pieces are copied and adapted.

Always read `SPEC.md` (what to build) and `PLAN.md` (in what order) before starting. If anything in this file contradicts the spec, stop and ask.

## Project identity
- Name: **UltimateCalendar**
- `applicationId`: `com.qtekfun.ultimatecalendar`
- Repository: `github.com/qtekfun/UltimateCalendar`, main branch **`master`** (by convention it is only entered through a PR with CI green; GitHub does not enforce it, there is no ruleset)
- License: **GPL-3.0-or-later** (SPDX header in every source file, also yml, toml, kts, manifest and md)
- UI languages: English (default) and Spanish. **No visible string is hardcoded**: everything in `strings.xml` (`values/` and `values-es/`).

## Stack (do not change without asking)
- Kotlin, Jetpack Compose, Material 3 (dynamic colors + dark mode + AMOLED)
- `minSdk` 26, `targetSdk` the latest stable. Raise `minSdk` only if something blocks it, and note it in `SPEC.md`.
- Architecture: MVVM + `ui` / `domain` / `data` / `sync` layers, unidirectional data flow (StateFlow)
- Calendar sources behind `CalendarSource` (`data/source/`): `ProviderCalendarSource` (CalendarContract) and, in phase 6, `CalDavCalendarSource`. The UI and `domain` never use `ContentResolver` or the network directly.
- Injection: Hilt · Own persistence: Room · Background: WorkManager · Reminders: AlarmManager
- Gradle with Kotlin DSL and version catalog (`gradle/libs.versions.toml`), same versions as UltimateTasks at the start (JDK 21 for Gradle, bytecode 17).

## Reuse from UltimateTasks
- They are **copied and adapted** (not shared as a library): Gradle and quality configuration, theme, reliability wizard, reminder scheduler, recovery of missed reminders, heartbeat, robust mode, `BootReceiver`, recurrence editor, settings, encrypted backup, Screenshots, release and F-Droid recipe; in phase 6, CalDAV client, iCalendar, Login Flow v2, queue and resolver.
- When copying, their tests are copied too. Package, names and strings are adapted; no dead code specific to tasks.

## Free-software rules (F-Droid) — non-negotiable
- **Forbidden**: Firebase, Google Play Services (also for reading Google Calendar: it is read from the Android provider), Crashlytics, analytics, proprietary SDKs, any non-free dependency.
- **Forbidden**: telemetry of any kind.
- Before adding a dependency: check its license (compatible with GPLv3) and **ask the user**.
- Publication metadata in fastlane format: `fastlane/metadata/android/{en-US,es-ES}/`.
- Reproducible builds: no timestamps or non-deterministic values in the build.

## Calendar provider: rules
- Write as a normal client, **never** with `CALLER_IS_SYNCADAPTER` outside of tests.
- Do not touch sync columns (`_SYNC_ID`, `SYNC_DATA*`, `CAL_SYNC*`) or extended properties of other apps.
- Read ranges with `Instances`; never expand recurrences in the UI.
- On the author's phone, test only with **test calendars created for that purpose**; do not create, edit or respond to real events.

## Commands
- Debug build: `./gradlew assembleDebug`
- Unit tests: `./gradlew testDebugUnitTest`
- UI tests on the phone (without uninstalling the app): `./gradlew installDebug installDebugAndroidTest` and `adb shell am instrument -w com.qtekfun.ultimatecalendar.test/com.qtekfun.ultimatecalendar.HiltTestRunner`. **Never** `connectedDebugAndroidTest` on the author's phone: it uninstalls the app and deletes its data.
- Lint and style: `./gradlew detekt ktlintCheck lintDebug`
- Coverage: `./gradlew koverVerify koverHtmlReport`
- All of the above (what CI runs): `./gradlew check`

## Quality and tests
- Every task ends with `./gradlew check` green. Do not mark a task as done if it fails.
- Test stack: JUnit5 + MockK, Turbine (flows), in-memory Room, MockWebServer (phase 6), Compose UI tests only for key flows.
- **Provider harness**: every `CalendarSource` feature is tested in the contract suite `CalendarSourceContract`, which runs against `FakeCalendarSource` (unit) and against the emulator's real provider (instrumented, local test account). If the fake and the real provider do not behave the same, the fake is wrong: fix it. Fixtures of real Google and DAVx5 rows in `app/src/test/resources/provider-fixtures/`, anonymized.
- **Coverage (Kover):**
  - Minimum global threshold **85 %** over `domain`, `data` and `sync`.
  - **100 % mandatory** in: invitation detector, reminder scheduler, recovery of missed reminders, recurrence splitting and, in phase 6, queue, resolver and recurrence expansion.
  - Excluded from measurement: generated code (Hilt, Room), `@Preview`, pure Compose UI.
  - **Never write empty or tautological tests** to raise the number. A test must be able to fail for a real reason.
- Dates with `java.time` and an injectable `Clock`; never `System.currentTimeMillis()` directly in testable logic. Time zone and daylight saving time change tests for everything that schedules.
- Kotlin and Lint warnings treated as errors.

## Workflow
- Work on **one task from `PLAN.md` at a time**, on a branch `feat/<task>` (or `fix/…`) from `master`.
- Start in plan mode: propose the approach and wait for confirmation before touching code.
- Commits following **Conventional Commits** (`feat:`, `fix:`, `perf:`, `test:`, `refactor:`, `docs:`, `build:`, `ci:`, `chore:`), small and atomic. **The PR title too**, because it is merged with squash and that is the commit that stays in `master`.
- Do not `git push --force`, do not rewrite shared history, do not commit or push to `master`. The hooks in `.claude/` block it; do not try to get around them.
- Open the PR with `gh pr create` filling in the template; wait for CI and fix whatever fails. When all checks pass, you merge with `gh pr merge --squash` (user's decision, 2026-10-06); if anything fails, do not merge.
- When finishing each task: summarize in 2-3 lines what was done and what remains; mark the task in `PLAN.md` (with `*Result:*` if something changed from the plan) and record decisions in `SPEC.md` §9 with date and task.
- If the spec is ambiguous or information is missing: **ask**, do not invent.

## Versions and releases
- Releases are manual, as in UltimateDeck (see `RELEASING.md`): `appVersion` and `CHANGELOG.md` only change in a release PR (`chore: release X.Y.Z`, branch `release/X.Y.Z`), which also carries the store texts `fastlane/metadata/android/{en-US,es-ES}/changelogs/<versionCode>.txt` (≤ 500 characters; CI requires them on those branches). In all other PRs `appVersion` is not touched.
- The `vX.Y.Z` tag is created and pushed by the author, or by me when the author expressly asks for it; the `Release` workflow builds and publishes the signed APK.

## Code conventions
- One file per relevant public class; packages by feature within each layer.
- No business logic in composables or in heavy ViewModels: it goes in `domain` (invitations, reminders, recurrences, layout of overlapping events).
- Immutability by default (`val`, `data class`, immutable collections).
- IO errors modeled with sealed types (`CalendarResult`), not with loose exceptions towards the UI.
- All access to the provider, Room and the network off the main thread (injectable Dispatchers).
- Secrets (phase 6) are stored encrypted with Android Keystore; never in logs or in plain text. No event titles or emails in logs.
- Accessibility: `contentDescription`, minimum touch targets of 48 dp, large font support.
- Room: schema change = new version + migration + migration test.

## What NOT to do
- Do not implement anything marked as "Out of scope" in `SPEC.md`.
- Do not change dependency versions manually: Dependabot manages it.
- Do not disable or relax detekt, ktlint, Lint, Kover, dependency verification, or the workflows to make CI pass.
- Do not use screenshots with real data for the store or the README: they come from the `Screenshots` test with made-up data.
