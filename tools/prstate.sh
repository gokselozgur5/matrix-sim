#!/usr/bin/env bash
# tools/prstate.sh — a pull request has a third state, and `gh pr checks` cannot say it (#1004)
#
# Usage: tools/prstate.sh N | --pr N     judge one pull request
#        tools/prstate.sh                judge every open pull request
#        tools/prstate.sh --for OWNER/NAME   name the repository (default: origin)
#        tools/prstate.sh --selftest     run the judge's own cases; no token, no network
#
# WHAT THIS EXISTS FOR. The merge rule in this house is *never merge red*. It has no clause
# for a pull request that is neither red nor green, and that is a state this repository
# produces routinely.
#
# GitHub builds a `pull_request` workflow run from the head/base MERGE REF. When the branch
# conflicts with main, that ref cannot be constructed, so no run is created at all — not a
# failed run, not a queued one. Nothing. And the thing a crew reads to find out says
#
#     $ gh pr checks 990
#     no checks reported on the 'unit/882-eco-scale-final' branch
#
# which is the same sentence it prints for a run that has not started yet. #990 sat
# CONFLICTING from 22:53 with zero runs across four head shas; its crew read the silence as
# slowness and spent over an hour reviewing a tree that did not compile — `Config.java:169:
# cannot find symbol`, lock 1, catchable in seconds. #917 sat seventeen minutes the same way
# over a `tools/README.md` conflict. Both times a rebase produced a run within seconds. With
# fifty units a day landing against one main, conflict is the NORMAL state of a branch
# between push and rebase, and every one of those windows is time when the locks are not
# running and nothing says so.
#
# The defect is not GitHub's behaviour, which is reasonable. It is that the repository reads
# "no checks" as neutral when it is an alarm. So this names the state — UNBUILT, distinct
# from RED and from PENDING — and states its denominator on the line.
#
# WHY THIS IS NOT A STEP IN locks.yml. Because the run that would carry the step is the very
# thing that does not exist. Any check living inside the workflow can only speak about pull
# requests that already built; the one this is about creates no run to put a step in. The
# reader has to stand outside CI, and it has to be the thing a crew runs INSTEAD of
# `gh pr checks`. Only the judge's own cases (`--selftest`) can be wired into the lane.
#
# WHAT IT DOES NOT DO. It does not rebase, merge or push anything, and it does not read the
# CONTENT of a run — locks 0 through 11 do that and this cannot improve on them. A green run
# under a CONFLICTING pull request is still UNBUILT here: that run was built from a merge ref
# that no longer exists, so it is evidence about a tree nobody is proposing to land. And it
# cannot see a run GitHub has not created yet, which is exactly why zero runs is an alarm and
# not a pass — from outside, "no run will ever exist" and "the run starts in four seconds"
# are the same reading, and only one of them is safe to merge on.
#
# EXIT GRAMMAR. 0 green · 1 red · 2 the invocation was refused · 3 the repository or the pull
# request could not be read · 4 UNBUILT · 5 pending. A sweep exits on its worst pull request,
# worst meaning UNBUILT before RED before PENDING.

set -euo pipefail

MODE=judge
PR=""
REPO=""
# Mergeability is computed lazily on GitHub's side and asking is what starts it, so a first
# read of UNKNOWN means "not computed yet" far more often than it means anything else.
POLLS=5
POLL_SLEEP=2

while [ $# -gt 0 ]; do
  case "$1" in
    --pr)  PR="${2:-}";   [ -n "$PR" ]   || { echo "FATAL --pr wants a number" >&2; exit 2; }; shift 2 ;;
    --for) REPO="${2:-}"; [ -n "$REPO" ] || { echo "FATAL --for wants OWNER/NAME" >&2; exit 2; }; shift 2 ;;
    --selftest) MODE=selftest; shift ;;
    -h|--help) sed -n '2,7p' "$0"; exit 0 ;;
    -*) echo "FATAL unknown flag: $1" >&2; exit 2 ;;
    *)  [ -z "$PR" ] || { echo "FATAL unexpected argument: $1" >&2; exit 2; }; PR="$1"; shift ;;
  esac
done
[ -z "$PR" ] || printf '%s\n' "$PR" | grep -qxE '[0-9]+' \
  || { echo "FATAL a pull request is a number, not '$PR'" >&2; exit 2; }

