#!/usr/bin/env bash
# tools/checkage.sh — a green check is evidence, and evidence has a date (#1017)
#
# Usage: tools/checkage.sh --pr N            judge the locks run on pull request N's head
#        tools/checkage.sh --sha SHA         judge the locks run on one head
#        tools/checkage.sh ... --base REF    the branch whose litany is the ruler
#                                            (default: the PR's own base, or main)
#        tools/checkage.sh ... --for OWNER/NAME    name the repository (default: origin)
#        tools/checkage.sh --selftest        the age arithmetic's own cases, no token, no network
#        tools/checkage.sh --help | -h      print this clause, and stop
#
# WHAT THIS EXISTS FOR. `gh pr checks` reports the checks attached to the current head, and
# a head that has not moved keeps the run it already has. That run stays green forever —
# including after the lock set it passed has been rewritten underneath it. The page says
# `locks pass` either way, and nothing on it separates a run of today's litany from a run
# of a litany that no longer exists.
#
# Measured on PR #889, before it was rebased:
#
#     $ gh pr checks 889 -R gokselozgur5/matrix-sim
#     locks   pass   34s   .../runs/31569862033
#     $ gh api repos/gokselozgur5/matrix-sim/actions/runs/31569862033 --jq .run_started_at
#     2026-08-12T06:24:10Z
#
# Between that run and the read, `.github/workflows/locks.yml` had been rewritten twenty-two
# times. That head had never met the canonical digest pin (#899), the attribution lock
# (#910), the charset lock (#836), the declared-move gate (#884), either balance.sh lock
# (#901, #828) or the neutral lane (#336) — and the pull request page said green. It is not
# hypothetical that this merges something bad: two PRs inherited that night carried commits
# authored under the address #910 refuses, and both showed green until a rebase gave CI a new
# head to judge, at which point the attribution lock ran for the first time and turned red.
#
# THE MECHANISM. One rule, and it is a rule about time rather than about content: a check is
# current only if its run STARTED AFTER the base's last commit to the litany file. Strictly
# after — a run that started in the same second as the commit did not contain it. Both sides
# print with their timestamps, so the verdict is quotable and not eyeballed.
#
# The two stamps come from the API in the same RFC 3339 shape, in UTC, with the Z on them,
# and are compared as strings after that shape is checked. No `date -d`: #901 is the bug that
# lives in exactly that idiom — a GNU-only spelling, green on the runner and dead on the
# macOS box that is the only one to ever type the command. Two strings of one fixed shape
# differ only in digit positions, so the string compare IS the chronological one, on any box
# and in any locale. A stamp in a shape this tool does not know is refused rather than
# compared: guessing at an unparsed date is how a stale green would be certified fresh.
#
# EXIT GRAMMAR. 0 the green is current · 1 the green judged a different litany (the refusal
# this exists for) · 2 the invocation was refused · 3 the answer could not be read (no gh, no
# token, no network, no such PR, a run with no datable start) · 4 no locks run on this head
# at all (#1004's shape — an absent run is not a passing one) · 5 the run exists and is not
# green (merge rule one refuses it before this tool's question arises).
#
# WHAT IT DOES NOT DO. It does not open the run and read which bytes of the litany the runner
# checked out — no API field carries that — so it dates the judgement rather than fingerprints
# it. That is enough for the failure this is about, where nothing ran at all after the litany
# moved, and it is why a re-run of an old run is the weaker remedy: it resets the clock this
# tool reads, and the answer that is definitely today's litany is a new head on today's base.
# It also does not judge the litany: whether today's lock set is a good one is an argument for
# a thread, and this asks only whether the green in front of you ever met it.

set -euo pipefail

WORKFLOW=.github/workflows/locks.yml
STAMP_RE='^[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}Z$'

MODE=""
PR=""
SHA=""
BASE=""
REPO=""

while [ $# -gt 0 ]; do
  case "$1" in
    --pr)       MODE=pr;  PR="${2:-}";   [ -n "$PR"   ] || { echo "FATAL --pr wants a number after it" >&2; exit 2; }; shift 2 ;;
    --sha)      MODE=sha; SHA="${2:-}";  [ -n "$SHA"  ] || { echo "FATAL --sha wants a commit after it" >&2; exit 2; }; shift 2 ;;
    --base)     BASE="${2:-}";           [ -n "$BASE" ] || { echo "FATAL --base wants a ref after it" >&2; exit 2; }; shift 2 ;;
    --for)      REPO="${2:-}";           [ -n "$REPO" ] || { echo "FATAL --for wants OWNER/NAME after it" >&2; exit 2; }; shift 2 ;;
    --selftest) MODE=selftest; shift ;;
    # READ TO THE END OF THE CLAUSE, not to a line number (#1382, #1520). `2,10p`
    # was right the day it was written and had already drifted by one — it printed
    # the blank comment line below the clause — and the direction that matters is
    # the other one: a door documented below the number is absent from `--help`
    # while sitting above the parser that accepts it. That is how `--schedules`
    # became a flag prstate.sh answered and never mentioned.
    -h|--help)  awk 'NR==1 {next} !/^#/ {exit} /^#$/ {if (++blank == 2) exit} {print}' "$0"; exit 0 ;;
    *) echo "FATAL unknown argument: $1" >&2; exit 2 ;;
  esac
