#!/usr/bin/env bash
# tools/balance.sh — is the day's work balanced? (D-060)
#
# Usage: tools/balance.sh [--account|--repo] [--for OWNER/NAME]
#                         [--week|--month|--days N] [--events] [YYYY-MM-DD]
#        tools/balance.sh --datecheck          (the day arithmetic, no token needed)
#        tools/balance.sh --rulercheck         (the ruler, no token needed)
#        tools/balance.sh --judgecheck         (the deficit advice, no token needed)
#
# The law: the four contribution kinds GitHub counts — commits, issues, pull
# requests, reviews — each hold a quarter of the day. The reasoning, not the
# arithmetic, is the point: a day of only PRs is a day nobody planned, a day of
# only issues is a day nobody shipped, a day without reviews is a day nothing
# was doubted, and a day without commits is a day nothing was built. This meter
# reads the same contribution API the profile graph reads, so it cannot flatter
# us.
#
# WHAT IT COUNTS, STATED PLAINLY. `contributionsCollection` is an *account*
# statistic: it spans every repository the account touched that day. So the
# default verdict answers "was the ACCOUNT balanced", which is not the same
# sentence as "was matrix-sim balanced" — a heavy day in another repository
# flatters this project's shape, and nothing in the output used to say so.
# Every line now carries `scope=`, and every run prints a SCOPE line comparing
# the account reading against this repository's own, so the difference is
# reported rather than assumed away:
#
#   --account   (default)  the account's whole day, all repositories
#   --repo                 only this repository's contributions
#   --for O/N              name the repository explicitly (default: origin)
#
# The default stays `account`, and the argument for it no longer lives here:
# D-060's errata of 2026-08-13 (#821) records the readings it was settled on
# and what the flip would cost. The SCOPE line is what removes the silence in
# the tool: when the two readings differ it says by how much, so a green
# verdict can never quietly mean somewhere else.
#
# WHOSE DAY IT COUNTS. The query roots at `viewer`, so it reads whoever holds
# the token. Every run therefore opens with a SUBJECT line naming that login,
# and refuses (exit 6) unless it owns the repository under measurement — a
# reading of the wrong account is not a small error, it is a different day.
#
# WHICH DAYS IT JUDGES. Some days belong to one leg by nature — a skeptic pass
# IS a review day, a decomposition sweep IS an issue day — so judging one square
# of the calendar calls three legs LAGGING and is wrong about each. --week (7),
# --month (30) and --days N (1-90) judge the same four quarters over a ROLLING
# window ending on the named day. Each window prints its days one BALANCE_DAY
# line each, judged alone, because the inversion a window claims to fix is only
# visible beside what the days said by themselves; a WINDOW line then re-adds
# those days and checks the sum against the window's own totals, and refuses
# both readings if they disagree. The days and the span totals come out of ONE
# query, so that check cannot fire on work that lands while the tool runs.
#
# HOW IT MEASURES, AND AGAINST WHAT. Two separate things used to make
# `verdict=OK` unreachable, and #828 named one of them.
#
# The one it named — the RULER. The meter counted contribution EVENTS, so one
# scripted day dominated any span: 2026-08-11 produced 603 issues, 63% of that
# whole week, and widening the window from two days to thirty changed nothing.
# A window averages the SHAPE of adjacent days; it cannot average away a day
# whose VOLUME exceeds every other day put together. So each day now contributes
# its own shape, weighted equally — 1000 per mille split across its four legs,
# and the window's leg is the sum of those splits over the days that had work.
# A sweep is then worth one day, and the most any single day can move a leg is
# 1000/days: bounded, where the event ruler was not. A single day IS its own
# shape, so a one-day reading is arithmetically identical under both rulers, and
# `--events` restores the old ruler for reproducing any earlier reading.
#
# The cost of that, stated rather than buried: a day holding ONE contribution
# votes as loudly as a full day. 2026-08-09 held a single pull request and
# nothing else, and it carries a whole day's 1000‰ of PR shape into its week —
# which is why the week ending 2026-08-13 reads its PR leg well over 300‰ by
# shape and just over 120‰ by events. The old distortion was unbounded and this
# one is bounded by 1000/days, which is the whole trade; D-060's errata of
# 2026-08-13 records it as an open cost with the figures of the day.
#
# The one #828 did not name — the TARGET. A leg used to clear the bar at
# `4*leg >= total`, and four legs summing to the total can all satisfy that only
# at exact equality: `verdict=OK` demanded 250‰/250‰/250‰/250‰ to the artifact,
# and a day of 5 commits, 5 issues, 5 PRs and 4 reviews read LAGGING. Changing
# only the ruler would have shipped a meter that still could not say OK, with a
# well-argued reason for it. So a leg is now THIN below HALF its quarter — under
# 125‰, an eighth of the whole. D-060's own English is "each holding ~25%" and
# every failure it names is an ABSENCE; the meter had turned "about a quarter"
# into "at least a quarter on all four at once", which is a different sentence
# and an unsatisfiable one. The floor is one-sided on purpose: a leg can only be
# large at another leg's expense, and that expense is what the floor reads.
#
# Both moves are carried by D-060's errata of 2026-08-13; the tool executes a
# ruler the record declares, it does not choose one.
#
# WHICH BUTTON SHAPED IT. The merge strategy is a term of this law, not a
# preference about history (D-061). A merge commit is a second authored commit
# per unit, so under it the commit leg runs at about twice the PR leg and the
# four quarters stop being a target that is missed and become one that cannot be
# hit. Every run therefore reads the repository's own merge settings and prints
# them on the SCOPE line: a verdict can be read wrong for a hundred reasons, but
# never again without the button that shaped it being on the same screen.
#
# WHAT THE ADVICE IS AN ANSWER TO. The DEFICIT line's four numbers each solve
# ONE leg with the other three standing still, so under the artifact ruler they
# are four answers to four different days and adding two of them is not a plan
# (#952): every artifact added enlarges the day the other legs are measured
# against, and work done on a lagging leg can push a leg that was ABOVE the floor
# under it. The line now says that about itself, and a second line — PLAN —
# carries the set that does compose: all four solved against the day they create
# together, which is a fixed point where no leg is thin and therefore reaches
# verdict=OK by construction. `--judgecheck` executes both readings over pinned
# day vectors, so the difference between them is a run and not a claim.

set -euo pipefail

SCOPE=account
REPO=""
DAY=""
SPAN=1
SPAN_NAME=day
DATECHECK=0
RULERCHECK=0
JUDGECHECK=0
EVENTS=0
for arg in "$@"; do
  case "$arg" in
    --account)   SCOPE=account ;;
    --datecheck) DATECHECK=1 ;;
    --rulercheck) RULERCHECK=1 ;;
    --judgecheck) JUDGECHECK=1 ;;
    --events)    EVENTS=1 ;;
    --repo)    SCOPE=repo ;;
    --week)    SPAN=7;  SPAN_NAME=week ;;
    --month)   SPAN=30; SPAN_NAME=month ;;
    --days)    SPAN="__next__"; SPAN_NAME=days ;;
    --for)     REPO="__next__" ;;
    -*)        echo "FATAL unknown flag: $arg" >&2; exit 2 ;;
    *)
      if [ "$REPO" = "__next__" ]; then REPO="$arg"
      elif [ "$SPAN" = "__next__" ]; then SPAN="$arg"
      else DAY="$arg"; fi ;;
  esac
