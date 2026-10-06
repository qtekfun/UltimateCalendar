#!/usr/bin/env bash
# SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
# SPDX-License-Identifier: GPL-3.0-or-later
# Stop: if Kotlin/Gradle files changed since HEAD, run the fast gates before Claude finishes.
# Exit 2 sends the failure back to Claude so it fixes it. The full gate is ./gradlew check (CI).
input=$(cat)
cd "$CLAUDE_PROJECT_DIR" || exit 0
# Avoid loops: if this hook already sent Claude back once, let it stop.
if echo "$input" | grep -q '"stop_hook_active": *true'; then exit 0; fi
[[ -x ./gradlew ]] || exit 0
changed=$( { git diff --name-only HEAD; git ls-files --others --exclude-standard; } 2>/dev/null | grep -E '\.(kt|kts|xml|toml)$' )
[[ -z "$changed" ]] && exit 0
if ! out=$(./gradlew -q ktlintCheck detekt testDebugUnitTest 2>&1); then
  echo "Fast gates failed (ktlintCheck detekt testDebugUnitTest). Fix before finishing:" >&2
  echo "$out" | tail -60 >&2
  exit 2
fi
exit 0