done

# ---- the arithmetic ----------------------------------------------------------
#
# Both halves of the judgement are pure functions of text, which is what lets the
# suite below run them with no token and no network. The live modes only fetch
# the text and hand it here.

# judge <run stamp> <litany stamp> -> CURRENT | STALE | UNDATED
judge() {
  local run="${1:-}" lit="${2:-}"
  printf '%s' "$run" | grep -qE "$STAMP_RE" || { echo UNDATED; return 0; }
  printf '%s' "$lit" | grep -qE "$STAMP_RE" || { echo UNDATED; return 0; }
  if [[ "$run" > "$lit" ]]; then echo CURRENT; else echo STALE; fi
}

# pick_run — stdin: path<TAB>id<TAB>event<TAB>status<TAB>conclusion<TAB>started rows.
# stdout: the newest row belonging to the litany's own workflow; exit 1 if there is none.
# A head carries runs from every workflow the repository owns, and the API returns them
# in no order this tool should trust, so the row is chosen here rather than by taking
# `.workflow_runs[0]` and hoping.
pick_run() {
  local best="" best_ts="" p id ev st cc ts
  while IFS=$'\t' read -r p id ev st cc ts; do
    [ "${p:-}" = "$WORKFLOW" ] || continue
    if [ -z "$best_ts" ] || [[ "$ts" > "$best_ts" ]]; then
      best_ts="$ts"
      best="$(printf '%s\t%s\t%s\t%s\t%s\t%s' "$p" "$id" "$ev" "$st" "$cc" "$ts")"
    fi
  done
  [ -n "$best" ] || return 1
  printf '%s\n' "$best"
}

# ---- the suite ---------------------------------------------------------------
#
# The live reading needs a token, a network and a pull request, so on the day this
# lands it is a claim nobody can re-run. These are the cases that can run anywhere:
# the ordering rule including the second it turns on, the refusal to compare a shape
# it does not know, and the row picker faced with a head that carries other
# workflows' runs. Fixtures are literals on purpose — an arithmetic that derived its
# own expected answers would be marking its own homework.
selftest() {
  local pass=0 fail=0

  is() { # is <name> <want> <got>
    if [ "$2" = "$3" ]; then
      pass=$((pass + 1)); printf 'CHECKAGE CASE %s want=%s got=%s PASS\n' "$1" "$2" "$3"
    else
      fail=$((fail + 1)); printf 'CHECKAGE CASE %s want=%s got=%s FAIL\n' "$1" "$2" "$3"
    fi
  }

  is run-after-litany       CURRENT "$(judge 2026-08-14T01:34:11Z 2026-08-14T01:33:06Z)"
  is run-one-second-after   CURRENT "$(judge 2026-08-12T06:24:11Z 2026-08-12T06:24:10Z)"
  is run-before-litany-889  STALE   "$(judge 2026-08-12T06:24:10Z 2026-08-14T01:33:06Z)"
  is run-one-second-before  STALE   "$(judge 2026-08-12T06:24:09Z 2026-08-12T06:24:10Z)"
  is run-at-the-same-second STALE   "$(judge 2026-08-12T06:24:10Z 2026-08-12T06:24:10Z)"
  is across-a-month-end     CURRENT "$(judge 2026-09-01T00:00:00Z 2026-08-31T23:59:59Z)"
  is across-a-year-end      CURRENT "$(judge 2027-01-01T00:00:00Z 2026-12-31T23:59:59Z)"
  is run-stamp-missing      UNDATED "$(judge '' 2026-08-14T01:33:06Z)"
  is run-stamp-not-utc      UNDATED "$(judge 2026-08-14T04:34:11+03:00 2026-08-14T01:33:06Z)"
  is run-stamp-spaced       UNDATED "$(judge '2026-08-14 01:34:11' 2026-08-14T01:33:06Z)"
  is litany-stamp-missing   UNDATED "$(judge 2026-08-14T01:34:11Z '')"

  # The picker: three runs on one head, the newest of them another workflow's, listed
  # out of order — the shape that makes `.workflow_runs[0]` wrong.
  local rows newest
  rows="$(printf '%s\t%s\t%s\t%s\t%s\t%s\n' \
    "$WORKFLOW"              9001 pull_request completed success 2026-08-14T01:29:56Z \
    ".github/workflows/other.yml" 9002 push     completed success 2026-08-14T01:40:00Z \
    "$WORKFLOW"              9003 pull_request completed failure 2026-08-14T01:30:09Z)"
  newest="$(printf '%s\n' "$rows" | pick_run | cut -f2)"
  is picks-the-newest-locks-run 9003 "$newest"

  newest="$(printf '%s\t%s\t%s\t%s\t%s\t%s\n' \
    ".github/workflows/other.yml" 9002 push completed success 2026-08-14T01:40:00Z | pick_run || echo NONE)"
  is no-locks-run-among-others NONE "$newest"

  newest="$(printf '' | pick_run || echo NONE)"
  is no-runs-at-all NONE "$newest"

  if [ "$((pass + fail))" -eq 0 ]; then
    echo "CHECKAGE SELFTEST VERDICT FAIL cases=0 failed=0  (a suite of nothing is not a pass)"
    return 1
  fi
  if [ "$fail" -eq 0 ]; then
    printf 'CHECKAGE SELFTEST VERDICT PASS cases=%d failed=0\n' "$((pass + fail))"
    return 0
  fi
  printf 'CHECKAGE SELFTEST VERDICT FAIL cases=%d failed=%d\n' "$((pass + fail))" "$fail"
  return 1
}

