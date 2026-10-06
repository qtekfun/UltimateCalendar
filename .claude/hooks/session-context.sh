#!/usr/bin/env bash
# SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
# SPDX-License-Identifier: GPL-3.0-or-later
# SessionStart: tells Claude where it is (branch, next PLAN task). Stdout becomes context.
cd "$CLAUDE_PROJECT_DIR" || exit 0
branch=$(git branch --show-current 2>/dev/null)
next=$(grep -m1 -E '^- \[ \] \*\*T[0-9]+' PLAN.md 2>/dev/null | sed -E 's/^- \[ \] \*\*([^*]+)\*\*.*/\1/')
echo "Branch: ${branch:-unknown}. Next open PLAN task: ${next:-none}."
if [[ "$branch" == "master" ]]; then
  echo "You are on master: create feat/<task> before changing anything (master is protected)."
fi
