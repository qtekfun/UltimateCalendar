<!--
SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
SPDX-License-Identifier: GPL-3.0-or-later

The PR title must be a Conventional Commit (feat: …, fix: …): it becomes the squash commit.
-->

## What
<!-- PLAN task (Txx) and/or RF-xx, in 1-3 lines. -->

## How it was verified
<!-- Tests added, manual test on device (which phone, which test calendar). -->

## Checklist
- [ ] `./gradlew check` passes locally
- [ ] New logic has tests that can fail for a real reason; 100% packages still at 100%
- [ ] Strings in `values/` and `values-es/`; SPDX headers on new files
- [ ] No new dependency, or its license was checked and approved
- [ ] `PLAN.md` / `SPEC.md` §9 updated if something changed
- [ ] Room schema change → new version + migration + migration test