if [ "$MODE" = selftest ]; then
  selftest
  exit $?
fi

# ---- the live reading --------------------------------------------------------

[ -n "$MODE" ] || {
  echo "FATAL nothing to judge: pass --pr N, --sha SHA, or --selftest" >&2
  sed -n '2,10p' "$0" >&2
  exit 2; }

if [ "$MODE" = pr ] && ! printf '%s' "$PR" | grep -qE '^[0-9]+$'; then
  echo "FATAL --pr wants a pull request number, got: $PR" >&2; exit 2
fi

command -v gh >/dev/null 2>&1 || {
  echo "FATAL gh is not on PATH — this reads GitHub's own record of the run" >&2; exit 3; }

if [ -z "$REPO" ]; then
  REPO="$(git remote get-url origin 2>/dev/null | sed -E 's#.*github\.com[/:]##; s#\.git$##' || true)"
fi
printf '%s' "$REPO" | grep -qE '^[A-Za-z0-9._-]+/[A-Za-z0-9._-]+$' || {
  echo "FATAL cannot tell which repository this is; pass --for OWNER/NAME" >&2; exit 2; }

# Every read below is an `if !` and not an `|| true`. `gh api` prints the error BODY on
# stdout and the failure on stderr, so a swallowed exit code leaves the 404 JSON sitting in
# the variable — and this tool then reported a base named {"message":"Not Found"}. The read
# is judged by its exit code, and what it returned is then checked for the SHAPE it must
# have: a sha of forty hex, a stamp with the Z on it. A field that arrived as prose from an
# error page must never reach the comparison.
if [ "$MODE" = pr ]; then
  if ! meta="$(gh api "repos/$REPO/pulls/$PR" --jq '[.head.sha, .base.ref] | @tsv' 2>/dev/null)"; then meta=""; fi
  [ -n "$meta" ] || { echo "FATAL cannot read pull request #$PR of $REPO (token? network? number?)" >&2; exit 3; }
  HEAD_SHA="$(printf '%s' "$meta" | cut -f1)"
  [ -n "$BASE" ] || BASE="$(printf '%s' "$meta" | cut -f2)"
  SUBJECT="pr=$PR"
else
  if ! HEAD_SHA="$(gh api "repos/$REPO/commits/$SHA" --jq '.sha' 2>/dev/null)"; then HEAD_SHA=""; fi
  [ -n "$HEAD_SHA" ] || { echo "FATAL $REPO has never seen $SHA (token? network? sha?)" >&2; exit 3; }
  [ -n "$BASE" ] || BASE=main
  SUBJECT="pr=-"
fi
printf '%s' "$HEAD_SHA" | grep -qE '^[0-9a-f]{40}$' || {
  echo "FATAL the head came back in a shape that is not a commit sha: $HEAD_SHA" >&2; exit 3; }
[ -n "$BASE" ] || { echo "FATAL no base to rule against; pass --base REF" >&2; exit 3; }

printf 'CHECKAGE SUBJECT repo=%s %s head=%s base=%s\n' "$REPO" "$SUBJECT" "${HEAD_SHA:0:7}" "$BASE"

# The ruler: the base's last commit to the litany file. Not this branch's — a PR that
# edits the workflow still has to have been judged after the base's last edit, and
# reading the ruler off the branch would let a PR move its own goalposts.
if ! lit="$(gh api "repos/$REPO/commits?path=$WORKFLOW&sha=$BASE&per_page=1" \
             --jq '.[0] | [.sha, .commit.committer.date] | @tsv' 2>/dev/null)"; then lit=""; fi
