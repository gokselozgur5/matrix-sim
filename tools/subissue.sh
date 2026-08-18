#!/usr/bin/env bash
# tools/subissue.sh — cut a child issue and hang it on its parent, in one motion.
#
# Usage: tools/subissue.sh <parent-number> "<title>" <body-file> [--label L] [--milestone M]
#        tools/subissue.sh --help | -h
#
# The tree is the work's real shape (D-059): a node branches until every leaf
# is one PR. GitHub's native sub-issues carry the hierarchy; this script keeps
# the two steps — create, then link — from ever drifting apart, because a child
# created and forgotten is a leaf nobody can find.

set -euo pipefail

REPO="${MATRIX_REPO:-gokselozgur5/matrix-sim}"

# The suite, before the refusals it exercises (#1309). Cutting an issue needs a
# token; REFUSING does not, and refusing is what this tool's catalog row
# promises. The token half stays out for #1273's reason — a fixture that fakes
# `gh` tests the fake — so this covers the door and the row says so.
if [ "${1:-}" = "--selftest" ]; then
  pass=0
  fail=0
  tmp="$(mktemp -d "${TMPDIR:-/tmp}/subissue.XXXXXX")"
  trap 'rm -rf "$tmp"' EXIT
  : > "$tmp/body.md"
  case_() {                     # case_ <name> <want-code> <args...>
    local name="$1" want="$2"; shift 2
    local got=0
    bash "$0" "$@" >/dev/null 2>&1 || got=$?
    if [ "$want" = "$got" ]; then
      pass=$((pass + 1)); printf 'SUBISSUE case=%-20s want=%s got=%s OK\n' "$name" "$want" "$got"
    else
      fail=$((fail + 1)); printf 'SUBISSUE case=%-20s want=%s got=%s BROKEN\n' "$name" "$want" "$got"
    fi
  }
  case_ no-arguments   2
  case_ no-title       2 1246
  case_ no-body        2 1246 "a title"
  case_ not-a-number   2 not-a-number "a title" "$tmp/body.md"
  case_ missing-body   2 1246 "a title" "$tmp/nope.md"
  case_ unknown-flag   2 1246 "a title" "$tmp/body.md" --nonsense
  echo "SUBISSUE SELFTEST VERDICT $([ "$fail" -eq 0 ] && echo PASS || echo FAIL) cases=$((pass + fail)) failed=$fail"
  [ "$fail" -eq 0 ] || exit 1
  exit 0
fi

# Refusals, spelled out rather than left to bash — the same defect #1276 found
# in issuetree.sh, sitting here in three places at once.
#
# `${1:?usage…}` leaves with 1, which is this tree's code for THE CLAIM DOES NOT
# HOLD. This row has always promised `2 the invocation was refused`, and three
# of its refusals — no parent, no title, no body — were spending 1 instead. The
# two that were spelled out by hand (`parent must be an issue number`, `body
# file not found`) had it right, so the tool disagreed with itself about what a
# refusal costs.
# THE DOOR IS READ BEFORE THE POSITIONAL ARGUMENTS (#1527). Below this the first argument is a parent issue number, and the arity check fires first, so an unread --help is refused with a usage line and exit 2 rather than answered.
case "${1:-}" in
  -h|--help) awk 'NR==1 {next} !/^#/ {exit} /^#$/ {if (++blank == 2) exit} {print}' "$0"; exit 0 ;;
esac

#
# Neither of advice.sh's exit-code checks could see it: `codes_undocumented`
# reads LITERAL exits and bash's is not one, and `codes_unspent` asks whether a
# promised code is spent — 2 is, by the two hand-written refusals — so the
# hidden 1 was invisible from both directions (#1309).
if [ $# -lt 3 ]; then
  echo "FATAL usage: tools/subissue.sh <parent-number> \"<title>\" <body-file> [--label L] [--milestone M]" >&2
  exit 2
fi
PARENT="$1"
TITLE="$2"
BODY="$3"
shift 3

[[ "$PARENT" =~ ^[0-9]+$ ]] || { echo "FATAL parent must be an issue number" >&2; exit 2; }
[[ -n "$TITLE" ]] || { echo "FATAL title required" >&2; exit 2; }
[[ -f "$BODY" ]] || { echo "FATAL body file not found: $BODY" >&2; exit 2; }

# THE FLAGS ARE READ BEFORE THE NETWORK, and CI is why (#1309). The suite's
# `unknown-flag` case passed locally and printed `want=2 got=4` on the runner:
# the flag loop sat below two `gh issue view` calls, so a typo'd flag went to
# GitHub first and left with whatever `gh` exits under `set -e`. Locally that
# path had a token and an open #1246 to read; the runner had neither.
#
# The repair is the right order regardless of the suite. Nothing about
# `--nonsense` needs an API to decide, and a tool that asks the network before
# reading its own argument list spends a round trip to refuse.
LABELS=()
MILESTONE=""
HAS_MILESTONE=0
while (($#)); do
  case "$1" in
    --label) LABELS+=(--label "$2"); shift 2 ;;
    --milestone) MILESTONE="$2"; HAS_MILESTONE=1; shift 2 ;;
    *) echo "FATAL unknown flag: $1" >&2; exit 2 ;;
  esac
done

# The parent must exist and be open — a tree does not grow from a closed branch.
STATE="$(gh issue view "$PARENT" --repo "$REPO" --json state --jq .state)"
[[ "$STATE" == "OPEN" ]] || { echo "FATAL parent #$PARENT is $STATE" >&2; exit 2; }

# Inherit the parent's milestone unless the caller names one; a child that
# floats out of its phase is a child nobody schedules.
INHERIT="$(gh issue view "$PARENT" --repo "$REPO" --json milestone --jq '.milestone.title // ""')"
ARGS=(--repo "$REPO" --title "$TITLE" --body-file "$BODY")
if ((${#LABELS[@]})); then
  ARGS+=("${LABELS[@]}")
fi
if (( HAS_MILESTONE )); then
  ARGS+=(--milestone "$MILESTONE")
elif [[ -n "$INHERIT" ]]; then
  ARGS+=(--milestone "$INHERIT")
fi

URL="$(gh issue create "${ARGS[@]}")"
NUM="${URL##*/}"
ID="$(gh api "repos/$REPO/issues/$NUM" --jq .id)"
gh api -X POST "repos/$REPO/issues/$PARENT/sub_issues" -F sub_issue_id="$ID" >/dev/null
echo "LINKED #$PARENT <- #$NUM  $TITLE"
echo "$URL"