done
# `--for` with nothing after it must not quietly become "use origin" — a tool
# arguing against silent scope should not have a silent scope of its own.
[ "$REPO" = "__next__" ] && { echo "FATAL --for wants OWNER/NAME after it" >&2; exit 2; }
[ "$SPAN" = "__next__" ] && { echo "FATAL --days wants a number after it" >&2; exit 2; }
[[ "$SPAN" =~ ^[0-9]+$ ]] && (( SPAN >= 1 && SPAN <= 90 )) \
  || { echo "FATAL --days wants 1..90 (one query per day; 90 is where that stops being polite)" >&2; exit 2; }
DAY="${DAY:-$(date -u +%F)}"   # -u: the query asks in UTC, so the default day must be UTC too
[[ "$DAY" =~ ^[0-9]{4}-[0-9]{2}-[0-9]{2}$ ]] || { echo "FATAL date must be YYYY-MM-DD" >&2; exit 2; }

# ONE DATE ARITHMETIC, TWO DIALECTS. `date -d` is GNU coreutils; macOS ships
# BSD date, which spells the same subtraction `-j -f FMT -v-Nd`. This tool was
# written and merged on a macOS box and CI runs ubuntu-latest with no tools/
# step, so the GNU-only idiom was dead on the one machine that would ever type
# `tools/balance.sh` and green on the one machine that never did (#901).
#
# The BSD branch parses NOON rather than midnight and does not ask for -u. Both
# choices are load-bearing. `-j -f %Y-%m-%d` fills the time-of-day from the
# clock, so `-u` would re-express a local wall time as UTC and hand back the
# previous day whenever the operator's offset had not yet caught up with the
# date — a meter that reads yesterday between 00:00 and 03:00 in Istanbul and is
# right the rest of the time is worse than one that refuses. Pinning noon and
# staying local makes it pure calendar arithmetic, which is what a day offset
# is; noon also sits far enough from either edge that a DST shift cannot move
# the date. The GNU branch keeps -u and midnight exactly as it was, so a Linux
# reading taken before this change is byte-identical to the same reading now.
#
# The dialect is decided ONCE, here, against a day whose answer is already
# known — 2000-03-01 minus one day is 2000-02-29, a leap day in a century year,
# which is the arithmetic a wrong implementation gets wrong. Probing by exit
# code alone would accept a date(1) that takes the flags and computes something
# else; probing by answer will not — and that is not hypothetical here. BSD
# date silently IGNORES a `-v` adjustment written after the operand:
#
#   date -j -f FMT '2000-03-01 12:00:00' -v-1d +%F   ->  Wed Mar  1 ...   exit 0
#   date -j -v-1d -f FMT '2000-03-01 12:00:00' +%F   ->  2000-02-29       exit 0
#
# Same box, same flags, exit 0 both times, one of them a whole day wrong. So
# the adjustment goes before the operand, and the probe reads the answer.
#
# Up here rather than lazily inside the helper is also the only place the
# dialect CAN be decided: every call site reads `$(day_minus ...)`, and a
# variable set inside a command substitution dies with the subshell that set it.
if [ "$(date -u -d '2000-03-01 -1 days' +%F 2>/dev/null)" = 2000-02-29 ]; then
  DATE_DIALECT=gnu
elif [ "$(date -j -v-1d -f '%Y-%m-%d %H:%M:%S' '2000-03-01 12:00:00' +%F 2>/dev/null)" = 2000-02-29 ]; then
  DATE_DIALECT=bsd
else
  echo "FATAL this date(1) speaks neither GNU (-d) nor BSD (-j -f -v) day arithmetic." >&2
  echo "      both dialects were asked for the day before 2000-03-01 and neither said 2000-02-29;" >&2
  echo "      refusing to compute a window from a calendar this box cannot do." >&2
  exit 2
fi

day_minus() {                   # day_minus <YYYY-MM-DD> <N> -> the day N days earlier
  local day="$1" n="$2" out=""
  case "$DATE_DIALECT" in
    gnu) out="$(date -u -d "$day -$n days" +%F 2>/dev/null)" || out="" ;;
    bsd) out="$(date -j -v-"${n}"d -f '%Y-%m-%d %H:%M:%S' "$day 12:00:00" +%F 2>/dev/null)" || out="" ;;
  esac
  # A dialect that answers with something that is not a date is a dialect that
  # did not answer. The regex is the guard, not the exit code: BSD date prints
  # a usage line and can leave the substitution non-empty.
  #
  # `exit 2` here ends the SUBSHELL the caller's $(...) opened, not the script —
  # so every call site pairs it with `|| exit 2`. Both halves are needed: the
  # subshell owns the message, the caller owns the death.
  [[ "$out" =~ ^[0-9]{4}-[0-9]{2}-[0-9]{2}$ ]] || {
    echo "FATAL cannot compute the day $n before $day (dialect=$DATE_DIALECT)" >&2
    exit 2
  }
  printf '%s' "$out"
}

# --datecheck: the day arithmetic, judged against answers a calendar already
# settled, with no token and no network in the way. It exists because #901 was
# not a bug anyone could have read off the diff — the tool was green on the box
# that never ran it and dead on the box that did, and only an executed case
# tells those two apart. Every offset the flags can produce is represented:
# 0 (the plain single-day default, the path the GNU idiom also killed), 6
# (--week), 29 (--month), 89 (--days 90). The rest are the places calendars
# break: both century rules, both year and month edges, and the two DST
# Sundays that the noon pin exists to survive.
if (( DATECHECK )); then
  FAILED=0 CASES=0
  while read -r base off want; do
    [ -n "$base" ] || continue
    CASES=$((CASES + 1))
    got="$(day_minus "$base" "$off")" || exit 2
    if [ "$got" = "$want" ]; then
      printf 'DATECHECK %s -%-2s => %s OK\n' "$base" "$off" "$got"
    else
      printf 'DATECHECK %s -%-2s => %s WANT %s FAIL\n' "$base" "$off" "$got" "$want"
      FAILED=$((FAILED + 1))
    fi
  done <<'CASES'
2000-03-01 1  2000-02-29
1900-03-01 1  1900-02-28
2026-01-01 1  2025-12-31
2026-03-01 1  2026-02-28
2026-08-13 0  2026-08-13
2026-08-13 6  2026-08-07
2026-08-13 29 2026-07-15
2026-08-13 89 2026-05-16
2026-03-29 1  2026-03-28
2026-10-25 1  2026-10-24
2026-11-01 1  2026-10-31
CASES
  if (( FAILED == 0 )); then
    printf 'DATECHECK VERDICT PASS dialect=%s cases=%d\n' "$DATE_DIALECT" "$CASES"
    exit 0
  fi
  printf 'DATECHECK VERDICT FAIL dialect=%s cases=%d failed=%d\n' "$DATE_DIALECT" "$CASES" "$FAILED"
  exit 5
fi

# ─── the ruler and the target ────────────────────────────────────────────────
# These four functions are the whole judgement, and they need no token, no
# network and no repository — which is why they sit up here, above everything
# that does, and why --rulercheck below can execute them in a `git archive` tree.

FLOOR_DIV=8            # a leg is thin below total/FLOOR_DIV — half of a quarter

