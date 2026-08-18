#!/usr/bin/env bash
# tools/prstate.sh — a pull request has a third state, and `gh pr checks` cannot say it (#1004)
#
# Usage: tools/prstate.sh N | --pr N     judge one pull request
#        tools/prstate.sh --reverts [REF]  does this branch revert a line REF added?
#        tools/prstate.sh --revertsweep [STATE]  the same question over every pull request
#        tools/prstate.sh                judge every open pull request
#        tools/prstate.sh --for OWNER/NAME   name the repository (default: origin)
#        tools/prstate.sh --selftest     run the judge's own cases; no token, no network
#        tools/prstate.sh --schedules    the same judgement over scheduled runs
#        tools/prstate.sh --sweepcost [N]  what the probe sweep has actually cost
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
REVERT_REF=""
REVERT_STATE=merged
SWEEP_RUNS=20
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
    --schedules) MODE=schedules; shift ;;
    --reverts) MODE=reverts; REVERT_REF="${2:-}"; case "$REVERT_REF" in ""|-*) REVERT_REF=""; shift ;; *) shift 2 ;; esac ;;
    --revertsweep) MODE=revertsweep; REVERT_STATE="${2:-merged}"; case "$REVERT_STATE" in ""|-*) REVERT_STATE=merged; shift ;; *) shift 2 ;; esac ;;
    --sweepcost) MODE=sweepcost; SWEEP_RUNS="${2:-20}"; case "$SWEEP_RUNS" in ""|*[!0-9]*) SWEEP_RUNS=20; shift ;; *) shift 2 ;; esac ;;
    # READ TO THE END OF THE USAGE BLOCK, not to a line number. `sed -n '2,7p'`
    # stopped exactly where the clause ended on the day it was written, so a door
    # added below line 7 would be absent from `--help` while sitting in the file
    # two lines above the parser that accepts it — which is how `--schedules` came
    # to be a flag this tool answers and never mentions (#1382).
    -h|--help) awk 'NR==1 {next} !/^#/ {exit} /^#$/ {if (++blank == 2) exit} {print}' "$0"; exit 0 ;;
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
  local pr_state="$1" mergeable="$2" rows="$3" base="${4:-main}" patterns="${5-main}"
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
  #
  # An EMPTY pattern set is "I could not read the triggers", not "nothing is
  # eligible". Those two readings differ by every pull request in the
  # repository, and the second one is reachable by a typo in a workflow, a
  # renamed directory, or running this tool from the wrong working directory.
  # Silence falls back to the old verdict rather than condemning everything.
  if [ "$total" -eq 0 ] && [ -n "$patterns" ] && ! base_eligible "$base" "$patterns"; then
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

# ---- the schedule question (#1233) -------------------------------------------
#
# A workflow on a cron is the only kind that can be COMPLETELY silent and still
# look installed. `determinism.yml` landed carrying `probes/bench.sh --twice` —
# the one pass in this tree that byte-compares a probe against a second run of
# itself — and had run zero times when this was written, because its first
# scheduled firing had not arrived and nobody had dispatched it. That is #1203's
# shape with the clock instead of a syntax error: there, a workflow that could
# not start looked like one that did not apply; here, one that has not started
# looks the same.
#
# The period is derived from the cron rather than configured, because a period
# stated beside the cron is a second copy of the cron.
#
# period_of <cron> -> days
#   `0 4 * * 0`   day-of-week pinned   -> 7
#   `0 4 1 * *`   day-of-month pinned  -> 31
#   `0 4 * * *`   neither              -> 1
#
# Not a cron parser. A parser would be better and is not free (D-009's habit),
# and these three shapes are what a repository schedule actually looks like. A
# cron this cannot read reports its shape rather than guessing a period.
period_of() {                   # period_of <cron-expression>
  local dom dow
  dom="$(printf '%s' "$1" | awk '{print $3}')"
  dow="$(printf '%s' "$1" | awk '{print $5}')"
  if [ "${dow:-*}" != '*' ]; then printf '7'; return 0; fi
  if [ "${dom:-*}" != '*' ]; then printf '31'; return 0; fi
  printf '1'
}