# ---- the judge ---------------------------------------------------------------
#
# One function decides every verdict, and it takes no network: the four states are an
# argument about counts, and an argument about counts is testable. Live reading below only
# fetches the three inputs.
#
# judge <pr-state> <mergeable> <rows>   rows: one workflow run per line,
#                                       name<TAB>event<TAB>status<TAB>conclusion
#   -> VERDICT<TAB>runs<TAB>green<TAB>red<TAB>pending<TAB>why
judge() {
  local pr_state="$1" mergeable="$2" rows="$3" base="${4:-main}" patterns="${5:-main}"
  local total=0 green=0 red=0 pending=0
  local name event status conclusion

  while IFS=$'\t' read -r name event status conclusion; do
    [ -n "${name:-}" ] || continue
    total=$((total + 1))
    if [ "${status:-}" != completed ]; then pending=$((pending + 1)); continue; fi
    case "${conclusion:-}" in
      # An unrecognised conclusion counts as red on purpose: a word this judge has no
      # reading for must not be able to arrive as a quiet pass.
      success|neutral|skipped) green=$((green + 1)) ;;
      *)                       red=$((red + 1)) ;;
    esac
  done <<< "$rows"

  # The conflicting leg is asked first and only of an OPEN pull request — a merged or closed
  # one reports mergeable=UNKNOWN forever, and nobody is about to land it. The counts are
  # still reported as measured: a stale green run from before main moved is exactly the
  # reading that makes this call look wrong to a reader who has not been told why.
  if [ "$pr_state" = OPEN ] && [ "$mergeable" = CONFLICTING ]; then
    printf 'UNBUILT\t%d\t%d\t%d\t%d\tconflicting\n' "$total" "$green" "$red" "$pending"; return 0
  fi
  # A fifth state, and the reason it is not UNBUILT: a pull request based on a
  # branch no workflow triggers for was never going to be built. `gh pr checks`
  # prints "no checks reported on the '<branch>' branch" for it — the same
  # sentence it prints for a run that has not started, which is the ambiguity
  # #1004 built this tool to end. It ended three of the four cases; this is the
  # fourth. Every workflow here is `branches: [main]`, so every stacked unit PR
  # — how this tree ships anything needing an unmerged predecessor — sits
  # unjudged and looks identical to one that is merely early (#1210).
  #
  # Asked BEFORE the zero-run test, because "no runs" is the symptom both share
  # and the eligible base is the thing that tells them apart.
  if [ "$total" -eq 0 ] && ! base_eligible "$base" "$patterns"; then
    printf 'NOT_ELIGIBLE\t0\t0\t0\t0\tbase=%s\n' "$base"; return 0
  fi
  if [ "$total" -eq 0 ]; then
    printf 'UNBUILT\t0\t0\t0\t0\tno-run\n'; return 0
  fi
  if [ "$red" -gt 0 ]; then
    printf 'RED\t%d\t%d\t%d\t%d\t-\n' "$total" "$green" "$red" "$pending"; return 0
  fi
  if [ "$pending" -gt 0 ]; then
    printf 'PENDING\t%d\t%d\t%d\t%d\t-\n' "$total" "$green" "$red" "$pending"; return 0
  fi
  printf 'GREEN\t%d\t%d\t%d\t%d\t-\n' "$total" "$green" "$red" "$pending"
}

code_for() { case "$1" in GREEN) echo 0 ;; RED) echo 1 ;; UNBUILT) echo 4 ;; PENDING) echo 5 ;; NOT_ELIGIBLE) echo 6 ;; *) echo 3 ;; esac; }
# NOT_ELIGIBLE outranks UNBUILT in a sweep: an unbuilt PR is waiting for a run,
# and this one is waiting for nothing at all.
rank_of()  { case "$1" in NOT_ELIGIBLE) echo 4 ;; UNBUILT) echo 3 ;; RED) echo 2 ;; PENDING) echo 1 ;; *) echo 0 ;; esac; }

# The bases the workflows will actually run for, READ FROM THE WORKFLOWS. The
# alternative is a list beside them, which is a second copy of a fact and the
# thing this tree keeps finding stale. Handles `branches: [main]` and
# `branches: ['main', 'unit/**']` alike.
eligible_bases() {
  grep -hE '^[[:space:]]*branches:[[:space:]]*\[' .github/workflows/*.yml 2>/dev/null \
    | sed -E 's/.*branches:[[:space:]]*\[([^]]*)\].*/\1/' \
    | tr ',' '\n' \
    | tr -d " '\"" \
    | grep -v '^$' \
    | sort -u
}

