#!/usr/bin/env bash
# SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
# SPDX-License-Identifier: GPL-3.0-or-later
#
# CI only: runs the instrumented tests on the emulator the workflow started and always leaves the
# emulator's log in build/logcat/ (the app, the system services it depends on and crashes), so a
# failure can be explained afterwards. With a number as first argument it runs only
# InvitationChainTest that many times in one emulator session, to measure a flaky chain.
# Never run this on a personal phone.
set -uo pipefail

cd "$(dirname "$0")/.."
repeat="${1:-0}"
extra=("${@:2}")
out=build/logcat
mkdir -p "$out"

adb logcat -G 16M || true
adb logcat -c || true

status=0
if [ "$repeat" -gt 0 ]; then
  ./gradlew installDebug installDebugAndroidTest || exit 1
  passed=0
  failed=0
  for i in $(seq 1 "$repeat"); do
    if adb shell am instrument -w -e class \
      com.qtekfun.ultimatecalendar.chains.InvitationChainTest \
      com.qtekfun.ultimatecalendar.test/com.qtekfun.ultimatecalendar.HiltTestRunner \
      | tee "$out/run-$i.txt" | grep -q '^OK ('; then
      passed=$((passed + 1))
      echo "CHAIN-RUN $i: pass"
    else
      failed=$((failed + 1))
      echo "CHAIN-RUN $i: FAIL"
    fi
  done
  echo "CHAIN-RESULT passed=$passed failed=$failed of $repeat"
  [ "$failed" -eq 0 ] || status=1
else
  ./gradlew connectedDebugAndroidTest "${extra[@]}" || status=$?
fi

adb logcat -d -v threadtime > "$out/full.txt" || true
grep -E 'ultimatecalendar|UC-DIAG|NotificationService|NotificationManager|ActivityManager|CalendarProvider|WM-|AndroidRuntime|FATAL|ANR' \
  "$out/full.txt" > "$out/relevant.txt" || true
rm -f "$out/full.txt"
exit "$status"
