<!--
SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
SPDX-License-Identifier: GPL-3.0-or-later
-->

# Contributing

Thanks for helping! A few rules keep UltimateCalendar free, reliable and easy to review.

## Ground rules

- **Free software only.** No Google Play Services (Google calendars are read through Android's calendar provider), Firebase, analytics, crash reporters or any non-free dependency. Before adding a dependency, open an issue: its license must be compatible with GPL-3.0-or-later.
- **No telemetry**, of any kind.
- **Every visible string in `strings.xml`**, in English (`values/`) and Spanish (`values-es/`).
- **SPDX header** in every source file: `SPDX-License-Identifier: GPL-3.0-or-later`.

## Workflow

1. One task or fix per branch (`feat/…`, `fix/…`), started from `master`. By convention changes only land through pull requests with green checks, squash-merged (GitHub does not enforce it: there is no branch ruleset).
2. The **PR title** must be a [Conventional Commit](https://www.conventionalcommits.org) (`feat: …`, `fix: …`): it becomes the squash commit.
3. `./gradlew check` must pass before you push: unit tests, detekt, ktlint, Android Lint (warnings are errors), Kover, dependency verification and the forbidden-dependency check.
4. `appVersion` and `CHANGELOG.md` change only in a release PR: see RELEASING.md.

## Code

- Kotlin, Jetpack Compose and Material 3; MVVM with `ui` / `domain` / `data` / `sync` layers and unidirectional data flow.
- Calendar data goes through `CalendarSource`; the UI never talks to the `ContentResolver` or the network.
- Write to the calendar provider as a normal client (never `CALLER_IS_SYNCADAPTER` outside tests) and never touch sync columns or other apps' extended properties.
- IO errors are typed results (`CalendarResult`), not exceptions reaching the UI.
- Room schema changes need a new version, a migration and a migration test.
- Accessibility: content descriptions, 48 dp touch targets, layouts that work at 200% font size.

## Tests

- JUnit 5, MockK, Turbine and in-memory Room for unit tests; Compose UI tests for key flows.
- **Provider harness:** `CalendarSourceContract` runs against `FakeCalendarSource` (unit tests) and against the real calendar provider on an emulator (instrumented tests, local test account). Any behaviour of a `CalendarSource` belongs in that contract. Fixtures with real (anonymised) Google and DAVx5 rows live in `app/src/test/resources/provider-fixtures/`.
- Coverage (Kover): at least 85% over `domain`, `data` and `sync`; **100%** on the invitation detector, reminder planning, missed-reminder recovery and recurrence splitting.
- A test must be able to fail for a real reason: no empty or tautological tests.
- Instrumented tests run nightly on an emulator in CI (`ui-tests.yml`); add the `ui-tests` label to a PR to run them there.
- `connectedDebugAndroidTest` uninstalls the app when it ends, data included. To keep your own data on a phone, install both APKs (`installDebug installDebugAndroidTest`) and run `adb shell am instrument -w com.qtekfun.ultimatecalendar.test/com.qtekfun.ultimatecalendar.HiltTestRunner`.

## Dependencies and versions

Dependabot keeps versions up to date; do not bump them by hand. When dependencies change, regenerate `gradle/verification-metadata.xml` from a clean Gradle home.
