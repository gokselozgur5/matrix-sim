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
# both readings if they disagree.
#
# Measured caveat, so nobody reads a window as a cure: the meter counts
# contribution EVENTS, so one scripted day can dominate any span (2026-08-11's
# 603 issues are 63% of that whole week). Widening the window does not fix a
# volume asymmetry — see #828, which is about the ruler rather than the span.
#
# WHICH BUTTON SHAPED IT. The merge strategy is a term of this law, not a
# preference about history (D-061). A merge commit is a second authored commit
# per unit, so under it the commit leg runs at about twice the PR leg and the
# four quarters stop being a target that is missed and become one that cannot be
# hit. Every run therefore reads the repository's own merge settings and prints
# them on the SCOPE line: a verdict can be read wrong for a hundred reasons, but
# never again without the button that shaped it being on the same screen.

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
