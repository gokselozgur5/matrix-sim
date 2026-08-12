#!/usr/bin/env bash
# tools/balance.sh — is the day's work balanced? (D-060)
#
# Usage: tools/balance.sh [YYYY-MM-DD]   (default: today)
#
# The law: the four contribution kinds GitHub counts — commits, issues, pull
# requests, reviews — each hold a quarter of the day. The reasoning, not the
# arithmetic, is the point: a day of only PRs is a day nobody planned, a day of
# only issues is a day nobody shipped, a day without reviews is a day nothing
# was doubted, and a day without commits is a day nothing was built. This meter
# reads the same contribution API the profile graph reads, so it cannot flatter
# us.

set -euo pipefail

DAY="${1:-$(date +%F)}"
[[ "$DAY" =~ ^[0-9]{4}-[0-9]{2}-[0-9]{2}$ ]] || { echo "FATAL date must be YYYY-MM-DD" >&2; exit 2; }

read -r COMMITS ISSUES PRS REVIEWS <<<"$(gh api graphql -f query="{
  viewer { contributionsCollection(from: \"${DAY}T00:00:00Z\", to: \"${DAY}T23:59:59Z\") {
    totalCommitContributions
    totalIssueContributions
    totalPullRequestContributions
    totalPullRequestReviewContributions
  } }
}" --jq '.data.viewer.contributionsCollection | "\(.totalCommitContributions) \(.totalIssueContributions) \(.totalPullRequestContributions) \(.totalPullRequestReviewContributions)"')"

TOTAL=$((COMMITS + ISSUES + PRS + REVIEWS))
if (( TOTAL == 0 )); then
  printf 'BALANCE day=%s commits=0 issues=0 prs=0 reviews=0 verdict=EMPTY\n' "$DAY"
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

printf 'BALANCE day=%s commits=%d(%s‰) issues=%d(%s‰) prs=%d(%s‰) reviews=%d(%s‰) total=%d verdict=%s\n' \
  "$DAY" "$COMMITS" "$(pct "$COMMITS")" "$ISSUES" "$(pct "$ISSUES")" \
  "$PRS" "$(pct "$PRS")" "$REVIEWS" "$(pct "$REVIEWS")" "$TOTAL" "$VERDICT"
if (( GAP > 0 )); then
  printf 'DEFICIT commits=%d issues=%d prs=%d reviews=%d  (the %s leg is furthest behind: %d to clear it)\n' \
    "$N_C" "$N_I" "$N_P" "$N_R" "$LAG" "$GAP"
fi
