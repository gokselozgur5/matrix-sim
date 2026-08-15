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
  # Under `set -e` these two used to take the script down mid-walk with gh's own
  # message and no verdict line — a partial tree and a generic exit 1, which
  # reads as "the tool crashed" rather than "the answer could not be read". Same
  # repair as the sub-issue call below, on the two calls that run first.
  # `if ! x="$(...)"` and never `x="$(...)"; rc=$?` — under `set -e` a plain
  # assignment dies ON THE ASSIGNMENT and the next line never runs. That is
  # tools/README.md's capture rule, and it is why the first draft of this repair
  # printed no verdict line at all: the script was already gone.
  local vfields
  if ! vfields="$(gh issue view "$num" --repo "$REPO" --json title,state --jq '[.title, .state] | @tsv' 2>&1)"; then
    UNREADABLE=$((UNREADABLE + 1))
    printf '%s? #%s <unreadable: %s>\n' "$indent" "$num" "${vfields##*: }"
    return 0
  fi
  IFS=$'\t' read -r title state <<< "$vfields"
  # An unreadable answer is not an empty one (#1235). This call used to end
  # `2>/dev/null || true`, so a rate limit, an expired token, a network drop and
  # a genuinely childless issue all produced `count=0` and printed a leaf. The
  # tree's shape is what a crew reads to decide what to work on; a parent drawn
  # as a leaf because the API was busy is the same class of defect as #1004's
  # "no checks reported" — the unasked question wearing the empty answer's face.
  #
  # 404 is the exception and it IS an empty answer: GitHub returns it for an
  # issue with no sub-issues, which is not a failure to read.
  local err
  kids=""
  if err="$(gh api "repos/$REPO/issues/$num/sub_issues" --jq '.[].number' 2>&1)"; then
    kids="$err"
  elif ! printf '%s' "$err" | grep -q 'HTTP 404'; then
    UNREADABLE=$((UNREADABLE + 1))
    printf '%s? #%s <unreadable: %s>\n' "$indent" "$num" "${err##*: }"
    return 0
  fi
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

UNREADABLE=0
walk "$ROOT" 0
# The denominator, and the exit code that goes with it. A tree drawn over an
# answer nobody could read is not a tree; 3 is this repository's code for "the
# answer could not be read" (`checkage.sh` and `prstate.sh` spend it the same
# way), and it is separated from a clean walk rather than folded into it.
printf 'ISSUETREE VERDICT %s root=%s unreadable=%d\n' \
  "$([ "$UNREADABLE" = 0 ] && printf COMPLETE || printf PARTIAL)" "$ROOT" "$UNREADABLE"
[ "$UNREADABLE" = 0 ] || exit 3