# How far a leg is from the floor, in the unit the caller is measuring in.
#
#   grow    adding artifacts grows the day, so the answer is the smallest n
#           with (have+n)*8 >= total+n, i.e. ceil((total - 8*have)/7). This is
#           the reading a single day and the --events ruler get. It solves one
#           leg with the other three standing still, which is an alternatives
#           list and not a shopping list — `together` below is the shopping
#           list, and the DEFICIT line says which of the two it is printing
#           (#952).
#   fixed   the day-shape ruler moves per mille around inside a total that is
#           fixed at 1000 per working day, so the shortfall is a plain distance:
#           ceil(total/8) - have. Nothing grows, so nothing has to be solved for.
short_by() {                    # short_by <have> <total> <grow|fixed>
  local n
  if [ "$3" = fixed ]; then
    n=$(( ($2 + FLOOR_DIV - 1) / FLOOR_DIV - $1 ))
  else
    n=$(( ($2 - FLOOR_DIV * $1 + FLOOR_DIV - 2) / (FLOOR_DIV - 1) ))
  fi
  (( n < 0 )) && n=0
  printf '%d' "$n"
}

# Judge four legs. Echoes: verdict lag gap n_c n_i n_p n_r
judge() {                       # judge <c> <i> <p> <r> <grow|fixed>
  local c="$1" i="$2" p="$3" r="$4" mode="$5"
  local t=$((c + i + p + r))
  if (( t == 0 )); then echo "EMPTY - 0 0 0 0 0"; return; fi
  local nc ni np nr lag="" gap=0 v="OK"
  nc="$(short_by "$c" "$t" "$mode")"; ni="$(short_by "$i" "$t" "$mode")"
  np="$(short_by "$p" "$t" "$mode")"; nr="$(short_by "$r" "$t" "$mode")"
  local leg n
  for leg in "commit:$nc" "issue:$ni" "pr:$np" "review:$nr"; do
    n="${leg##*:}"
    if (( n > gap )); then gap="$n"; lag="${leg%%:*}"; fi
  done
  (( gap > 0 )) && v="LAGGING:$lag"
  echo "$v ${lag:--} $gap $nc $ni $np $nr"
}

# The set that composes, under the grow ruler. `short_by grow` answers "this leg,
# with the other three standing still", and four such answers are four answers to
# four different days: acting on one of them enlarges the day the others were
# measured against, so a second number goes stale the moment the first is acted
# on, and a leg that was ABOVE the floor can be pushed under it by work done
# somewhere else (#952).
#
# This solves all four at once. Each round takes the floor of the day the plan so
# far creates and lifts every leg sitting under it; the day grows, the floor
# grows with it, and the round repeats until nothing moves. Where it stops is a
# day in which no leg is thin — that IS the fixed point's definition — so the set
# it prints reaches verdict=OK by construction rather than by hope, and lifting
# only the legs that are under the floor makes it the smallest such set.
#
# It terminates because a plan cannot outrun the day it is added to: every leg
# ends a round at most at F = ceil((total+S)/FLOOR_DIV), so with four legs and a
# floor of an eighth S <= 4F <= (total+S)/2 + 4, i.e. S <= total + 8, while every
# round that changes anything adds at least 1. That bound is a property of the
# floor, not of this loop: a floor above a quarter has no fixed point at all and
# the legs would run away until they overflowed. So both exits are guarded, and
# both echo NOTHING rather than a set this function has not proved — the caller
# prints a WARN in place of a plan it was not given. Echoes: n_c n_i n_p n_r
together() {                    # together <c> <i> <p> <r> -> the four taken at once
  local ac="$1" ai="$2" ap="$3" ar="$4" t f k moved
  (( ac + ai + ap + ar > 0 )) || { echo "0 0 0 0"; return; }   # no day, nothing to plan
  for ((k = 0; k < 64; k++)); do
    t=$((ac + ai + ap + ar))
    (( t > 0 )) || return                      # only reachable by runaway: say nothing
    f=$(( (t + FLOOR_DIV - 1) / FLOOR_DIV ))   # have < F is exactly FLOOR_DIV*have < total
    moved=0
    if (( ac < f )); then ac=$f; moved=1; fi
    if (( ai < f )); then ai=$f; moved=1; fi
    if (( ap < f )); then ap=$f; moved=1; fi
    if (( ar < f )); then ar=$f; moved=1; fi
    if (( moved == 0 )); then
      echo "$((ac - $1)) $((ai - $2)) $((ap - $3)) $((ar - $4))"
      return
    fi
  done
}

pct() { printf '%d' $(( ($1 * 1000 + $2 / 2) / $2 )); }   # per mille, rounded

# A day expressed as 1000 per mille of itself — the day-shape ruler's unit.
# Flooring four shares leaves 0..3 per mille unallocated; the remainder goes to
# the day's LARGEST leg, which is the leg this ruler exists to stop from
# dominating. Rounding therefore always runs against the argument the ruler is
# making, and can never be accused of having made it.
day_shape() {                   # day_shape <c> <i> <p> <r> -> four shares summing to 1000
  local t=$(( $1 + $2 + $3 + $4 ))
  (( t > 0 )) || { echo "0 0 0 0"; return; }
  local sc=$(( $1 * 1000 / t )) si=$(( $2 * 1000 / t ))
  local sp=$(( $3 * 1000 / t )) sr=$(( $4 * 1000 / t ))
  local left=$(( 1000 - sc - si - sp - sr )) big=$1 which=c
  (( $2 > big )) && { big=$2; which=i; }
  (( $3 > big )) && { big=$3; which=p; }
  (( $4 > big )) && { big=$4; which=r; }
  case "$which" in
    c) sc=$((sc + left)) ;; i) si=$((si + left)) ;;
    p) sp=$((sp + left)) ;; r) sr=$((sr + left)) ;;
  esac
  echo "$sc $si $sp $sr"
}

# --rulercheck: the ruler and the floor, judged against day vectors whose right
# answers are settled by arithmetic rather than by an API. It exists for the
# same reason --datecheck does: #828's whole complaint is a claim about what the
# meter CAN say, and a claim about reachability is only worth what an executed
# case makes it worth. No token, no network, no repository — so CI runs it, and
# so does a pinned tree.
#
# The two rows that carry the unit are the pair of "sweep beside six ordinary
# days": the same seven days read LAGGING:review by events and OK by day-shape.
# That pair IS "verdict=OK must be reachable by working well", executed.
#
# The two "one-PR days" rows are the cost, asserted so it is a known property
# and not a future surprise: under day-shape three days holding a single pull
# request each outvote one balanced day, and the reading flips the other way.
if (( RULERCHECK )); then
  FAILED=0 CASES=0
  while IFS='|' read -r name ruler want days; do
    case "${name// /}" in ''|'#'*) continue ;; esac
    CASES=$((CASES + 1))
    C=0; I=0; P=0; R=0; BAD=""
    IFS=';' read -r -a vec <<<"$days"
    for day in "${vec[@]}"; do
      IFS=',' read -r dc di dp dr <<<"$day"
      if [ "$ruler" = events ]; then
        C=$((C + dc)); I=$((I + di)); P=$((P + dp)); R=$((R + dr))
      else
        (( dc + di + dp + dr > 0 )) || continue
        read -r sc si sp sr <<<"$(day_shape "$dc" "$di" "$dp" "$dr")"
        (( sc + si + sp + sr == 1000 )) || BAD=" shape($day) sums to $((sc + si + sp + sr))"
        C=$((C + sc)); I=$((I + si)); P=$((P + sp)); R=$((R + sr))
      fi
    done
    [ "$ruler" = events ] && MODE=grow || MODE=fixed
    read -r got _l _g _a _b _e _f <<<"$(judge "$C" "$I" "$P" "$R" "$MODE")"
    if [ "$got" = "$want" ] && [ -z "$BAD" ]; then
      printf 'RULERCHECK %-48s ruler=%-9s => %-15s OK\n' "$name" "$ruler" "$got"
    else
      printf 'RULERCHECK %-48s ruler=%-9s => %-15s WANT %s FAIL%s\n' "$name" "$ruler" "$got" "$want" "$BAD"
      FAILED=$((FAILED + 1))
    fi
  done <<'CASES'