# judge_schedule <period-days> <age-days|none> -> VERDICT<TAB>why
#
# Pure, so the arithmetic is testable without a token. `none` is not "overdue by
# a lot" — it is a workflow that has never run at all, and the two want different
# words: one is a schedule that slipped, the other is a schedule nobody has ever
# seen work.
#
# The tolerance is 1.5x the period. A run that fires at 04:00 and is read at
# 03:00 seven days later is not late, and a schedule GitHub skipped once is.
judge_schedule() {
  local period="$1" age="$2"
  if [ "$age" = none ]; then
    printf 'NEVER_RAN\tperiod=%sd\n' "$period"; return 0
  fi
  # Integer arithmetic only: 2 * age > 3 * period is age > 1.5 * period. The
  # first draft wrote `3 * age > 2 * period`, which is age > 0.67 * period —
  # a weekly schedule read as overdue five days in. The cases below caught it,
  # which is the entire reason the tolerance is bracketed on both sides rather
  # than asserted once.
  if [ $((2 * age)) -gt $((3 * period)) ]; then
    printf 'OVERDUE\tage=%sd period=%sd\n' "$age" "$period"; return 0
  fi
  printf 'CURRENT\tage=%sd period=%sd\n' "$age" "$period"
}
# NOT_ELIGIBLE outranks UNBUILT in a sweep: an unbuilt PR is waiting for a run,
# and this one is waiting for nothing at all.
rank_of()  { case "$1" in NOT_ELIGIBLE) echo 4 ;; UNBUILT) echo 3 ;; RED) echo 2 ;; PENDING) echo 1 ;; *) echo 0 ;; esac; }

