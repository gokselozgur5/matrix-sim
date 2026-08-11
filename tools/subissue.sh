#!/usr/bin/env bash
# tools/subissue.sh — cut a child issue and hang it on its parent, in one motion.
#
# Usage: tools/subissue.sh <parent-number> "<title>" <body-file> [--label L] [--milestone M]
#
# The tree is the work's real shape (D-059): a node branches until every leaf
# is one PR. GitHub's native sub-issues carry the hierarchy; this script keeps
# the two steps — create, then link — from ever drifting apart, because a child
# created and forgotten is a leaf nobody can find.

set -euo pipefail

REPO="${MATRIX_REPO:-gokselozgur5/matrix-sim}"
PARENT="${1:?usage: tools/subissue.sh <parent-number> \"<title>\" <body-file> [--label L] [--milestone M]}"
TITLE="${2:?title required}"
BODY="${3:?body file required}"
shift 3

[[ "$PARENT" =~ ^[0-9]+$ ]] || { echo "FATAL parent must be an issue number" >&2; exit 2; }
[[ -f "$BODY" ]] || { echo "FATAL body file not found: $BODY" >&2; exit 2; }

# The parent must exist and be open — a tree does not grow from a closed branch.
STATE="$(gh issue view "$PARENT" --repo "$REPO" --json state --jq .state)"
[[ "$STATE" == "OPEN" ]] || { echo "FATAL parent #$PARENT is $STATE" >&2; exit 2; }

# Inherit the parent's milestone unless the caller names one; a child that
# floats out of its phase is a child nobody schedules.
INHERIT="$(gh issue view "$PARENT" --repo "$REPO" --json milestone --jq '.milestone.title // ""')"
ARGS=(--repo "$REPO" --title "$TITLE" --body-file "$BODY")
HAS_MILESTONE=0
while (($#)); do
  case "$1" in
    --label) ARGS+=(--label "$2"); shift 2 ;;
    --milestone) ARGS+=(--milestone "$2"); HAS_MILESTONE=1; shift 2 ;;
    *) echo "FATAL unknown flag: $1" >&2; exit 2 ;;
  esac
done
if (( ! HAS_MILESTONE )) && [[ -n "$INHERIT" ]]; then
  ARGS+=(--milestone "$INHERIT")
fi

URL="$(gh issue create "${ARGS[@]}")"
NUM="${URL##*/}"
ID="$(gh api "repos/$REPO/issues/$NUM" --jq .id)"
gh api -X POST "repos/$REPO/issues/$PARENT/sub_issues" -F sub_issue_id="$ID" >/dev/null
echo "LINKED #$PARENT <- #$NUM  $TITLE"
echo "$URL"
