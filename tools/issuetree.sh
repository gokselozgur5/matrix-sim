#!/usr/bin/env bash
# tools/issuetree.sh — print an issue's tree, root to leaves.
#
# Usage: tools/issuetree.sh <issue-number> [max-depth]
#
# Leaves carry a dot, parents carry their child count: the shape of the work at
# a glance (D-059). A leaf is one PR; a parent is a promise about leaves.

set -euo pipefail

REPO="${MATRIX_REPO:-gokselozgur5/matrix-sim}"
ROOT="${1:?usage: tools/issuetree.sh <issue-number> [max-depth]}"
MAXDEPTH="${2:-6}"

walk() {
  local num="$1" depth="$2"
  local indent title state kids count mark suffix
  indent="$(printf '%*s' $((depth * 2)) '')"
  title="$(gh issue view "$num" --repo "$REPO" --json title --jq .title)"
  state="$(gh issue view "$num" --repo "$REPO" --json state --jq .state)"
  kids="$(gh api "repos/$REPO/issues/$num/sub_issues" --jq '.[].number' 2>/dev/null || true)"
  count=0
  if [[ -n "$kids" ]]; then
    count="$(printf '%s\n' "$kids" | grep -c .)"
  fi
  mark="."
  (( count > 0 )) && mark="[$count]"
  suffix=""
  [[ "$state" == "CLOSED" ]] && suffix=" (closed)"
  printf '%s%s #%s %s%s\n' "$indent" "$mark" "$num" "$title" "$suffix"
  (( depth + 1 > MAXDEPTH )) && return 0
  local k
  for k in $kids; do
    walk "$k" $((depth + 1))
  done
}

walk "$ROOT" 0