# The bases the workflows will actually run for, READ FROM THE WORKFLOWS. The
# alternative is a list beside them, which is a second copy of a fact and the
# thing this tree keeps finding stale. Handles `branches: [main]` and
# `branches: ['main', 'unit/**']` alike.
eligible_bases() {
  # Both spellings YAML allows, because a tool that reads one of them and
  # returns nothing for the other does not report "I could not read this" — it
  # reports an empty allow-list, and an empty allow-list makes EVERY pull
  # request ineligible. The caller's empty-set rule is the other half of that
  # guard; this half is simply reading the file properly.
  #
  #     branches: [main]              flow
  #     branches:                     block
  #       - main
  #
  # `branches-ignore:` is deliberately not read. It inverts the meaning, and a
  # parser that treated its list as an allow-list would be confidently backwards
  # — the one failure mode worse than reading nothing.
  awk '
    /^[[:space:]]*branches:[[:space:]]*\[/ {
      line = $0
      sub(/.*branches:[[:space:]]*\[/, "", line)
      sub(/\].*/, "", line)
      n = split(line, parts, ",")
      for (i = 1; i <= n; i++) { print parts[i] }
      inblock = 0; next
    }
    /^[[:space:]]*branches:[[:space:]]*$/ { inblock = 1; next }
    inblock && /^[[:space:]]*-[[:space:]]*[^[:space:]]/ {
      item = $0
      sub(/^[[:space:]]*-[[:space:]]*/, "", item)
      print item
      next
    }
    { inblock = 0 }
  ' .github/workflows/*.yml 2>/dev/null \
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

# THE ARITHMETIC IS A PURE FUNCTION OF A LIST, so it can be watched being wrong
# without a token and without a network (#1348). It sits ABOVE `selftest` because
# bash binds a function when it reaches the definition and the suite runs before the
# fetching half is defined — the split is fetch-then-compute, and only the compute
# half has cases, which is the division `checkage.sh` makes for the same reason.
sweep_stats() {                 # sweep_stats <budget> <secs, one per line>
  local budget="$1" list="$2"
  printf '%s\n' "$list" | awk -v budget="$budget" '
    /^[0-9]+$/ { v[n++] = $1; total += $1; if ($1 > budget) over++ }
    END {
      if (n == 0) { print "SWEEP COST UNREADABLE runs=0"; exit }
      # Insertion sort: n is twenty-odd and this keeps the whole statistic in one
      # awk with no pipe to lose an exit status in.
      for (i = 1; i < n; i++) { x = v[i]; for (j = i - 1; j >= 0 && v[j] > x; j--) v[j+1] = v[j]; v[j+1] = x }
      # The LOWER median on an even count, stated rather than left to be inferred:
      # a mean of the middle two would invent a duration nothing measured.
      median = v[int((n - 1) / 2)]
      printf "SWEEP COST runs=%d min=%d median=%d mean=%d max=%d budget=%d over=%d headroom=%d%% source=step\n",
             n, v[0], median, int(total / n), v[n-1], budget, over + 0, int((budget - v[n-1]) * 100 / budget)
    }'
}

reverts_in() {                    # reverts_in <lines main added> <the branch's file>
  # `comm`, NOT `grep`. Two spellings failed before this one, both silently or
  # loudly on the same input: a per-line `grep -qxF` over the whole file, and a
  # `grep -Fxvf` with the file as a patterns list. Both print `grep: out of memory`
  # on `probes/README.md`, where a catalog row is nine thousand characters on ONE
  # line (#1370) — a regex engine given that as a fixed pattern gives up.
  #
  # `comm` is set arithmetic over sorted lines and has no pattern to compile, so the
  # length of a line is not its problem. Sorting loses the order, which this question
  # does not use: it asks whether a line is PRESENT, not where.
  local added="$1" content="$2" a b lost
  a="$(mktemp "${TMPDIR:-/tmp}/prstate-add.XXXXXX")"
  b="$(mktemp "${TMPDIR:-/tmp}/prstate-now.XXXXXX")"
  printf '%s\n' "$added" | grep -v '^[[:space:]]*$' | sort -u > "$a"
  printf '%s\n' "$content" | sort -u > "$b"
  lost="$(comm -23 "$a" "$b" | grep -c . || true)"
  rm -f "$a" "$b"
  printf '%s\n' "${lost:-0}"
}

selftest() {
  local pass=0 fail=0 seen=""
  check() { # check <name> <pr-state> <mergeable> <rows> <want> [base] [patterns]
    local got
    # `${7-main}`, not `${7:-main}`: an EMPTY pattern set is a case this suite
    # has to be able to express, and `:-` would silently turn it into "main".
    got="$(judge "$2" "$3" "$4" "${6:-main}" "${7-main}" | awk -F'\t' '{printf "%s runs=%s green=%s red=%s pending=%s why=%s", $1,$2,$3,$4,$5,$6}')"
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
  # An unreadable trigger set must not condemn every pull request. A parser that
  # reads one YAML spelling and not the other returns exactly this, and the
  # difference between the two readings is the whole repository (found by this
  # PR's own adversarial pass).
  check no-patterns-readable    OPEN MERGEABLE   ''  \
    'UNBUILT runs=0 green=0 red=0 pending=0 why=no-run' \
    unit/1206-tool-depth-guard ''

  # The schedule question (#1233), whose whole arithmetic is testable with no
  # token: a period read off a cron, an age in days, and the 1.5x tolerance.
  sched() {                     # sched <name> <cron> <age> <want>
    local got
    got="$(judge_schedule "$(period_of "$2")" "$3" | tr '\t' ' ')"
    seen="$seen ${got%% *}"
    if [ "$got" = "$4" ]; then
      pass=$((pass + 1)); printf 'CASE %-26s OK    %s\n' "$1" "$got"
    else
      fail=$((fail + 1)); printf 'CASE %-26s FAIL  got=[%s] want=[%s]\n' "$1" "$got" "$4"
    fi
  }
  sched weekly-fresh        '0 4 * * 0' 3    'CURRENT age=3d period=7d'
  sched weekly-at-period    '0 4 * * 0' 7    'CURRENT age=7d period=7d'
  # 1.5x of 7 is 10.5, so ten is inside the tolerance and eleven is not. Both,
  # because a tolerance stated and not bracketed is a tolerance nobody checked.
  sched weekly-inside-slack '0 4 * * 0' 10   'CURRENT age=10d period=7d'
  sched weekly-overdue      '0 4 * * 0' 11   'OVERDUE age=11d period=7d'
  sched daily-overdue       '0 4 * * *' 2    'OVERDUE age=2d period=1d'
  sched monthly-fresh       '0 4 1 * *' 20   'CURRENT age=20d period=31d'
  # A workflow that has never run is not "overdue by a lot": one is a schedule
  # that slipped, the other is a schedule nobody has ever seen work — which is
  # exactly the state `determinism.yml` was in on the day it was written.
  sched never-ran           '0 4 * * 0' none 'NEVER_RAN period=7d'

  # THE SWEEP-COST ARITHMETIC (#1348), over synthetic lists. No token, no network:
  # everything above `sweep_stats` fetches and everything below computes, and only
  # the computing half is testable — which is the split `checkage.sh` makes for the
  # same reason.
  cost_case() {                 # cost_case <name> <secs-list> <want-line>
    local got
    got="$(sweep_stats 300 "$2")"
    if [ "$got" = "$3" ]; then
      pass=$((pass + 1)); printf 'CASE %-26s OK    %s\n' "$1" "$got"
    else
      fail=$((fail + 1)); printf 'CASE %-26s FAIL  got=[%s] want=[%s]\n' "$1" "$got" "$3"
    fi
  }
  cost_case cost-one-run '210' \
    'SWEEP COST runs=1 min=210 median=210 mean=210 max=210 budget=300 over=0 headroom=30% source=step'
  # Odd count: the median is the middle element and not an average of anything.
  cost_case cost-odd-median "$(printf '100\n300\n200\n')" \
    'SWEEP COST runs=3 min=100 median=200 mean=200 max=300 budget=300 over=0 headroom=0% source=step'
  # EVEN count takes the LOWER median deliberately: a mean of the middle two would
  # invent a duration nothing measured, which is the shape #1221 objects to.
  cost_case cost-even-median "$(printf '100\n200\n300\n400\n')" \
    'SWEEP COST runs=4 min=100 median=200 mean=250 max=400 budget=300 over=1 headroom=-33% source=step'
  # A sweep AT the budget is within it — `over` counts breaches, and 300 is not one.
  cost_case cost-at-the-budget '300' \
    'SWEEP COST runs=1 min=300 median=300 mean=300 max=300 budget=300 over=0 headroom=0% source=step'
  # Headroom goes NEGATIVE rather than clamping at zero, because "how far past" is
  # the number somebody raising the budget needs and a clamped zero hides it.
  cost_case cost-over-the-budget '450' \
    'SWEEP COST runs=1 min=450 median=450 mean=450 max=450 budget=300 over=1 headroom=-50% source=step'
  # An empty read is refused rather than reported as a fast sweep — #1235's shape:
  # an answer nobody could read is not an answer.
  cost_case cost-nothing-read '' 'SWEEP COST UNREADABLE runs=0'


  # THE REVERT ARITHMETIC (#1118), over two texts. Everything above `reverts_in`
  # asks git; everything below counts, and only the counting half is testable —
  # the same split `sweep_cost` makes and for the same reason.
  revert_case() {                 # revert_case <name> <want> <added> <content>
    local got
    got="$(reverts_in "$3" "$4")"
    if [ "$2" = "$got" ]; then
      pass=$((pass + 1)); printf 'CASE %-26s OK    want=%s got=%s\n' "$1" "$2" "$got"
    else
      fail=$((fail + 1)); printf 'CASE %-26s FAIL  want=%s got=%s\n' "$1" "$2" "$got"
    fi
  }
  revert_case revert:all-present 0 "$(printf 'a\nb')" "$(printf 'x\na\nb\ny')"
  # The live shape: main added two lines and the branch's file has neither.
  revert_case revert:two-lost    2 "$(printf 'a\nb')" "$(printf 'x\ny')"
  revert_case revert:one-lost    1 "$(printf 'a\nb')" "$(printf 'a\ny')"
  # Blank lines are not evidence of anything and must not count as reverted.
  revert_case revert:blanks-skip 0 "$(printf 'a\n\n   \n')" "$(printf 'a')"
  # ORDER IS NOT THE QUESTION. `comm` sorts, which loses order — the check asks
  # whether a line is PRESENT, and a branch that moved a line has not reverted it.
  revert_case revert:reordered   0 "$(printf 'a\nb')" "$(printf 'b\na')"
  # A DUPLICATE is one line, because `sort -u` is the right reading here: main
  # adding the same text twice is not two facts to lose.
  revert_case revert:duplicate   1 "$(printf 'a\na')" "$(printf 'x')"
  # Nothing added is nothing lost, which is the common case: most files a branch
  # touches were not touched by main.
  revert_case revert:nothing-added 0 "" "$(printf 'a\nb')"
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

# ---- the live schedule reading -----------------------------------------------


# WHAT HAS THE SWEEP ACTUALLY COST? (#1348)
#
# `BENCH_BUDGET_SECS=300` bounds the SUM of the probe sweep and nothing bounds a
# row, and #1348 lists three ways to close that — a per-row ratio, a pinned cost
# table, or reporting forever. It also says which question comes first, and that
# nobody had answered it: *has the sweep ever been near 300?* The trailer prints
# `secs=` on every run and nothing keeps them.
#
# WHY THE STEP AND NOT THE LOG. `secs=` lives inside the sweep's own stdout, and
# reading it means fetching a log archive per run. The lane's step timings are on
# the jobs API — started_at and completed_at, one request per run — and the step
# named for the sweep IS the sweep plus process startup. That is a proxy and this
# says so on the line: `source=step`, not `source=trailer`.
#
# WHY A REPORT AND NOT A GATE. Wall clock on a shared runner is not stable, and
# this mode exists to measure how unstable before anyone writes a floor against
# it — #1221's argument in its original form, and the reason the cost line inside
# the sweep is a census. What comes out of here decides which of #1348's three
# options is worth building, or whether 300 is the number that needs revisiting.
SWEEP_STEP='probe sweep'
SWEEP_BUDGET=300
SWEEP_TMP="${TMPDIR:-/tmp}/prstate-sweep.$$"


sweep_cost() {                  # sweep_cost [runs]
  local want="${1:-20}" ids id secs n=0 line over
  ids="$(gh run list --workflow locks.yml --status success --limit "$want" \
           --json databaseId -q '.[].databaseId' 2>/dev/null || true)"
  if [ -z "$ids" ]; then
    echo "SWEEP COST UNREADABLE runs=0 (the API returned no completed locks run)" >&2
    return 3
  fi
  : > "$SWEEP_TMP"
  for id in $ids; do
    # One request per run. `started_at`/`completed_at` are RFC 3339 and are
    # subtracted through epoch seconds, so no `date(1)` dialect is involved —
    # #901's lesson, which `balance.sh` pays for in two spellings.
    secs="$(gh api "repos/${REPO}/actions/runs/$id/jobs" \
              -q ".jobs[].steps[] | select(.name | startswith(\"$SWEEP_STEP\")) | \"\(.started_at) \(.completed_at)\"" \
              2>/dev/null | head -1 | while read -r a b; do
                sa="$(date -u -j -f '%Y-%m-%dT%H:%M:%SZ' "$a" +%s 2>/dev/null || date -u -d "$a" +%s 2>/dev/null)"
                sb="$(date -u -j -f '%Y-%m-%dT%H:%M:%SZ' "$b" +%s 2>/dev/null || date -u -d "$b" +%s 2>/dev/null)"
                [ -n "$sa" ] && [ -n "$sb" ] && echo $(( sb - sa ))
              done)"
    [ -n "$secs" ] || continue
    n=$((n + 1))
    printf '%s\n' "$secs" >> "$SWEEP_TMP"
    printf 'SWEEP run=%s secs=%s\n' "$id" "$secs"
  done
  if [ "$n" -eq 0 ]; then
    echo "SWEEP COST UNREADABLE runs=0 (no run carried a step named \"$SWEEP_STEP\")" >&2
    rm -f "$SWEEP_TMP"
    return 3
  fi
  # THE FETCHER COUNTS AND NOTHING ELSE (#1560). It accumulated `min`, `max`,
  # `total` and `over` while reading, and then handed the same list to
  # `sweep_stats`, which computes all four again — two implementations of one
  # statistic, and only the one with cases was tested. The untested copy used
  #
  #     [ -z "$min" ] || [ "$secs" -lt "$min" ] && min="$secs"
  #
  # which is `([ -z ] || [ -lt ]) && min=` in shell, not the C-like reading a
  # person sees. It was correct for this input by luck. `n` survives because
  # emptiness is the fetcher's own question, and the RETURN CODE is read back
  # off the verdict line rather than counted twice.
  line="$(sweep_stats "$SWEEP_BUDGET" "$(cat "$SWEEP_TMP")")"
  rm -f "$SWEEP_TMP"
  printf '%s\n' "$line"
  # The count is read back with the same parameter expansion locks.yml uses on every
  # suite gate (`ran="${verdict##*cases=}"`), not with a glob: ` over=0 ` would also
  # have to be spelled carefully enough not to match ` over=01 `, which is the exact
  # trap the bench's exact-line greps exist to avoid.
  over="${line##*over=}"; over="${over%% *}"
  [ "$over" = 0 ]
}


# DOES THIS BRANCH REVERT A LINE MAIN ADDED AFTER ITS BASE? (#1118, for #1063)
#
# #1118's table is four instances of one shape in one night, and it says exactly
# where the existing locks reach:
#
#   PR #1059   fifteen files reverted, 482 deletions   nobody — every lock green
#   PR #1086   `movers=18` beside a `three rows` note  a person, resolving by hand
#   #1028      a renamed symbol reverted               a person, on a compile error
#   PR #1117   `movers=18` in a bench row              CI, on the exact-line judge
#
# The one a machine caught was caught because a pin and a reality were compared
# MECHANICALLY. Everywhere the comparison is absent — a reverted file, a renamed
# symbol in an unrelated method, a document's prose — nothing looks. That is
# #1082's thesis wearing different clothes, and a stale base is simply the fastest
# way to make two copies of a fact drift.
#
# WHY NOT A STALENESS ALARM. #1118 says the cheapest useful version is not "this
# branch is old" but "this branch's version of a file it TOUCHES is missing a line
# main added after the merge base". A commit-count heuristic cries wolf on an
# honest old branch that touches nothing contested; this cannot, because a branch
# that does not touch the file is not asked about it — the merge will bring main's
# version and there is nothing to revert.
#
# WHAT IT CANNOT SEE. A line main added and this branch legitimately DELETED. That
# is indistinguishable from a revert without knowing intent, and the direction is
# the noisy one: a deliberate deletion is reported. Reported, never judged, for
# exactly that reason (#1207) — the line names the file and the count, and a
# person decides.
REVERT_BASE=origin/main

# THE ARITHMETIC IS A PURE FUNCTION OF TWO TEXTS, so it can be watched being wrong
# with no git and no network. Everything above it asks git; everything below counts.


# THE SAME QUESTION OVER EVERY PULL REQUEST (#1614). The reader landed with no
# measurement of the population it checks: #1118 counted four instances in one
# night by reading pull requests afterwards, and nobody had counted them since the
# reader existed. Without the number, wiring it into the lane is a guess and
# leaving it out is a guess — `unfalsifiable=`'s path needs a starting figure
# (#1095 -> #1311).
#
# IT WORKS ON MERGED PULL REQUESTS TOO, which is what makes the measurement
# possible at all: GitHub keeps `refs/pull/N/head` after the branch is deleted, so
# a day's landed work can be re-asked the question it was never asked.
#
# THE BASE IS THE ONE THE PULL REQUEST HAD, not today's `main`. Asking a merged
# branch whether it is missing lines `main` has gained SINCE it merged would report
# every one of them, which is not the question — the question is whether it was
# missing lines `main` had at the time.

# THE READER, POINTED AT ANY COMMIT (#1614). `reverts` asks about HEAD; this asks
# about a ref, which is what lets the sweep re-ask a merged pull request the
# question nobody asked it at the time. One body, two entry points: `reverts` is
# `reverts_at HEAD` with a printed verdict.
reverts_at() {                    # reverts_at <ref> [target-ref] — prints the count
  # THE MERGED TREE, NOT THE BRANCH'S (#1614). The first reading asked whether the
  # BRANCH's copy of a file carries every line the target added, and over thirty
  # merged pull requests that reported fifty-eight reverts of which every one
  # checked was false: git's three-way merge keeps the target's lines wherever the
  # branch did not touch them, so a branch that merely SHARES a file with a newer
  # commit looked like it was reverting it.
  #
  # `git merge-tree --write-tree` performs the merge without a working tree and
  # hands back the resulting tree, so the question becomes the only one that
  # matters: after merging, is a line the target added MISSING. That is what #1059
  # actually did — fifteen files staged from a stale base, overwriting them whole —
  # and a merge cannot rescue an overwrite.
  local ref="$1" target="${2:-$REVERT_BASE}" base tree f added lost total=0
  base="$(git merge-base "$target" "$ref" 2>/dev/null || true)"
  [ -n "$base" ] || { printf '0\n'; return 0; }
  tree="$(git merge-tree --write-tree "$target" "$ref" 2>/dev/null || true)"
  # A merge with conflicts writes a tree with markers in it and exits non-zero. That
  # is a conflict, not a revert: git is refusing to guess and a person will resolve
  # it, which is the state this check does not judge.
  [ -n "$tree" ] || { printf '0\n'; return 0; }
  for f in $(git diff --name-only "$base".."$ref" 2>/dev/null); do
    added="$(git diff "$base".."$target" -- "$f" 2>/dev/null | grep '^+[^+]' | sed 's/^+//' || true)"
    [ -n "$added" ] || continue
    lost="$(reverts_in "$added" "$(git show "$tree:$f" 2>/dev/null || true)")"
    total=$((total + lost))
  done
  printf '%s\n' "$total"
}

reverts_sweep() {                 # reverts_sweep [state] [limit]
  local state="${1:-open}" limit="${2:-30}" n head merge target total=0 seen=0 lost ref
  for n in $(gh pr list --state "$state" --limit "$limit" --json number -q '.[].number' 2>/dev/null); do
    ref="prstate-rev/$n"
    git fetch -q origin "pull/$n/head:$ref" --force 2>/dev/null || continue
    head="$(git rev-parse "$ref" 2>/dev/null || true)"
    [ -n "$head" ] || continue
    # THE TARGET IS `main` AS IT WAS WHEN THIS PULL REQUEST WAS ANSWERED, and the
    # first measurement got that wrong: it compared every merged branch against
    # TODAY's `main` and reported 3,932 reverted lines over thirty pull requests —
    # every line every LATER unit added, which no branch could have carried.
    #
    # A merged pull request's `main` is its merge commit's first parent. An open one's
    # is `main` now. Using one target for both is the same mistake this whole mode is
    # about: two copies of a fact, and the wrong one read (#1082).
    target="$REVERT_BASE"
    if [ "$state" = merged ]; then
      merge="$(gh pr view "$n" --json mergeCommit -q '.mergeCommit.oid' 2>/dev/null || true)"
      [ -n "$merge" ] && git fetch -q origin "$merge" 2>/dev/null
      [ -n "$merge" ] && target="${merge}^1"
      git rev-parse "$target" >/dev/null 2>&1 || target="$REVERT_BASE"
    fi
    seen=$((seen + 1))
    lost="$(reverts_at "$head" "$target")"
    total=$((total + lost))
    [ "$lost" -eq 0 ] || printf 'REVERTS pr=%s reverted=%s target=%s\n' "$n" "$lost" "${target:0:12}"
    git branch -D "$ref" >/dev/null 2>&1 || true
  done
  printf 'REVERTS SWEEP state=%s asked=%d reverted=%d\n' "$state" "$seen" "$total"
  [ "$total" -eq 0 ]
}

reverts() {                       # reverts [base-ref]
  # ONE BODY, TWO ENTRY POINTS (#1614): this is `reverts_at HEAD` with a verdict
  # line and a file count, so the branch reading and the sweep reading cannot
  # disagree about what a revert IS — which is the mistake this whole mode is about.
  local base_ref="${1:-$REVERT_BASE}" base files total
  base="$(git merge-base "$base_ref" HEAD 2>/dev/null || true)"
  if [ -z "$base" ]; then
    echo "FATAL no merge base with $base_ref" >&2
    return 3
  fi
  files="$(git diff --name-only "$base"...HEAD | grep -c . || true)"
  total="$(reverts_at HEAD "$base_ref")"
  [ "$total" -eq 0 ] || printf 'REVERT this branch loses %s line(s) %s added after it was cut\n' "$total" "$base_ref"
  printf 'REVERTS VERDICT %s base=%s files=%d reverted=%d\n' \
    "$([ "$total" -eq 0 ] && printf NONE || printf FOUND)" "${base:0:12}" "${files:-0}" "$total"
  [ "$total" -eq 0 ]
}

schedules() {
  local f cron period last age verdict why worst=0 n=0
  for f in .github/workflows/*.yml .github/workflows/*.yaml; do
    [ -f "$f" ] || continue
    # The cron line, if this workflow has one. `awk` rather than a YAML reader,
    # for D-009's reason and because the shape is one line.
    cron="$(awk -F"'" '/^[[:space:]]*-[[:space:]]*cron:/ {print $2; exit}' "$f")"
    [ -n "$cron" ] || continue
    n=$((n + 1))
    period="$(period_of "$cron")"
    # The most recent run of any kind — a dispatch counts, because the question
    # is whether the pass has been TAKEN, not whether the clock delivered it.
    last="$(gh run list --workflow "$(basename "$f")" --limit 1 \
              --json createdAt -q '.[0].createdAt' 2>/dev/null || true)"
    if [ -z "$last" ] || [ "$last" = null ]; then
      age=none
    else
      # Days between then and now, computed from epoch seconds so no date(1)
      # dialect is involved (#901 is why that sentence is here).
      local then now
      then="$(date -u -j -f '%Y-%m-%dT%H:%M:%SZ' "$last" +%s 2>/dev/null \
              || date -u -d "$last" +%s 2>/dev/null || true)"
      now="$(date -u +%s)"
      if [ -z "$then" ]; then
        echo "SCHEDULE $(basename "$f") UNREADABLE stamp=$last"
        continue
      fi
      age=$(( (now - then) / 86400 ))
    fi
    IFS=$'\t' read -r verdict why <<< "$(judge_schedule "$period" "$age")"
    printf 'SCHEDULE %s %s cron="%s" %s\n' "$(basename "$f")" "$verdict" "$cron" "$why"
    case "$verdict" in NEVER_RAN|OVERDUE) worst=1 ;; esac
  done
  printf 'PR STATE SCHEDULES VERDICT %s scheduled=%d\n' \
    "$([ "$worst" = 0 ] && printf CURRENT || printf OVERDUE)" "$n"
  [ "$worst" = 0 ]
}

if [ "$MODE" = schedules ]; then schedules; exit $?; fi
# Before the repository is resolved: this mode reads git and never the API.
if [ "$MODE" = reverts ]; then reverts "${REVERT_REF:-$REVERT_BASE}"; exit $?; fi
if [ "$MODE" = revertsweep ]; then reverts_sweep "${REVERT_STATE:-merged}" 30; exit $?; fi

# ---- the live reading --------------------------------------------------------

if [ -z "$REPO" ]; then
  REPO="$(git remote get-url origin 2>/dev/null | sed -E 's#.*github\.com[/:]##; s#\.git$##' || true)"
fi
[[ "$REPO" =~ ^[A-Za-z0-9._-]+/[A-Za-z0-9._-]+$ ]] \
  || { echo "FATAL cannot tell which repository this is; pass --for OWNER/NAME" >&2; exit 2; }

# AFTER the repository is resolved, because this mode reads the jobs API by slug.
if [ "$MODE" = sweepcost ]; then sweep_cost "${SWEEP_RUNS:-20}"; exit $?; fi

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