# base_eligible <base> <newline-separated patterns>
#
# `**` is GitHub's glob, not the shell's: in a workflow `unit/**` matches across
# slashes, while the shell's `case` treats `*` as matching them anyway — so the
# prefix test below is the honest reading of the pattern rather than a lucky one.
base_eligible() {
  local base="$1" pat
  while IFS= read -r pat; do
    [ -n "$pat" ] || continue
    case "$pat" in
      *'**') case "$base" in "${pat%'**'}"*) return 0 ;; esac ;;
      *)     case "$base" in $pat) return 0 ;; esac ;;
    esac
  done <<< "$2"
  return 1
}

# ---- the suite ---------------------------------------------------------------

selftest() {
  local pass=0 fail=0 seen=""
  check() { # check <name> <pr-state> <mergeable> <rows> <want> [base] [patterns]
    local got
    got="$(judge "$2" "$3" "$4" "${6:-main}" "${7:-main}" | awk -F'\t' '{printf "%s runs=%s green=%s red=%s pending=%s why=%s", $1,$2,$3,$4,$5,$6}')"
    seen="$seen ${got%% *}"
    if [ "$got" = "$5" ]; then
      pass=$((pass + 1)); printf 'CASE %-26s OK    %s\n' "$1" "$got"
    else
      fail=$((fail + 1)); printf 'CASE %-26s FAIL  got=[%s] want=[%s]\n' "$1" "$got" "$5"
    fi
  }

  local GREEN_ROW=$'locks\tpull_request\tcompleted\tsuccess'
  local FAIL_ROW=$'locks\tpull_request\tcompleted\tfailure'
  local QUEUED_ROW=$'locks\tpull_request\tqueued\t-'

  # The unit's own two readings first: #990 and #917 both sat conflicting, one of them with a
  # run left over from before main moved under it.
  check conflicting-no-run      OPEN CONFLICTING ''  \
    'UNBUILT runs=0 green=0 red=0 pending=0 why=conflicting'
  check conflicting-stale-green OPEN CONFLICTING "$GREEN_ROW" \
    'UNBUILT runs=1 green=1 red=0 pending=0 why=conflicting'
  check clean-no-run            OPEN MERGEABLE   ''  \
    'UNBUILT runs=0 green=0 red=0 pending=0 why=no-run'
  check clean-green             OPEN MERGEABLE   "$GREEN_ROW" \
    'GREEN runs=1 green=1 red=0 pending=0 why=-'
  check clean-red               OPEN MERGEABLE   "$FAIL_ROW" \
    'RED runs=1 green=0 red=1 pending=0 why=-'
  check clean-queued            OPEN MERGEABLE   "$QUEUED_ROW" \
    'PENDING runs=1 green=0 red=0 pending=1 why=-'
  check green-plus-queued       OPEN MERGEABLE   "$GREEN_ROW"$'\n'"$QUEUED_ROW" \
    'PENDING runs=2 green=1 red=0 pending=1 why=-'
  check red-beats-pending       OPEN MERGEABLE   "$FAIL_ROW"$'\n'"$QUEUED_ROW" \
    'RED runs=2 green=0 red=1 pending=1 why=-'
  check cancelled-is-red        OPEN MERGEABLE   $'locks\tpull_request\tcompleted\tcancelled' \
    'RED runs=1 green=0 red=1 pending=0 why=-'
  check unknown-conclusion-red  OPEN MERGEABLE   $'locks\tpull_request\tcompleted\tsomething_new' \
    'RED runs=1 green=0 red=1 pending=0 why=-'
  check skipped-counts-green    OPEN MERGEABLE   $'gate\tpull_request\tcompleted\tskipped'$'\n'"$GREEN_ROW" \
    'GREEN runs=2 green=2 red=0 pending=0 why=-'
  # A merged pull request answers UNKNOWN forever, which must not read as conflicting; and
  # the postmortem question this unit was opened by — did #990 land with a run at all — is
  # the same judge asked about a closed one.
  check merged-unknown-green    MERGED UNKNOWN   "$GREEN_ROW" \
    'GREEN runs=1 green=1 red=0 pending=0 why=-'
  check merged-unknown-no-run   MERGED UNKNOWN   ''  \
    'UNBUILT runs=0 green=0 red=0 pending=0 why=no-run'

  # The fifth state, and the four ways it must NOT fire (#1210).
  check stacked-no-run          OPEN MERGEABLE   ''  \
    'NOT_ELIGIBLE runs=0 green=0 red=0 pending=0 why=base=unit/1206-tool-depth-guard' \
    unit/1206-tool-depth-guard main
  # A base nothing triggers for, but the runs happened anyway — a workflow_dispatch,
  # a re-run, a trigger this parser did not read. Measured beats inferred: rows on
  # the table mean the thing was built, whatever the base says.
  check stacked-but-built       OPEN MERGEABLE   "$GREEN_ROW" \
    'GREEN runs=1 green=1 red=0 pending=0 why=-' \
    unit/1206-tool-depth-guard main
  # Conflicting is still asked first: a branch that cannot merge is unbuilt for a
  # reason the base cannot excuse.
  check stacked-conflicting     OPEN CONFLICTING ''  \
    'UNBUILT runs=0 green=0 red=0 pending=0 why=conflicting' \
    unit/1206-tool-depth-guard main
  # The widened trigger from #1210's first candidate repair: with `unit/**` in the
  # workflows, the same PR is eligible and its zero runs mean what they used to.
  check stacked-when-widened    OPEN MERGEABLE   ''  \
    'UNBUILT runs=0 green=0 red=0 pending=0 why=no-run' \
    unit/1206-tool-depth-guard $'main\nunit/**'
  # And the glob must not swallow everything: `unit/**` is not a licence for `dev`.
  check other-base-still-not    OPEN MERGEABLE   ''  \
    'NOT_ELIGIBLE runs=0 green=0 red=0 pending=0 why=base=dev' \
    dev $'main\nunit/**'

  # A suite that reaches one verdict is not a suite. The five states are the whole claim of
  # this tool, so the cases must be shown to separate them before their verdict counts.
  local missing=""
  local v
  for v in UNBUILT RED PENDING GREEN NOT_ELIGIBLE; do
    printf '%s' "$seen" | grep -qw "$v" || missing="$missing $v"
  done
  if [ -n "$missing" ]; then
    printf 'PRSTATE SELFTEST VERDICT FAIL cases=%d failed=%d  (no case reaches:%s)\n' \
      "$((pass + fail))" "$fail" "$missing"
    return 1
  fi
  if [ "$fail" -eq 0 ]; then
    printf 'PRSTATE SELFTEST VERDICT PASS cases=%d failed=0\n' "$pass"; return 0
  fi
  printf 'PRSTATE SELFTEST VERDICT FAIL cases=%d failed=%d\n' "$((pass + fail))" "$fail"
  return 1
}