# the sweep day of 2026-08-11, judged alone, is the same reading either way
the sweep day alone|events|LAGGING:review|47,603,49,0
the sweep day alone|day-shape|LAGGING:review|47,603,49,0
# the floor: exact quarters were the ONLY thing the old target accepted
a day of exact quarters|events|OK|5,5,5,5
one review short of exact quarters|events|OK|5,5,5,4
a vestigial leg is thin|events|LAGGING:review|10,10,10,1
# the unit: one sweep beside six ordinary days, read by each ruler
the sweep beside six ordinary days|events|LAGGING:review|47,603,49,0;5,5,5,5;5,5,5,5;5,5,5,5;5,5,5,5;5,5,5,5;5,5,5,5
the sweep beside six ordinary days|day-shape|OK|47,603,49,0;5,5,5,5;5,5,5,5;5,5,5,5;5,5,5,5;5,5,5,5;5,5,5,5
# the cost: a day holding one artifact still casts a whole day's vote
three one-PR days beside one ordinary day|events|OK|0,0,1,0;0,0,1,0;0,0,1,0;10,10,10,10
three one-PR days beside one ordinary day|day-shape|LAGGING:commit|0,0,1,0;0,0,1,0;0,0,1,0;10,10,10,10
# nothing to judge stays nothing to judge under both
an empty window|events|EMPTY|0,0,0,0;0,0,0,0
an empty window|day-shape|EMPTY|0,0,0,0;0,0,0,0
CASES
  if (( FAILED == 0 )); then
    printf 'RULERCHECK VERDICT PASS floor=%d‰ cases=%d\n' $((1000 / FLOOR_DIV)) "$CASES"
    exit 0
  fi
  printf 'RULERCHECK VERDICT FAIL floor=%d‰ cases=%d failed=%d\n' $((1000 / FLOOR_DIV)) "$CASES" "$FAILED"
  exit 5
fi

# --judgecheck: the ADVICE, judged the way an operator judges it — by doing what
# the line says and reading the day that comes back. --rulercheck asks whether
# the verdict is right about the day it was given; nothing asked whether the
# remedy printed under that verdict survives being taken (#952).
#
# Three things are executed per row. `alone=` is what the DEFICIT line prints:
# each leg solved with the other three standing still. `all-at-once=` is the
# verdict the day reads after ALL of those numbers are acted on — the reading
# that makes them alternatives, because acting on one enlarges the day the rest
# were measured against. `together=` is the PLAN line's set, and the row fails
# unless the day it creates reads OK.
#
# The rows are chosen so the check cannot be satisfied by a slogan. Two of them
# show the alternatives holding — one where nothing knocks anything under, one
# where the rounding slack absorbs the other leg's number — so "alternatives
# never compose" is not what is being asserted here; the fault is that the line
# never said WHICH kind of day it was printing. The row that carries the unit is
# `30,8,25,1`: one leg is thin, its number is 8, and taking that 8 leaves the
# ISSUE leg — the leg the line printed as 0 — thin by 2. That is the zero the
# issue calls the misleading part, executed.
#
# Under the fixed ruler nothing grows: the day-shape total is 1000‰ per working
# day whatever is done, so a leg is lifted by ‰ taken from a leg above the floor.
# There the four numbers ARE a sum, and what has to be checked is that the day
# has the room — the deficits below the floor against the surplus above it.
#
# No token, no network, no repository, so CI runs it and so does a pinned tree.
if (( JUDGECHECK )); then
  FAILED=0 CASES=0
  while IFS='|' read -r name mode vec want_alone want_after want_plan; do
    case "${name// /}" in ''|'#'*) continue ;; esac
    CASES=$((CASES + 1))
    IFS=',' read -r c i p r <<<"${vec// /}"
    read -r _v _l _g a_c a_i a_p a_r <<<"$(judge "$c" "$i" "$p" "$r" "$mode")"
    got_alone="$a_c,$a_i,$a_p,$a_r"
    t=$((c + i + p + r))
    BAD=""
    if [ "$mode" = grow ]; then
      read -r t_c t_i t_p t_r <<<"$(together "$c" "$i" "$p" "$r")"
      read -r after _rest <<<"$(judge $((c + a_c)) $((i + a_i)) $((p + a_p)) $((r + a_r)) grow)"
      read -r plan_v _rest <<<"$(judge $((c + t_c)) $((i + t_i)) $((p + t_p)) $((r + t_r)) grow)"
    else
      # The plan is the four deficits themselves; the question is whether the
      # fixed total holds them. It does when the ‰ above the floor cover the ‰
      # below it, which is what a floor of an eighth is for: four floors are half
      # a day, so half a day is always available to reach them.
      t_c=$a_c; t_i=$a_i; t_p=$a_p; t_r=$a_r
      f=$(( (t + FLOOR_DIV - 1) / FLOOR_DIV ))
      need=$((a_c + a_i + a_p + a_r)); room=0
      for h in "$c" "$i" "$p" "$r"; do
        if (( h > f )); then room=$((room + h - f)); fi
      done
      if (( need <= room )); then after=OK; else after="NOROOM(need=$need room=$room)"; fi
      plan_v="$after"
    fi
    got_plan="$t_c,$t_i,$t_p,$t_r"
    # The assertion the unit exists for: the set the PLAN line prints must leave
    # a day with no thin leg. Everything else on the row is a pin on the numbers.
    [ "$plan_v" = OK ] || [ "$plan_v" = EMPTY ] || BAD="${BAD} the together set leaves ${plan_v};"
    [ "$got_alone" = "${want_alone// /}" ] || BAD="${BAD} alone WANT ${want_alone};"
    [ "$after" = "${want_after// /}" ]     || BAD="${BAD} all-at-once WANT ${want_after};"
    [ "$got_plan" = "${want_plan// /}" ]   || BAD="${BAD} together WANT ${want_plan};"
    if [ -z "$BAD" ]; then
      printf 'JUDGECHECK %-42s %-5s alone=%-12s all-at-once=%-14s together=%-12s => %-6s OK\n' \
        "$name" "$mode" "$got_alone" "$after" "$got_plan" "$plan_v"
    else
      printf 'JUDGECHECK %-42s %-5s alone=%-12s all-at-once=%-14s together=%-12s => %-6s FAIL%s\n' \
        "$name" "$mode" "$got_alone" "$after" "$got_plan" "$plan_v" "$BAD"
      FAILED=$((FAILED + 1))
    fi
  done <<'CASES'
