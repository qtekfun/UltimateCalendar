#!/usr/bin/env python3
# SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
# SPDX-License-Identifier: GPL-3.0-or-later
"""PreToolUse(Bash): blocks commands that break the rules in CLAUDE.md.

Exit code 2 blocks the tool call and shows stderr to Claude.
"""
import json
import re
import subprocess
import sys

cmd = json.load(sys.stdin).get("tool_input", {}).get("command", "")


def block(reason: str) -> None:
    print(f"Blocked by .claude/hooks/guard-bash.py: {reason}", file=sys.stderr)
    sys.exit(2)


def branch() -> str:
    try:
        return subprocess.run(
            ["git", "branch", "--show-current"], capture_output=True, text=True, check=False
        ).stdout.strip()
    except OSError:
        return ""


if re.search(r"\bgit\s+push\b.*(\s--force(-with-lease)?\b|\s-f\b)", cmd):
    block("no force-push (CLAUDE.md, Flujo de trabajo).")
if re.search(r"\bgit\s+push\b.*\b(origin\s+)?(HEAD:)?master\b", cmd):
    block("never push to master; open a PR from feat/<task>.")
if re.search(r"\bgit\s+commit\b", cmd) and branch() == "master":
    block("you are on master; create a feat/<task> branch first.")
if re.search(r"--no-verify\b", cmd):
    block("do not skip git hooks.")
if re.search(r"\bgit\s+(tag|rebase\s+-i|reset\s+--hard\s+origin)", cmd):
    block("tags come from release-please; no history rewriting.")
if re.search(r"\bgradlew\b.*\s-x\s+\S*(check|test|detekt|ktlint|lint|kover)", cmd, re.I):
    block("do not exclude quality tasks; `./gradlew check` must pass as is.")
if re.search(r"\bgradlew\b.*\bconnected\w*AndroidTest\b", cmd):
    block("connected*AndroidTest uninstalls the app and wipes data on the author's phone; "
          "use installDebug installDebugAndroidTest + adb shell am instrument (CLAUDE.md, Comandos). "
          "CI runs it on an emulator.")
if re.search(r"\bgh\s+api\b.*rulesets", cmd):
    block("the user manages rulesets.")
if re.search(r"\bgh\s+release\b", cmd):
    block("releases are created by release-please in CI.")
sys.exit(0)