if [ "$MODE" = selftest ]; then selftest; exit $?; fi

# ---- the live reading --------------------------------------------------------

if [ -z "$REPO" ]; then
  REPO="$(git remote get-url origin 2>/dev/null | sed -E 's#.*github\.com[/:]##; s#\.git$##' || true)"
fi
[[ "$REPO" =~ ^[A-Za-z0-9._-]+/[A-Za-z0-9._-]+$ ]] \
  || { echo "FATAL cannot tell which repository this is; pass --for OWNER/NAME" >&2; exit 2; }

report() { # report <pr-number> -> prints its rows and verdict line, returns its exit code
  local n="$1" fields state mergeable head base rows verdict runs green red pending why i
  local wname wevent wstatus wconclusion

  fields="$(gh pr view "$n" --repo "$REPO" --json state,mergeable,headRefOid,baseRefName \
              --jq '[.state, .mergeable, .headRefOid, .baseRefName] | @tsv' 2>/dev/null)" || {
    echo "FATAL cannot read pull request $n in $REPO (number? token? network?)" >&2; return 3; }
  IFS=$'\t' read -r state mergeable head base <<< "$fields"

  i=0
  while [ "$state" = OPEN ] && [ "$mergeable" = UNKNOWN ] && [ "$i" -lt "$POLLS" ]; do
    sleep "$POLL_SLEEP"
    fields="$(gh pr view "$n" --repo "$REPO" --json state,mergeable,headRefOid,baseRefName \
                --jq '[.state, .mergeable, .headRefOid, .baseRefName] | @tsv' 2>/dev/null)" || break
    IFS=$'\t' read -r state mergeable head base <<< "$fields"
    i=$((i + 1))
  done

  rows="$(gh api --paginate "repos/$REPO/actions/runs?head_sha=$head&per_page=100" \
            --jq '.workflow_runs[] | [.name, .event, .status, (.conclusion // "-")] | @tsv' 2>/dev/null)" || {
    echo "FATAL cannot read the workflow runs of ${head:0:7} in $REPO" >&2; return 3; }

  while IFS=$'\t' read -r wname wevent wstatus wconclusion; do
    [ -n "${wname:-}" ] || continue
    printf 'PR STATE RUN pr=%s %s event=%s status=%s conclusion=%s\n' \
      "$n" "$wname" "$wevent" "$wstatus" "$wconclusion"
  done <<< "$rows"

  IFS=$'\t' read -r verdict runs green red pending why \
    <<< "$(judge "$state" "$mergeable" "$rows" "$base" "$(eligible_bases)")"

  printf 'PR STATE VERDICT %s pr=%s head=%s state=%s mergeable=%s runs=%s green=%s red=%s pending=%s' \
    "$verdict" "$n" "${head:0:7}" "$state" "$mergeable" "$runs" "$green" "$red" "$pending"
  [ "$why" = "-" ] && printf '\n' || printf ' why=%s\n' "$why"

  case "$why" in
    conflicting)
      echo "  the merge ref cannot be built, so GitHub creates no run for this head — not a"
      echo "  failed one, not a queued one. Any run counted above predates the conflict."
      echo "  fix: git fetch origin main && git rebase origin/main && git push --force-with-lease" ;;
    no-run)
      echo "  no workflow run exists for this head sha. An absent run is not a passed one: the"
      echo "  locks have not judged this tree, whatever the checklist in the body says."
      echo "  if the branch is clean, the push may not have landed — git log origin/<branch> -1" ;;
  esac

  if [ "$state" = OPEN ] && [ "$mergeable" = UNKNOWN ]; then
    echo "PR STATE WARN pr=$n mergeable is still UNKNOWN after $POLLS reads — GitHub has not"
    echo "  computed this branch against main, so the conflicting leg of the verdict above went"
    echo "  unasked. Rerun before trusting a green."
  fi

  return "$(code_for "$verdict")"
}

