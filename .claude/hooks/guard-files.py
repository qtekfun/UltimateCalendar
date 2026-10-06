#!/usr/bin/env python3
# SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
# SPDX-License-Identifier: GPL-3.0-or-later
"""PreToolUse(Edit|Write): protects files that CI, release-please or Dependabot own.

- deny: files owned by automation (versions, CHANGELOG, checksums).
- ask:  quality gates and CI; the user must approve any change, never to make CI pass.
"""
import json
import os
import re
import sys

data = json.load(sys.stdin)
path = data.get("tool_input", {}).get("file_path", "")
root = os.environ.get("CLAUDE_PROJECT_DIR", os.getcwd())
rel = os.path.relpath(os.path.abspath(path), root) if path else ""

DENY = {
    "CHANGELOG.md": "release-please writes CHANGELOG.md.",
    ".release-please-manifest.json": "release-please owns the manifest.",
    "gradle/verification-metadata.xml": "regenerate it with --write-verification-metadata, never by hand.",
    "gradle/libs.versions.toml": None,  # versions only; see below
    "gradle.properties": None,  # appVersion only; see below
}
ASK = [
    r"^config/detekt/",
    r"^\.github/",
    r"^\.claude/",
    r"^release-please-config\.json$",
    r"^app/build\.gradle\.kts$",
    r"^build\.gradle\.kts$",
    r"^fdroid/",
]


def decide(decision: str, reason: str) -> None:
    print(json.dumps({"hookSpecificOutput": {
        "hookEventName": "PreToolUse",
        "permissionDecision": decision,
        "permissionDecisionReason": reason,
    }}))
    sys.exit(0)


new_text = json.dumps(data.get("tool_input", {}))

if rel in DENY and DENY[rel]:
    decide("deny", DENY[rel])
if rel == "gradle.properties" and "appVersion" in new_text:
    decide("deny", "appVersion is bumped by release-please.")
if rel == "gradle/libs.versions.toml" and re.search(r'=\s*\\?"\d', new_text):
    decide("ask", "Changing a version or adding a dependency: Dependabot bumps versions, "
                  "and new dependencies need a license check and the user's approval.")
for pattern in ASK:
    if re.search(pattern, rel):
        decide("ask", f"{rel} is a quality/CI/release file: only change it for the task at hand, "
                      "never to relax a gate.")
sys.exit(0)