# the reading that opened #952. Under the eighth floor it is thin nowhere, so the
# line it quoted no longer prints at all on that day — the fault it named is not
# a property of that day's numbers, and the rows under it carry it to days that
# still print a DEFICIT.
the reading of #952|grow|13,19,18,16|0,0,0,0|OK|0,0,0,0
# two thin legs: each asks for 2 alone, and both taken leaves both thin again
two thin legs, 2 each alone|grow|10,10,1,1|0,0,2,2|LAGGING:pr|0,0,3,3
# the sweep day of 2026-08-11, the real vector --rulercheck also pins
the sweep day of 2026-08-11|grow|47,603,49,0|47,0,44,100|LAGGING:pr|74,0,72,121
# alternatives that DO compose: the rounding slack covers the other leg's 1
two thin legs whose numbers compose|grow|8,8,24,25|1,1,0,0|OK|1,1,0,0
# the unit: ONE thin leg, and the leg the line printed 0 for goes thin on its fix
one thin leg, and a 0 that is not|grow|30,8,25,1|0,0,0,8|LAGGING:issue|0,2,0,9
# one thin leg with nothing marginal beside it: alone and together agree
one thin leg, nothing knocked under|grow|10,10,10,1|0,0,0,4|OK|0,0,0,4
# an already balanced day asks for nothing under either reading
a day that is thin nowhere|grow|5,5,5,4|0,0,0,0|OK|0,0,0,0
# day-shape windows: seven working days, so the total is fixed at 7000‰
a 7-day window, one leg thin by 146|fixed|729,2500,2200,1571|146,0,0,0|OK|146,0,0,0
a 7-day window, two legs thin|fixed|500,4000,1900,600|375,0,0,275|OK|375,0,0,275
CASES
  if (( FAILED == 0 )); then
    printf 'JUDGECHECK VERDICT PASS cases=%d floor=%d‰\n' "$CASES" $((1000 / FLOOR_DIV))
    exit 0
  fi
  printf 'JUDGECHECK VERDICT FAIL cases=%d floor=%d‰ failed=%d\n' "$CASES" $((1000 / FLOOR_DIV)) "$FAILED"
  exit 5
fi

# The repository this tree came from, unless told otherwise. tools/ rides the
# pin-to-SHA rule, and a `git archive` copy has no remote to ask — so a pinned
# run must pass --for, and is told that rather than left to guess. It sits below
# the date work because --datecheck answers for the calendar, not for a
# repository, and must not be refused by a tree that has no remote.
if [ -z "$REPO" ] || [ "$REPO" = "__next__" ]; then
  REPO="$(git remote get-url origin 2>/dev/null | sed -E 's#.*github\.com[/:]##; s#\.git$##' || true)"
fi
if [[ ! "$REPO" =~ ^[A-Za-z0-9._-]+/[A-Za-z0-9._-]+$ ]]; then
  echo "FATAL cannot tell which repository this is (no usable origin remote)." >&2
  echo "      pass --for OWNER/NAME — a pinned tree has no remote to ask." >&2
  exit 2
fi

# The window ends on DAY and reaches back SPAN-1 days: a ROLLING window, not a
# calendar square. "This week" starting on a Monday would measure the calendar;
# the law is about the shape of the work, and work does not observe Mondays.
FROM="$(day_minus "$DAY" "$((SPAN - 1))")" || exit 2

# WHOSE DAY IS THIS. `contributionsCollection` roots at `viewer` — the account
# the token happens to belong to — and nothing in the output used to name it. A
# token for the owner's *other* account therefore produced a complete, confident
# reading of the wrong subject:
#
#   $ GH_TOKEN=<other account> tools/balance.sh 2026-08-11
#   BALANCE day=2026-08-11 commits=0 issues=0 prs=0 reviews=0 verdict=EMPTY ...
#
# for a day that took 603 issues and 47 commits, exit 0. The nine-integer guard
# below cannot catch that, because zero is an integer; every line was correct
# about a subject nobody asked for. A meter that can be silently wrong about
# WHICH subject it measured is worse than no meter (#902).
#
# So the subject is asserted before it is measured: the login the token resolves
# to must own the repository being judged. Both logins come back from the API in
# its own canonical spelling — even a `--for` argument is handed to the API and
# echoed back resolved, so neither side of the comparison is ever the operator's
# typing — and a plain `=` is therefore the entire test. Nothing folds case here
# because nothing here can be miscased.
#
# It sits BELOW the date arithmetic and the repository resolution, because
# everything a box can answer without a token should be answered without one:
# `balance.sh 2026-13-99` still refuses with "cannot compute the day", not with
# an authentication complaint about a run that was never going to happen.
#
# This is its OWN query, and that is deliberate: it must answer before anything
# is measured, and the two READINGS share a query for a reason that does not
# apply here — they must be taken at the same instant or they disagree over a
# second of clock. Identity does not move between two calls.
#
# It asks `repositoryOwner`, not `repository`, and that is not interchangeable.
# A top-level `repository(owner:,name:)` that resolves to nothing returns a
# NOT_FOUND *error*, and on any GraphQL error `gh api --jq` prints the raw
# response instead of the filtered row — so the parse below would take the error
# text as a login and refuse with "the API would not say who this token is",
# which is a true sentence about the wrong fault. `repositoryOwner` returns a
# plain null for a login nobody has and never errors, so every path here reaches
# the message it earned. The cost is stated rather than hidden: this resolves
# the OWNER half of `OWNER/NAME` and not the name, so `--for <you>/typo` still
# passes the subject check and reads zero for a repository that does not exist.
# That is the object of the measurement rather than its subject, and #902 is
# about the subject.
#
# gh's own stderr is left alone. With `repositoryOwner` the only things that can
# reach it are transport and credential faults — `gh: Bad credentials (HTTP
# 401)` is the sentence that tells an operator which of the two happened, and
# the FATAL below is deliberately the general one.
IDQ="{ viewer { login } repositoryOwner(login: \"${REPO%%/*}\") { login __typename } }"
read -r V_LOGIN R_OWNER R_KIND <<<"$(gh api graphql -f query="$IDQ" --jq \
  '[.data.viewer.login // "-", .data.repositoryOwner.login // "-", .data.repositoryOwner.__typename // "-"] | @tsv' \
  || true)"

# An unnamed subject is not a subject. This fires on an absent token, an expired
# one, and a rate limit — every case where the API declined to say who is asking.
[[ "$V_LOGIN" =~ ^[A-Za-z0-9-]+$ ]] || {
  echo "FATAL the API would not say which account this token belongs to." >&2
  echo "      every count below is that account's; refusing to measure an unnamed subject." >&2
  exit 3
}
# No owner is nothing to check the subject against, and an unchecked subject is
# the whole fault this guard exists for.
[ "$R_OWNER" != "-" ] || {
  echo "FATAL no GitHub account or organisation is named '${REPO%%/*}' (from ${REPO})." >&2
  echo "      the subject is checked against the owner of the repository being judged, and there is" >&2
  echo "      no owner to read; check --for, or this tree's origin remote." >&2
  exit 3
}

MATCH=OK
[ "$V_LOGIN" = "$R_OWNER" ] || MATCH=NO
printf 'SUBJECT login=%s repo=%s owner=%s owner_kind=%s match=%s\n' \
  "$V_LOGIN" "$REPO" "$R_OWNER" "$R_KIND" "$MATCH"