[ -n "$lit" ] || {
  echo "FATAL cannot read a commit to $WORKFLOW on $BASE — is that the right base?" >&2; exit 3; }
LIT_SHA="$(printf '%s' "$lit" | cut -f1)"
LIT_AT="$(printf '%s' "$lit" | cut -f2)"
printf '%s' "$LIT_AT" | grep -qE "$STAMP_RE" || {
  echo "FATAL $WORKFLOW's last commit on $BASE carries no comparable date: $LIT_AT" >&2; exit 3; }

printf 'CHECKAGE LITANY %s head=%s committed=%s base=%s\n' "$WORKFLOW" "${LIT_SHA:0:7}" "$LIT_AT" "$BASE"

# A read that FAILED and a head that carries NO run are different answers, and only the
# second one is ABSENT. Collapsing them would turn a missing token into an alarm about the
# pull request.
if ! rows="$(gh api "repos/$REPO/actions/runs?head_sha=$HEAD_SHA&per_page=100" \
              --jq '.workflow_runs[] | [.path, .id, .event, .status, (.conclusion // "-"), (.run_started_at // .created_at)] | @tsv' \
              2>/dev/null)"; then
  echo "FATAL cannot read the runs attached to ${HEAD_SHA:0:7} (token? network?)" >&2; exit 3
fi

run_row="$(printf '%s\n' "$rows" | pick_run || true)"
if [ -z "$run_row" ]; then
  printf 'CHECKAGE RUN none\n'
  printf 'CHECKAGE VERDICT ABSENT head=%s litany=%s  (no %s run on this head; an absent run is not a passing one — #1004)\n' \
    "${HEAD_SHA:0:7}" "$LIT_AT" "$WORKFLOW"
  exit 4
fi

RUN_ID="$(printf '%s' "$run_row" | cut -f2)"
RUN_EVENT="$(printf '%s' "$run_row" | cut -f3)"
RUN_STATUS="$(printf '%s' "$run_row" | cut -f4)"
RUN_CONCL="$(printf '%s' "$run_row" | cut -f5)"
RUN_AT="$(printf '%s' "$run_row" | cut -f6)"

printf 'CHECKAGE RUN id=%s event=%s status=%s conclusion=%s started=%s\n' \
  "$RUN_ID" "$RUN_EVENT" "$RUN_STATUS" "$RUN_CONCL" "$RUN_AT"

# A run that has not finished, or finished red, is refused by the first merge rule and
# never reaches the question this tool was built for. Saying CURRENT about it would read
# as approval of a check that is not green.
if [ "$RUN_STATUS" != completed ] || [ "$RUN_CONCL" != success ]; then
  printf 'CHECKAGE VERDICT NOTGREEN run=%s status=%s conclusion=%s  (never merge red; the age question is downstream of this one)\n' \
    "$RUN_ID" "$RUN_STATUS" "$RUN_CONCL"
  exit 5
fi

case "$(judge "$RUN_AT" "$LIT_AT")" in
  CURRENT)
    printf 'CHECKAGE VERDICT CURRENT run=%s started=%s litany=%s edits=0\n' "$RUN_ID" "$RUN_AT" "$LIT_AT"
    exit 0 ;;
  UNDATED)
    printf 'CHECKAGE VERDICT UNDATED run=%s started=%s litany=%s  (a stamp in a shape this tool cannot compare)\n' \
      "$RUN_ID" "$RUN_AT" "$LIT_AT"
    exit 3 ;;
esac

# The count is the size of the gap, not the verdict — the verdict is already known. A
# counting call that fails says so rather than printing a confident 0, which would read as
# "the litany barely moved" about a run that could be a month old.
if edits_raw="$(gh api --paginate "repos/$REPO/commits?path=$WORKFLOW&sha=$BASE&since=$RUN_AT&per_page=100" \
                 --jq '.[].sha' 2>/dev/null)"; then
  edits="$(printf '%s\n' "$edits_raw" | grep -c '[0-9a-f]' || true)"
else
  edits=unknown
fi
printf 'CHECKAGE VERDICT STALE run=%s started=%s litany=%s edits=%s\n' "$RUN_ID" "$RUN_AT" "$LIT_AT" "$edits"
printf '  %s rewrote %s after this run started (edits=%s), so this green is not evidence\n' "$BASE" "$WORKFLOW" "$edits"
printf '  about the lock set the merge would land under.\n'
printf '  fix: put the head on today'"'"'s base and let CI judge it there —\n'
printf '       git fetch origin && git rebase origin/%s && git push --force-with-lease\n' "$BASE"
printf '  then read this line again. A new head gets a run of today'"'"'s litany; re-running run %s\n' "$RUN_ID"
printf '  only resets the clock this line reads.\n'
exit 1
