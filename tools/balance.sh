#!/usr/bin/env bash
# tools/balance.sh — is the day's work balanced? (D-060)
#
# Usage: tools/balance.sh [--account|--repo] [--for OWNER/NAME]
#                         [--week|--month|--days N] [YYYY-MM-DD]
#        tools/balance.sh --datecheck          (the day arithmetic, no token needed)
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
# The default stays `account` deliberately. D-060 was accepted on account-wide
# numbers, and silently redefining a law's inputs is worse than a wide scope
# honestly labelled — flipping it is a decision for the record, not for a tool.
# The SCOPE line is what removes the silence: when the two readings differ it
# says by how much, so a green verdict can never quietly mean somewhere else.
#
# WHICH DAYS IT JUDGES. Some days belong to one leg by nature — a skeptic pass
# IS a review day, a decomposition sweep IS an issue day — so judging one square
# of the calendar calls three legs LAGGING and is wrong about each. --week (7),
# --month (30) and --days N (1-90) judge the same four quarters over a ROLLING
# window ending on the named day. Each window prints its days one BALANCE_DAY
# line each, judged alone, because the inversion a window claims to fix is only
# visible beside what the days said by themselves; a WINDOW line then re-adds
# those days and checks the sum against the window's own totals, and refuses
# both readings if they disagree.
#
# Measured caveat, so nobody reads a window as a cure: the meter counts
# contribution EVENTS, so one scripted day can dominate any span (2026-08-11's
# 603 issues are 63% of that whole week). Widening the window does not fix a
# volume asymmetry — see #828, which is about the ruler rather than the span.

set -euo pipefail

SCOPE=account
REPO=""
DAY=""
SPAN=1
SPAN_NAME=day
DATECHECK=0
for arg in "$@"; do
  case "$arg" in
    --account)   SCOPE=account ;;
    --datecheck) DATECHECK=1 ;;
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

