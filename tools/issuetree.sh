#!/usr/bin/env bash
# tools/issuetree.sh — print an issue's tree, root to leaves.
#
# Usage: tools/issuetree.sh <issue-number> [max-depth]
#
# Leaves carry a dot, parents carry their child count: the shape of the work at
# a glance (D-059). A leaf is one PR; a parent is a promise about leaves.

set -euo pipefail

REPO="${MATRIX_REPO:-gokselozgur5/matrix-sim}"

# Refusals, spelled out rather than left to bash (#1276).
#
# `${1:?usage…}` is bash's own mechanism and it leaves with 1, which is this
# tree's code for "the claim does not hold" — so a missing argument reported the
# same way a broken contract does, and the catalog row documented that as if it
# were the rule. 2 is the refusal code, ten tools deep, and the largest existing
# agreement in the grammar.
#
# The second refusal did not exist at all. A non-numeric argument was walked as
# though it were an issue number: `issuetree.sh not-a-number` printed
# `? #not-a-number <unreadable>` and left with 3 — the code for THE ANSWER
# COULD NOT BE READ. A typo and a rate-limited API produced the same verdict,
# which is #1235's own defect (an unreadable node reported as an empty one)
# arriving through the argument door of the tool that fixed it. `subissue.sh`
# has refused a non-numeric parent since it was written; this is that rule,
# applied to the tool that reads the same numbers.
# The suite, before the refusals it exercises — a `--selftest` that fell
# through to the argument checks would be refused by them (#1307).
#
# Argument handling is exactly the part that needs no token and no network,
# and it is the part #1276 changed. Both refusals were verified by hand, in a
# pull request, once; `advice.sh` prints UNFALSIFIABLE about precisely that.
#
# The WALK stays out. A fixture that fakes `gh` tests the fake — #1273's
# reasoning, one tool over — so this suite covers the door and says so rather
# than implying the whole tool is under test.
if [ "${1:-}" = "--selftest" ]; then
  pass=0
  fail=0
  case_() {                     # case_ <name> <want-code> <args...>
    local name="$1" want="$2"; shift 2
    local got=0
    bash "$0" "$@" >/dev/null 2>&1 || got=$?
    if [ "$want" = "$got" ]; then
      pass=$((pass + 1)); printf 'ISSUETREE case=%-18s want=%s got=%s OK\n' "$name" "$want" "$got"
    else
      fail=$((fail + 1)); printf 'ISSUETREE case=%-18s want=%s got=%s BROKEN\n' "$name" "$want" "$got"
    fi
  }
  case_ no-argument   2
  case_ not-a-number  2 not-a-number
  case_ bad-depth     2 1246 not-a-depth
  case_ a-flag        2 --help
  # The empty string is its own case: `case "$1" in ''|*[!0-9]*)` has two arms
  # for a reason, and a draft that dropped the first would pass every case
  # above while accepting `issuetree.sh ""` as issue number zero.
  case_ empty-string  2 ""
  echo "ISSUETREE SELFTEST VERDICT $([ "$fail" -eq 0 ] && echo PASS || echo FAIL) cases=$((pass + fail)) failed=$fail"
  [ "$fail" -eq 0 ] || exit 1
  exit 0
fi

if [ $# -lt 1 ]; then
  echo "FATAL usage: tools/issuetree.sh <issue-number> [max-depth]" >&2
  exit 2
fi
case "$1" in
  ''|*[!0-9]*) echo "FATAL not an issue number: $1" >&2; exit 2 ;;
esac
case "${2:-6}" in
  ''|*[!0-9]*) echo "FATAL not a depth: $2" >&2; exit 2 ;;
esac

ROOT="$1"
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
