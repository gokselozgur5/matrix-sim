#!/usr/bin/env bash
# tools/baseline.sh — does this PR's evidence know which world it was measured in? (#822)
#
# Usage: tools/baseline.sh <pr-body-file> <base-sha>
#
# THE FINDING THIS EXISTS FOR. PR #207 shipped a full evidence table — a 196-tick
# park at seed 5, a refusal at 4265, an ECO dip at 5300 — and none of it
# reproduced at HEAD. The numbers were true when measured. Between measurement
# and merge, #205 landed and moved the world underneath them. Every lock we own
# passed, because our locks check the CODE against itself and never the EVIDENCE
# against the tree it is about to land on.
#
# So this is the one lock that judges a claim rather than a compilation. It asks
# a PR a single question — *which main were you measuring?* — and then checks
# whether that main is still the one underneath it.
#
# WHY IT WARNS AND DOES NOT FAIL, MOSTLY. On a day when a dozen crews merge into
# one main, drift is the normal condition, not the exception; a check that fails
# on drift would be red all day and would teach the crew to ignore it, which is
# strictly worse than no check. So drift is reported, named, and left to the
# author's judgement: most drift does not touch most evidence, and only the
# author knows whether these particular commits touch these particular numbers.
#
# THE ONE PLACE IT FAILS. A declared move — a PR that says the DIGEST chain
# legitimately changes here — quotes a 'before' sha as the whole basis of its
# claim. If that 'before' is not the tree's actual before, the claim is not
# weakened, it is unverifiable by construction: nobody can ever check that the
# move was the move that was declared. That is not drift. That is a lock with
# nothing behind it, and it fails.
#
# Zero dependencies beyond git and coreutils, like every other lock (Dev7).

set -euo pipefail

BODY="${1:-}"
BASE="${2:-}"

if [ -z "$BODY" ] || [ -z "$BASE" ]; then
  echo "usage: tools/baseline.sh <pr-body-file> <base-sha>" >&2
  exit 2
fi
[ -r "$BODY" ] || { echo "FATAL cannot read PR body file: $BODY" >&2; exit 2; }

# A provenance check run against a shallow clone would call every honest sha
# unknown and blame the author for the runner's configuration. Refuse instead:
# a checker that cannot see history has no standing to judge history.
if [ "$(git rev-parse --is-shallow-repository 2>/dev/null || echo true)" = true ]; then
  echo "FATAL this clone is shallow, so it cannot verify any baseline sha." >&2
  echo "      the provenance lock needs history: checkout with fetch-depth: 0." >&2
  exit 2
fi

git cat-file -e "${BASE}^{commit}" 2>/dev/null || {
  echo "FATAL the base sha ${BASE} is not a commit in this clone." >&2
  exit 2
}

# The two fields, read off the body. Both are matched case-insensitively, with
# or without markdown bold, because the field is for humans to type and a lock
# that trips on `**Baseline:**` versus `Baseline:` is a lock about formatting.
field() {                       # field <label-regex> — the first matching line, or empty
  grep -im1 -E "^[[:space:]]*[*_]{0,2}${1}[*_]{0,2}[[:space:]]*:" "$BODY" || true
}

BASELINE_LINE="$(field 'baseline')"
MOVE_LINE="$(field 'declared move')"

# The first 7–40 hex run on the baseline line. Anything else on that line — a
# backtick, a link, a parenthetical — is the author's business, not ours.
STATED="$(printf '%s' "$BASELINE_LINE" | grep -oiE '\b[0-9a-f]{7,40}\b' | head -1 || true)"

# `yes` anywhere on the declared-move line means yes. The template's default is
# `no`, so the strict path is opt-in and nobody gets failed for a field they
# never touched.
DECLARED_MOVE=no
printf '%s' "$MOVE_LINE" | grep -qiE '\byes\b' && DECLARED_MOVE=yes

verdict() { printf 'BASELINE %s\n' "$1"; }