if [ "$MATCH" = NO ]; then
  if [ "$R_KIND" = Organization ]; then
    echo "FATAL ${REPO} belongs to the ${R_OWNER} organisation, and contributionsCollection roots at" >&2
    echo "      a single user — so no token can satisfy this check and none can be asserted against it." >&2
    echo "      Refusing to print a verdict whose subject was never established." >&2
  else
    echo "FATAL this token reads ${V_LOGIN}'s day; ${REPO} belongs to ${R_OWNER}." >&2
    echo "      the counts would be a real measurement of the wrong person — including the confident" >&2
    echo "      verdict=EMPTY a wrong-account run used to print for a day full of work." >&2
    echo "      Switch accounts (gh auth switch --user ${R_OWNER}) or --for a repository ${V_LOGIN} owns." >&2
  fi
  exit 6
fi

# One query, EVERY reading. Asking twice would invite the halves to be measured
# a second apart and disagree for a reason that is not the point.
#
# That rule was written for the two halves of the span reading and then broken
# by the third reading added beside them: the per-day rows went out as their own
# call a moment later, so a contribution landing between the two calls made the
# days read high by exactly that work and the sum check refused both readings
# (#982). The refusal landed on the honest run — the tool being used DURING
# work, which is the run D-060 exists to serve. So the day aliases moved here,
# into the same request as the span totals, and the sum check below now compares
# two views of one instant rather than two instants.
#
# The days are aliases on the same `viewer` the span totals hang off, so this is
# a merge of two query strings and not a new mechanism. The span gets an alias
# of its own to keep it apart from the day rows when the response is read.
DAYSEL=""
if (( SPAN > 1 )); then
  for ((k = SPAN - 1; k >= 0; k--)); do
    d="$(day_minus "$DAY" "$k")" || exit 2
    DAYSEL+=" d${k}: contributionsCollection(from:\"${d}T00:00:00Z\",to:\"${d}T23:59:59Z\")"
    DAYSEL+="{totalCommitContributions totalIssueContributions totalPullRequestContributions totalPullRequestReviewContributions}"
  done
fi
Q="{
  viewer {${DAYSEL}
    span: contributionsCollection(from: \"${FROM}T00:00:00Z\", to: \"${DAY}T23:59:59Z\") {
    totalCommitContributions
    totalIssueContributions
    totalPullRequestContributions
    totalPullRequestReviewContributions
    commitContributionsByRepository(maxRepositories: 100) { repository { nameWithOwner } contributions { totalCount } }
    issueContributionsByRepository(maxRepositories: 100) { repository { nameWithOwner } contributions { totalCount } }
    pullRequestContributionsByRepository(maxRepositories: 100) { repository { nameWithOwner } contributions { totalCount } }
    pullRequestReviewContributionsByRepository(maxRepositories: 100) { repository { nameWithOwner } contributions { totalCount } }
  } }
}"