# One query, both readings. Asking twice would invite the two halves to be
# measured a second apart and disagree for a reason that is not the point.
Q="{
  viewer { contributionsCollection(from: \"${FROM}T00:00:00Z\", to: \"${DAY}T23:59:59Z\") {
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

# `// 0` is load-bearing: a repository absent from a breakdown contributed
# nothing that day, which is a zero and not a missing value.
JQ='.data.viewer.contributionsCollection as $c
| def mine($l): ($l | map(select(.repository.nameWithOwner == "REPOSLOT")) | (.[0].contributions.totalCount // 0));
  [ $c.totalCommitContributions, $c.totalIssueContributions,
    $c.totalPullRequestContributions, $c.totalPullRequestReviewContributions,
    mine($c.commitContributionsByRepository), mine($c.issueContributionsByRepository),
    mine($c.pullRequestContributionsByRepository), mine($c.pullRequestReviewContributionsByRepository),
    ([$c.commitContributionsByRepository, $c.issueContributionsByRepository,
      $c.pullRequestContributionsByRepository, $c.pullRequestReviewContributionsByRepository]
     | map(length) | max)
  ] | @tsv'

read -r A_C A_I A_P A_R R_C R_I R_P R_R MAXLIST \
  <<<"$(gh api graphql -f query="$Q" --jq "${JQ//REPOSLOT/$REPO}" || true)"

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
  printf 'SCOPE %s repo=%s account_total=%d repo_total=%d delta=%d judged=%s  (%s)\n' \
    "$WHEN" "$REPO" "$A_TOTAL" "$R_TOTAL" "$delta" "$SCOPE" "$note"
  # 100 is the page this query asks for. A full page means the breakdown may be
  # truncated and the repo reading could be a floor rather than a count.
  if [ "$MAXLIST" -ge 100 ]; then
    printf 'SCOPE WARN a per-repository breakdown came back full (%d of max 100): the repo reading may be truncated\n' "$MAXLIST"
  fi
}

# Each leg's target is a quarter. Adding to one leg grows the total, so the
# honest deficit solves for the bigger day rather than the day we already had:
# the smallest n with (have+n)*4 >= total+n, which is ceil((total - 4*have)/3).
# Closed form rather than the original count-up loop, because a month's window
# can want thousands of iterations to answer a question arithmetic settles.
need_in() {                     # need_in <count> <total>
  local n=$(( ($2 - 4 * $1 + 2) / 3 ))
  (( n < 0 )) && n=0
  printf '%d' "$n"
}

# Judge four counts. Echoes: verdict lag gap n_c n_i n_p n_r
judge() {
  local c="$1" i="$2" p="$3" r="$4"
  local t=$((c + i + p + r))
  if (( t == 0 )); then echo "EMPTY - 0 0 0 0 0"; return; fi
  local nc ni np nr lag="" gap=0 v="OK"
  nc="$(need_in "$c" "$t")"; ni="$(need_in "$i" "$t")"
  np="$(need_in "$p" "$t")"; nr="$(need_in "$r" "$t")"
  local leg n
  for leg in "commit:$nc" "issue:$ni" "pr:$np" "review:$nr"; do
    n="${leg##*:}"
    if (( n > gap )); then gap="$n"; lag="${leg%%:*}"; fi
  done
  (( gap > 0 )) && v="LAGGING:$lag"
  echo "$v ${lag:--} $gap $nc $ni $np $nr"
}

if (( TOTAL == 0 )); then
  printf 'BALANCE %s commits=0 issues=0 prs=0 reviews=0 verdict=EMPTY scope=%s\n' "$WHEN" "$SCOPE"
  scope_line
  exit 0
fi

pct() { printf '%d' $(( ($1 * 1000 + TOTAL / 2) / TOTAL )); }   # per mille, rounded

read -r VERDICT LAG GAP N_C N_I N_P N_R <<<"$(judge "$COMMITS" "$ISSUES" "$PRS" "$REVIEWS")"

printf 'BALANCE %s commits=%d(%s‰) issues=%d(%s‰) prs=%d(%s‰) reviews=%d(%s‰) total=%d verdict=%s scope=%s\n' \
  "$WHEN" "$COMMITS" "$(pct "$COMMITS")" "$ISSUES" "$(pct "$ISSUES")" \
  "$PRS" "$(pct "$PRS")" "$REVIEWS" "$(pct "$REVIEWS")" "$TOTAL" "$VERDICT" "$SCOPE"
scope_line

# The window's days, one line each, each judged alone. This is the whole reason
# --week exists: a day that IS a review pass or IS a decomposition sweep will
# read LAGGING on three legs and be wrong about each, and the only way to show
# a window fixing that is to print what the days said on their own.
if (( SPAN > 1 )); then
  DQ="{ viewer {"
  for ((k = SPAN - 1; k >= 0; k--)); do
    d="$(day_minus "$DAY" "$k")" || exit 2
    DQ+=" d${k}: contributionsCollection(from:\"${d}T00:00:00Z\",to:\"${d}T23:59:59Z\")"
    DQ+="{totalCommitContributions totalIssueContributions totalPullRequestContributions totalPullRequestReviewContributions}"
  done
  DQ+=" } }"
  # Every row carries its own alias, and the alias IS the day offset. GraphQL
  # returns aliased fields in SORTED key order, not query order (d0 d1 d10 d11
  # d2 ...), so reading them positionally silently pairs each day's counts with
  # another day's date — and the sum still checks out, because the set of days
  # is right and only the labels are wrong. Nothing downstream can catch that,
  # so nothing downstream is asked to.
  DAYROWS="$(gh api graphql -f query="$DQ" --jq '.data.viewer | to_entries | map("\(.key) \(.value.totalCommitContributions) \(.value.totalIssueContributions) \(.value.totalPullRequestContributions) \(.value.totalPullRequestReviewContributions)") | join("\n")' || true)"

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

  S_C=0; S_I=0; S_P=0; S_R=0
  for ((k = SPAN - 1; k >= 0; k--)); do
    [ -n "${D_C[$k]:-}" ] || continue
    c="${D_C[$k]}"; i="${D_I[$k]}"; p="${D_P[$k]}"; r="${D_R[$k]}"
    d="$(day_minus "$DAY" "$k")" || exit 2
    read -r dv _dl _dg _a _b _e _f <<<"$(judge "$c" "$i" "$p" "$r")"
    printf 'BALANCE_DAY day=%s commits=%d issues=%d prs=%d reviews=%d total=%d verdict=%s\n' \
      "$d" "$c" "$i" "$p" "$r" "$((c + i + p + r))" "$dv"
    S_C=$((S_C + c)); S_I=$((S_I + i)); S_P=$((S_P + p)); S_R=$((S_R + r))
  done

  # The window must BE its days re-added, not a second opinion about them. If
  # these disagree, one of the two readings is wrong and neither should be
  # quoted — so say so instead of picking a favourite.
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

if (( GAP > 0 )); then
  printf 'DEFICIT commits=%d issues=%d prs=%d reviews=%d  (the %s leg is furthest behind: %d to clear it)\n' \
    "$N_C" "$N_I" "$N_P" "$N_R" "$LAG" "$GAP"
fi
