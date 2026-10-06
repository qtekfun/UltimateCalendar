#!/usr/bin/env bash
# SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
# SPDX-License-Identifier: GPL-3.0-or-later
#
# Takes the store and README screenshots (the Screenshots test: invented calendars in a LOCAL
# test account, fixed clock) on an EMULATOR and copies the PNGs to
# <dest>/<locale>/images/<phoneScreenshots|tenInchScreenshots>/. The default destination is
# fastlane/metadata/android. It refuses to run on a physical device: the test fills the
# calendar provider with made-up events and must never touch a real phone.
#
# Usage: tools/take-screenshots.sh [phone|tablet] [dest]
set -euo pipefail

cd "$(dirname "$0")/.."
kind=${1:-phone}
dest=${2:-fastlane/metadata/android}
package=com.qtekfun.ultimatecalendar
case "$kind" in
  phone) method=phoneScreenshots ;;
  tablet) method=tenInchScreenshots ;;
  *) echo "usage: $0 [phone|tablet] [dest]" >&2; exit 2 ;;
esac

qemu=$(adb shell getprop ro.kernel.qemu | tr -d '\r')
[[ -n "$qemu" ]] || qemu=$(adb shell getprop ro.boot.qemu | tr -d '\r')
if [[ "$qemu" != "1" ]]; then
  echo "Not an emulator: refusing to run the screenshots test here." >&2
  exit 1
fi

./gradlew installDebug installDebugAndroidTest
out=$(adb shell am instrument -w -e screenshots true \
  -e class "$package.Screenshots#$method" \
  "$package.test/$package.HiltTestRunner" | tr -d '\r')
echo "$out"

# Whatever was taken is copied, even when the test failed, to see how far it got.
mkdir -p "$dest"
adb pull "/sdcard/Android/data/$package/files/screenshots/." "$dest" || true

# `am instrument` exits 0 even when a test fails: look for the verdict.
if ! grep -q '^OK (' <<<"$out"; then
  echo "::error::the Screenshots test did not pass (or was skipped)" >&2
  exit 1
fi
echo "Screenshots are in $dest"