# One response, read twice — the span row first, then a row per day. Both come
# out of the same filter because they come out of the same measurement, and the
# span row leads so the nine-integer guard below still reads it with a plain
# `read` off the first line.
#
# `// 0` is load-bearing: a repository absent from a breakdown contributed
# nothing that day, which is a zero and not a missing value.
JQ='.data.viewer as $v
| $v.span as $c
| def mine($l): ($l | map(select(.repository.nameWithOwner == "REPOSLOT")) | (.[0].contributions.totalCount // 0));
  ([ $c.totalCommitContributions, $c.totalIssueContributions,
    $c.totalPullRequestContributions, $c.totalPullRequestReviewContributions,
    mine($c.commitContributionsByRepository), mine($c.issueContributionsByRepository),
    mine($c.pullRequestContributionsByRepository), mine($c.pullRequestReviewContributionsByRepository),
    ([$c.commitContributionsByRepository, $c.issueContributionsByRepository,
      $c.pullRequestContributionsByRepository, $c.pullRequestReviewContributionsByRepository]
     | map(length) | max)
  ] | @tsv),
  ($v | to_entries | map(select(.key | test("^d[0-9]+$"))) | .[]
   | "\(.key) \(.value.totalCommitContributions) \(.value.totalIssueContributions) \(.value.totalPullRequestContributions) \(.value.totalPullRequestReviewContributions)")'

MEAS="$(gh api graphql -f query="$Q" --jq "${JQ//REPOSLOT/$REPO}" || true)"
read -r A_C A_I A_P A_R R_C R_I R_P R_R MAXLIST <<<"$MEAS"

# A read that failed must not become a verdict. `set -e` does not catch a
# command substitution inside a here-string, so a bad date, an expired token or
# a rate limit used to sail past here and print a shape computed from the error
# text — a meter reporting confidently on a measurement it never took. Nine
# integers or nothing.
for v in "$A_C" "$A_I" "$A_P" "$A_R" "$R_C" "$R_I" "$R_P" "$R_R" "$MAXLIST"; do
  [[ "$v" =~ ^[0-9]+$ ]] || {
    echo "FATAL the contributions API did not return numbers for ${DAY}." >&2
    echo "      a real date, a live token and a reachable API are all required;" >&2
    echo "      refusing to print a verdict from a read that did not happen." >&2
    exit 3
  }
done

if [ "$SCOPE" = repo ]; then
  COMMITS=$R_C; ISSUES=$R_I; PRS=$R_P; REVIEWS=$R_R
else
  COMMITS=$A_C; ISSUES=$A_I; PRS=$A_P; REVIEWS=$A_R
fi

A_TOTAL=$((A_C + A_I + A_P + A_R))
R_TOTAL=$((R_C + R_I + R_P + R_R))
TOTAL=$((COMMITS + ISSUES + PRS + REVIEWS))

# How the window is named on every line: a single day still says day=, so a
# reading taken before --week existed is byte-identical to the same reading now.
if (( SPAN == 1 )); then
  WHEN="day=$DAY"
  WHEN_WORD="today"
else
  WHEN="window=${FROM}..${DAY} days=${SPAN}"
  WHEN_WORD="in this window"
fi

# WHICH BUTTON SHAPED THIS READING (D-061, #911). The merge strategy is an input
# to the commit leg and it is the only input that lives outside the repository,
# in a settings page nobody reading a verdict can see. Under merge commits a unit
# of `k` atomic commits lands `k+1`, so the commit leg sits at roughly twice the
# PR leg by construction; under rebase it lands `k`, and `k = 1` is the 1:1 the
# law wants. So the settings are read here and printed there.
#
# Read once, from the repository being judged rather than from the tree, because
# `--for` can name a repository this checkout is not: the button that shaped the
# counts is the button of the repository the counts came from.
#
# A failed read is reported as `unreadable`, not as a default and not as a fatal.
# The nine-integer guard above already refused everything that makes the counts
# untrustworthy; this call is a fourth thing said about a reading that is already
# sound, and losing it should cost the reader the sentence, not the verdict.
read -r M_MERGE M_SQUASH M_REBASE <<<"$(gh api "repos/$REPO" \
  --jq '[.allow_merge_commit, .allow_squash_merge, .allow_rebase_merge] | @tsv' 2>/dev/null || true)"
if [[ "$M_MERGE" =~ ^(true|false)$ && "$M_SQUASH" =~ ^(true|false)$ && "$M_REBASE" =~ ^(true|false)$ ]]; then
  MERGE_ALLOWED=""
  if [ "$M_MERGE"  = true ]; then MERGE_ALLOWED="${MERGE_ALLOWED}+merge";  fi
  if [ "$M_SQUASH" = true ]; then MERGE_ALLOWED="${MERGE_ALLOWED}+squash"; fi
  if [ "$M_REBASE" = true ]; then MERGE_ALLOWED="${MERGE_ALLOWED}+rebase"; fi
  MERGE_ALLOWED="${MERGE_ALLOWED#+}"
  MERGE_ALLOWED="${MERGE_ALLOWED:-none}"
else
  MERGE_ALLOWED=unreadable
fi

# The scope report rides every run, including the empty one: "nothing happened
# here" and "nothing happened anywhere" are different days.
scope_line() {
  local delta=$((A_TOTAL - R_TOTAL)) note
  if [ "$A_TOTAL" -eq 0 ]; then
    note="the account made no contributions anywhere ${WHEN_WORD}, so neither reading has anything to judge"
  elif [ "$delta" -eq 0 ]; then
    note="the account touched nothing outside this repository ${WHEN_WORD}, so the two readings are the same number"
  elif [ "$SCOPE" = account ]; then
    note="WARN this verdict counts ${delta} contribution(s) made outside ${REPO}; rerun with --repo for this repository's own shape"
  else
    note="the account also made ${delta} contribution(s) outside this repository ${WHEN_WORD}, which this verdict excludes"
  fi
  # `merge=` goes on the end rather than beside `repo=`, so every field a reading
  # taken before D-061 carried is still in the same place: the old line is a
  # prefix of the new one, and nothing that quoted it has to be re-read.
  printf 'SCOPE %s repo=%s account_total=%d repo_total=%d delta=%d judged=%s merge=%s  (%s)\n' \
    "$WHEN" "$REPO" "$A_TOTAL" "$R_TOTAL" "$delta" "$SCOPE" "$MERGE_ALLOWED" "$note"
  # 100 is the page this query asks for. A full page means the breakdown may be
  # truncated and the repo reading could be a floor rather than a count.
  if [ "$MAXLIST" -ge 100 ]; then
    printf 'SCOPE WARN a per-repository breakdown came back full (%d of max 100): the repo reading may be truncated\n' "$MAXLIST"
  fi
  # Naming the open buttons is not the same as saying what they cost. D-061
  # refuses two of the three and for different reasons, so the warning carries
  # the reason rather than a rule number — a reader who has to open an ADR to
  # find out why a line is yellow is a reader who will not.
  if [ "$MERGE_ALLOWED" = unreadable ]; then
    printf 'SCOPE WARN the merge settings of %s could not be read, so this verdict does not say which button shaped it\n' "$REPO"
  else
    local why=""
    if [ "$M_MERGE" = true ]; then
      why="merge commits author a second commit per unit, which holds the commit leg near twice the PR leg"
    fi
    if [ "$M_SQUASH" = true ]; then
      if [ -n "$why" ]; then why="${why}; "; fi
      why="${why}squash merges land one commit per unit but fuse a k>1 unit's atomic commits into one, so D-039's artifact survives only while k=1"
    fi
    if [ -n "$why" ]; then
      printf 'SCOPE WARN D-061 makes rebase the term of this law and %s still offers %s: %s\n' \
        "$REPO" "$MERGE_ALLOWED" "$why"
    fi
  fi
}

# WHICH RULER THIS RUN USES. The default is day-shape; two things take it away,
# and both of them are a missing measurement rather than a preference.
#
#   --events   asked for, so the old ruler is given: this is how any reading
#              taken before 2026-08-13 is reproduced exactly.
#   scope=repo over a window: the per-day query below is account-wide and has no
#              per-repository breakdown, so there is no repo-scoped day shape to
#              take. Falling back to events and saying so beats computing a
#              repository's verdict out of the account's days.
#
# A single day is its own shape, so SPAN=1 is the same arithmetic under either
# name and the label is the only thing that changes.
RULER=day-shape
RULER_WHY="asked for: the ‰ above are the counts divided by the total, which is the ruler one scripted day can dominate (#828)"
(( EVENTS )) && RULER=events
if [ "$RULER" = day-shape ] && (( SPAN > 1 )) && [ "$SCOPE" = repo ]; then
  RULER=events
  RULER_WHY="the per-day query carries no per-repository breakdown, so no day shape can be taken at scope=repo (#966)"
fi

if (( TOTAL == 0 )); then
  printf 'BALANCE %s commits=0 issues=0 prs=0 reviews=0 verdict=EMPTY scope=%s ruler=%s\n' "$WHEN" "$SCOPE" "$RULER"
  scope_line
  exit 0
fi

# The window's days. This read used to sit below the verdict as a cross-check on
# it; under the day-shape ruler it IS the measurement, so the window line is
# computed from it. The cross-check survives unchanged at the bottom — the days
# must still re-add to the window the API reports for the whole span — and it is
# now a check on one measurement rather than on two, because these rows arrived
# in the same response as the span totals.
#
# Everything after the span row is a day row, so the span row is dropped by
# taking what follows the first newline. A response too short to hold one never
# reaches here: the nine-integer guard above already refused it.
if (( SPAN > 1 )); then
  DAYROWS="${MEAS#*$'\n'}"
  # Every row carries its own alias, and the alias IS the day offset. GraphQL
  # returns aliased fields in SORTED key order, not query order (d0 d1 d10 d11
  # d2 ...), so reading them positionally silently pairs each day's counts with
  # another day's date — and the sum still checks out, because the set of days
  # is right and only the labels are wrong. Nothing downstream can catch that,
  # so nothing downstream is asked to.
  declare -a D_C D_I D_P D_R
  ROWS=0
  while read -r key c i p r; do
    [[ "$key" =~ ^d([0-9]+)$ ]] || continue
    off="${BASH_REMATCH[1]}"   # read it NOW: the next =~ clobbers BASH_REMATCH
    [[ "$c" =~ ^[0-9]+$ && "$i" =~ ^[0-9]+$ && "$p" =~ ^[0-9]+$ && "$r" =~ ^[0-9]+$ ]] || continue
    (( off >= SPAN )) && continue
    D_C[$off]=$c; D_I[$off]=$i; D_P[$off]=$p; D_R[$off]=$r
    ROWS=$((ROWS + 1))
  done <<<"$DAYROWS"

  # A ruler built out of days cannot be built out of some of them. The window
  # line printed from a short read would be a real number about a span nobody
  # asked for, so the ruler steps back to events and the WINDOW line below still
  # reports the short read on its own terms.
  if [ "$RULER" = day-shape ] && (( ROWS != SPAN )); then
    RULER=events
    RULER_WHY="the per-day read came back ${ROWS} of ${SPAN} days, and a day shape cannot be taken from days that did not arrive"
  fi

  # Each working day contributes exactly 1000 per mille of itself. Empty days
  # are skipped rather than counted as four zeros: a day with no work has no
  # shape, and giving it one would drag every leg toward nothing and call the
  # result balance.
  SH_C=0; SH_I=0; SH_P=0; SH_R=0; WORKDAYS=0
  for ((k = SPAN - 1; k >= 0; k--)); do
    [ -n "${D_C[$k]:-}" ] || continue
    (( D_C[k] + D_I[k] + D_P[k] + D_R[k] > 0 )) || continue
    read -r sc si sp sr <<<"$(day_shape "${D_C[$k]}" "${D_I[$k]}" "${D_P[$k]}" "${D_R[$k]}")"
    SH_C=$((SH_C + sc)); SH_I=$((SH_I + si)); SH_P=$((SH_P + sp)); SH_R=$((SH_R + sr))
    WORKDAYS=$((WORKDAYS + 1))
  done
fi

# What gets judged, and in what unit. The BALANCE line always prints the real
# event COUNTS — they are facts and no ruler changes them — while the per mille
# beside them, the verdict and the deficit all come from the ruler in force. The
# RULER line under it says which, so no figure on the page is ambiguous about
# what produced it.
if [ "$RULER" = day-shape ] && (( SPAN > 1 )); then
  J_C=$SH_C; J_I=$SH_I; J_P=$SH_P; J_R=$SH_R; MODE=fixed
else
  J_C=$COMMITS; J_I=$ISSUES; J_P=$PRS; J_R=$REVIEWS; MODE=grow
fi
J_TOTAL=$((J_C + J_I + J_P + J_R))

read -r VERDICT LAG GAP N_C N_I N_P N_R <<<"$(judge "$J_C" "$J_I" "$J_P" "$J_R" "$MODE")"

printf 'BALANCE %s commits=%d(%s‰) issues=%d(%s‰) prs=%d(%s‰) reviews=%d(%s‰) total=%d verdict=%s scope=%s ruler=%s\n' \
  "$WHEN" "$COMMITS" "$(pct "$J_C" "$J_TOTAL")" "$ISSUES" "$(pct "$J_I" "$J_TOTAL")" \
  "$PRS" "$(pct "$J_P" "$J_TOTAL")" "$REVIEWS" "$(pct "$J_R" "$J_TOTAL")" \
  "$TOTAL" "$VERDICT" "$SCOPE" "$RULER"
if [ "$RULER" = day-shape ] && (( SPAN > 1 )); then
  printf 'RULER day-shape floor=%d‰ days_with_work=%d of %d  (each working day contributes its own shape, weighted equally; the ‰ above are those shapes re-added, not the counts divided)\n' \
    $((1000 / FLOOR_DIV)) "$WORKDAYS" "$SPAN"
elif [ "$RULER" = day-shape ]; then
  printf 'RULER day-shape floor=%d‰  (one day is its own shape, so this reading is the same arithmetic under --events)\n' \
    $((1000 / FLOOR_DIV))
else
  printf 'RULER events floor=%d‰  (%s)\n' $((1000 / FLOOR_DIV)) "$RULER_WHY"
fi
scope_line

# The window's days, one line each, each judged alone. This is the whole reason
# --week exists: a day that IS a review pass or IS a decomposition sweep will
# read LAGGING on three legs and be wrong about each, and the only way to show
# a window fixing that is to print what the days said on their own.
if (( SPAN > 1 )); then
  S_C=0; S_I=0; S_P=0; S_R=0
  for ((k = SPAN - 1; k >= 0; k--)); do
    [ -n "${D_C[$k]:-}" ] || continue
    c="${D_C[$k]}"; i="${D_I[$k]}"; p="${D_P[$k]}"; r="${D_R[$k]}"
    d="$(day_minus "$DAY" "$k")" || exit 2
    read -r dv _dl _dg _a _b _e _f <<<"$(judge "$c" "$i" "$p" "$r" grow)"
    printf 'BALANCE_DAY day=%s commits=%d issues=%d prs=%d reviews=%d total=%d verdict=%s\n' \
      "$d" "$c" "$i" "$p" "$r" "$((c + i + p + r))" "$dv"
    S_C=$((S_C + c)); S_I=$((S_I + i)); S_P=$((S_P + p)); S_R=$((S_R + r))
  done

  # The window must BE its days re-added, not a second opinion about them. If
  # these disagree, one of the two readings is wrong and neither should be
  # quoted — so say so instead of picking a favourite.
  #
  # Both sides of this comparison came out of one response, so a contribution
  # landing while the tool runs can no longer split them: it is either in both
  # readings or in neither. What survives is what the branch says it catches — a
  # real disagreement between the API's own two answers about one instant.
  S_TOTAL=$((S_C + S_I + S_P + S_R))
  if (( ROWS != SPAN )); then
    printf 'WINDOW days_returned=%d expected=%d sum_check=INCOMPLETE  (the per-day read did not come back whole; the window line above stands, the days do not)\n' \
      "$ROWS" "$SPAN"
  elif (( S_C == A_C && S_I == A_I && S_P == A_P && S_R == A_R )); then
    printf 'WINDOW days=%d days_sum=%d account_total=%d sum_check=OK  (the window is its days re-added, not a second measurement)\n' \
      "$SPAN" "$S_TOTAL" "$A_TOTAL"
  else
    printf 'WINDOW days=%d days_sum=%d account_total=%d sum_check=MISMATCH  (commits %d/%d issues %d/%d prs %d/%d reviews %d/%d — refuse both readings)\n' \
      "$SPAN" "$S_TOTAL" "$A_TOTAL" "$S_C" "$A_C" "$S_I" "$A_I" "$S_P" "$A_P" "$S_R" "$A_R"
    exit 4
  fi
fi

# The deficit carries its unit, because under the two rulers it is two different
# quantities. Under events it is artifacts: how many of that kind would lift the
# leg over the floor, solved against the larger day they create. Under day-shape
# nothing grows — the total is fixed at 1000 per working day — so the shortfall
# is a distance, and the readable form of that distance is days: 1000‰-days is
# one whole day given to nothing but that leg.
#
# It also carries what KIND of list it is, which is the whole of #952. Under the
# fixed ruler the four are a sum: the total does not move, so a leg is lifted by
# ‰ taken from a leg above the floor and no number here enlarges another's
# denominator. Under the grow ruler they are alternatives — four answers to four
# different days — so the set that can actually be acted on goes on its own line
# rather than being left for the reader to derive.
if (( GAP > 0 )); then
  if [ "$MODE" = fixed ]; then
    printf 'DEFICIT unit=permille-days commits=%d issues=%d prs=%d reviews=%d  (a sum, not alternatives: the total is fixed at 1000‰ per working day, so these are distances inside it — the %s leg is furthest below the floor: %d‰-days, %d.%03d of a day given entirely to it)\n' \
      "$N_C" "$N_I" "$N_P" "$N_R" "$LAG" "$GAP" $((GAP / 1000)) $((GAP % 1000))
  else
    printf 'DEFICIT unit=artifacts commits=%d issues=%d prs=%d reviews=%d  (alternatives, not a sum: each number clears its own leg with the other three standing still, and every artifact added enlarges the day the others are measured against — the %s leg is furthest below the floor at %d)\n' \
      "$N_C" "$N_I" "$N_P" "$N_R" "$LAG" "$GAP"
    read -r T_C T_I T_P T_R <<<"$(together "$J_C" "$J_I" "$J_P" "$J_R")"
    if [[ "$T_C" =~ ^[0-9]+$ && "$T_I" =~ ^[0-9]+$ && "$T_P" =~ ^[0-9]+$ && "$T_R" =~ ^[0-9]+$ ]]; then
      printf 'PLAN unit=artifacts commits=%d issues=%d prs=%d reviews=%d  (this one IS a sum: all four solved against the day they create together, which is the smallest set that reaches verdict=OK — a leg printed 0 above can appear here, because work on a lagging leg can push a leg that was above the floor under it)\n' \
        "$T_C" "$T_I" "$T_P" "$T_R"
    else
      printf 'PLAN WARN the set that composes did not settle in 64 rounds, so it is not printed: the DEFICIT numbers above are alternatives and this run has no plan to offer\n'
    fi
  fi
fi