# ---- 1. no field at all -----------------------------------------------------
if [ -z "$STATED" ]; then
  if [ "$DECLARED_MOVE" = yes ]; then
    verdict 'MISSING declared_move=yes'
    echo "FATAL this PR declares a move of the DIGEST chain but states no baseline." >&2
    echo "      a declared move is a claim about a before and an after; without the" >&2
    echo "      before, there is nothing for anyone to check it against." >&2
    echo "      add to the body:  **Baseline:** \$(git rev-parse --short HEAD)" >&2
    exit 1
  fi
  verdict 'MISSING declared_move=no'
  echo "WARN  this PR states no baseline, so its evidence cannot be dated." >&2
  echo "      add one line to the body and this becomes checkable:" >&2
  echo "      **Baseline:** \$(git rev-parse --short HEAD)   # before you branched" >&2
  exit 0
fi

# ---- 2. a sha nobody can resolve -------------------------------------------
# Worse than no sha: it *reads* as provenance. A typo, a sha from a fork, a
# number that was never a commit — all pass a human's eye and none can be
# checked. This fails on every PR, declared move or not, because the failure is
# not "your evidence is stale", it is "your evidence cites a world that does not
# exist".
if ! git cat-file -e "${STATED}^{commit}" 2>/dev/null; then
  verdict "UNKNOWN stated=${STATED}"
  echo "FATAL the stated baseline ${STATED} is not a commit in this repository." >&2
  echo "      a sha that cannot be resolved is not provenance — it only looks like it." >&2
  exit 1
fi

STATED_FULL="$(git rev-parse "${STATED}^{commit}")"
BASE_FULL="$(git rev-parse "${BASE}^{commit}")"

# ---- 3. fresh ---------------------------------------------------------------
if [ "$STATED_FULL" = "$BASE_FULL" ]; then
  verdict "FRESH stated=${STATED} base=$(git rev-parse --short "$BASE_FULL")"
  echo "the evidence was measured against the tree it is landing on."
  exit 0
fi

SHORT_S="$(git rev-parse --short "$STATED_FULL")"
SHORT_B="$(git rev-parse --short "$BASE_FULL")"

# ---- 4. stale, but on the line ---------------------------------------------
if git merge-base --is-ancestor "$STATED_FULL" "$BASE_FULL"; then
  # Counted and listed over the SAME set. The first draft counted every commit
  # and listed only the non-merges, so a 12-commit drift printed seven lines —
  # a meter disagreeing with its own evidence, which is the exact defect this
  # whole file exists to catch. Merge commits carry no content of their own
  # here; what moved the world underneath the author is the non-merge set, so
  # that is the set both numbers describe.
  N="$(git rev-list --no-merges --count "${STATED_FULL}..${BASE_FULL}")"
  verdict "STALE stated=${SHORT_S} base=${SHORT_B} intervening=${N} declared_move=${DECLARED_MOVE}"
  echo "${N} commit(s) landed on main between the measurement and this check:"
  git log --no-merges --format='  %h  %s' "${STATED_FULL}..${BASE_FULL}" | head -40
  [ "$N" -gt 40 ] && echo "  … and $((N - 40)) more"
  if [ "$DECLARED_MOVE" = yes ]; then
    echo >&2
    echo "FATAL this PR declares a move of the DIGEST chain, and its 'before' is not" >&2
    echo "      the tree's before. The move it declares cannot be verified by anyone:" >&2
    echo "      re-measure against ${SHORT_B} and restate the baseline." >&2
    exit 1
  fi
  echo
  echo "WARN  this is not automatically wrong — most drift does not touch most"
  echo "      evidence. But only you know whether these commits touch these numbers."
  echo "      If any of them do, re-measure against ${SHORT_B} and restate the baseline."
  exit 0
fi

# ---- 5. stale, and off the line --------------------------------------------
# The stated sha resolves but is not an ancestor of the base: it was a branch
# tip, a commit that was squashed away, or another lineage entirely. The
# evidence describes a world that main never passed through, which is a
# different and louder problem than being behind.
verdict "OFF_LINE stated=${SHORT_S} base=${SHORT_B} declared_move=${DECLARED_MOVE}"
echo "the stated baseline is not an ancestor of main's tip — it is not a tree main ever was."
echo "commonly: a sha from your own branch rather than the main you branched off."
echo "the merge base of the two is $(git merge-base "$STATED_FULL" "$BASE_FULL" | cut -c1-7)."
if [ "$DECLARED_MOVE" = yes ]; then
  echo >&2
  echo "FATAL a declared move measured against a tree main never was is unverifiable." >&2
  exit 1
fi
echo
echo "WARN  re-measure against ${SHORT_B} — the tree this is landing on."
exit 0