if [ -n "$PR" ]; then
  set +e; report "$PR"; rc=$?; set -e
  exit "$rc"
fi

# The sweep is the mode that replaces `gh pr checks` as the thing a crew reads at the top of
# a session: every open pull request, one verdict line each, with the counts underneath.
nums="$(gh pr list --repo "$REPO" --state open --limit 100 --json number --jq '.[].number' 2>/dev/null)" || {
  echo "FATAL cannot list the open pull requests of $REPO (token? network?)" >&2; exit 3; }

open=0 unbuilt=0 red=0 pending=0 green=0 worst=0 worst_rc=0
for n in $nums; do
  [ -n "$n" ] || continue
  open=$((open + 1))
  set +e; out="$(report "$n")"; rc=$?; set -e
  [ "$rc" = 3 ] && exit 3
  printf '%s\n' "$out"
  v="$(printf '%s\n' "$out" | awk '/^PR STATE VERDICT /{print $4; exit}')"
  case "$v" in
    UNBUILT) unbuilt=$((unbuilt + 1)) ;;
    RED)     red=$((red + 1)) ;;
    PENDING) pending=$((pending + 1)) ;;
    GREEN)   green=$((green + 1)) ;;
  esac
  r="$(rank_of "$v")"
  if [ "$r" -gt "$worst" ]; then worst="$r"; worst_rc="$rc"; fi
done

# An empty sweep is a true answer here and says so in words — unlike `gh pr checks`, whose
# silence is the whole complaint this tool was built out of.
printf 'PR STATE SWEEP repo=%s open=%d unbuilt=%d red=%d pending=%d green=%d\n' \
  "$REPO" "$open" "$unbuilt" "$red" "$pending" "$green"
[ "$open" -eq 0 ] && echo "  (no open pull requests — nothing was judged, and that is the reading, not a pass)"
exit "$worst_rc"
