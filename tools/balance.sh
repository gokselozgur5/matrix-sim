#!/usr/bin/env bash
# tools/balance.sh — is the day's work balanced? (D-060)
#
# Usage: tools/balance.sh [--account|--repo] [--for OWNER/NAME] [YYYY-MM-DD]
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

set -euo pipefail

SCOPE=account
REPO=""
DAY=""
for arg in "$@"; do
  case "$arg" in
    --account) SCOPE=account ;;
    --repo)    SCOPE=repo ;;
    --for)     REPO="__next__" ;;
    -*)        echo "FATAL unknown flag: $arg" >&2; exit 2 ;;
    *)
      if [ "$REPO" = "__next__" ]; then REPO="$arg"; else DAY="$arg"; fi ;;
  esac
done
# `--for` with nothing after it must not quietly become "use origin" — a tool
# arguing against silent scope should not have a silent scope of its own.
[ "$REPO" = "__next__" ] && { echo "FATAL --for wants OWNER/NAME after it" >&2; exit 2; }
DAY="${DAY:-$(date +%F)}"
[[ "$DAY" =~ ^[0-9]{4}-[0-9]{2}-[0-9]{2}$ ]] || { echo "FATAL date must be YYYY-MM-DD" >&2; exit 2; }

# The repository this tree came from, unless told otherwise. tools/ rides the
# pin-to-SHA rule, and a `git archive` copy has no remote to ask — so a pinned
# run must pass --for, and is told that rather than left to guess.
if [ -z "$REPO" ] || [ "$REPO" = "__next__" ]; then
  REPO="$(git remote get-url origin 2>/dev/null | sed -E 's#.*github\.com[/:]##; s#\.git$##' || true)"
fi
if [[ ! "$REPO" =~ ^[A-Za-z0-9._-]+/[A-Za-z0-9._-]+$ ]]; then
  echo "FATAL cannot tell which repository this is (no usable origin remote)." >&2
  echo "      pass --for OWNER/NAME — a pinned tree has no remote to ask." >&2
  exit 2
fi

# One query, both readings. Asking twice would invite the two halves to be
# measured a second apart and disagree for a reason that is not the point.
Q="{
  viewer { contributionsCollection(from: \"${DAY}T00:00:00Z\", to: \"${DAY}T23:59:59Z\") {
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

# The scope report rides every run, including the empty one: "nothing happened
# here" and "nothing happened anywhere" are different days.
scope_line() {
  local delta=$((A_TOTAL - R_TOTAL)) note
  if [ "$A_TOTAL" -eq 0 ]; then
    note="the account made no contributions anywhere today, so neither reading has anything to judge"
  elif [ "$delta" -eq 0 ]; then
    note="the account touched nothing outside this repository today, so the two readings are the same number"
  elif [ "$SCOPE" = account ]; then
    note="WARN this verdict counts ${delta} contribution(s) made outside ${REPO}; rerun with --repo for this repository's own shape"
  else
    note="the account also made ${delta} contribution(s) outside this repository today, which this verdict excludes"
  fi
  printf 'SCOPE day=%s repo=%s account_total=%d repo_total=%d delta=%d judged=%s  (%s)\n' \
    "$DAY" "$REPO" "$A_TOTAL" "$R_TOTAL" "$delta" "$SCOPE" "$note"
  # 100 is the page this query asks for. A full page means the breakdown may be
  # truncated and the repo reading could be a floor rather than a count.
  if [ "$MAXLIST" -ge 100 ]; then
    printf 'SCOPE WARN a per-repository breakdown came back full (%d of max 100): the repo reading may be truncated\n' "$MAXLIST"
  fi
}

if (( TOTAL == 0 )); then
  printf 'BALANCE day=%s commits=0 issues=0 prs=0 reviews=0 verdict=EMPTY scope=%s\n' "$DAY" "$SCOPE"
  scope_line
  exit 0
fi

pct() { printf '%d' $(( ($1 * 1000 + TOTAL / 2) / TOTAL )); }   # per mille, rounded

# Each leg's target is a quarter. Adding to one leg grows the total, so the
# honest deficit solves for the bigger day rather than the day we already had.
need() {                        # need <count>
  local have="$1" n=0
  while (( (have + n) * 4 < TOTAL + n )); do n=$((n + 1)); done
  printf '%d' "$n"
}
N_C="$(need "$COMMITS")"; N_I="$(need "$ISSUES")"; N_P="$(need "$PRS")"; N_R="$(need "$REVIEWS")"

VERDICT="OK"; LAG=""; GAP=0
for leg in "commit:$N_C" "issue:$N_I" "pr:$N_P" "review:$N_R"; do
  n="${leg##*:}"
  if (( n > GAP )); then GAP="$n"; LAG="${leg%%:*}"; fi
done
(( GAP > 0 )) && VERDICT="LAGGING:$LAG"

printf 'BALANCE day=%s commits=%d(%s‰) issues=%d(%s‰) prs=%d(%s‰) reviews=%d(%s‰) total=%d verdict=%s scope=%s\n' \
  "$DAY" "$COMMITS" "$(pct "$COMMITS")" "$ISSUES" "$(pct "$ISSUES")" \
  "$PRS" "$(pct "$PRS")" "$REVIEWS" "$(pct "$REVIEWS")" "$TOTAL" "$VERDICT" "$SCOPE"
scope_line
if (( GAP > 0 )); then
  printf 'DEFICIT commits=%d issues=%d prs=%d reviews=%d  (the %s leg is furthest behind: %d to clear it)\n' \
    "$N_C" "$N_I" "$N_P" "$N_R" "$LAG" "$GAP"
fi
