#!/usr/bin/env bash
# SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
# SPDX-License-Identifier: GPL-3.0-or-later
#
# Creates or updates the "master" ruleset from .github/rulesets/master.json.
# Needs `gh auth login` as a repository admin. Run once after creating the repo (PLAN T01)
# and again whenever the JSON changes.
set -euo pipefail

cd "$(dirname "$0")/.."
repo=${1:-qtekfun/UltimateCalendar}
id=$(gh api "repos/$repo/rulesets" --jq '.[] | select(.name == "master") | .id')
if [[ -n "$id" ]]; then
  gh api -X PUT "repos/$repo/rulesets/$id" --input .github/rulesets/master.json >/dev/null
  echo "Updated ruleset $id on $repo"
else
  gh api -X POST "repos/$repo/rulesets" --input .github/rulesets/master.json >/dev/null
  echo "Created ruleset on $repo"
fi
# Squash commits take the PR title, which pr-title.yml validates for release-please.
gh api -X PATCH "repos/$repo" -f squash_merge_commit_title=PR_TITLE -f squash_merge_commit_message=PR_BODY \
  -F allow_merge_commit=false -F allow_rebase_merge=false -F allow_squash_merge=true \
  -F delete_branch_on_merge=true >/dev/null
echo "Squash-only merges with the PR title as commit message"
