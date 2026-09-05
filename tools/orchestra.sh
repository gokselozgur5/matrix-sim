#!/usr/bin/env bash
# tools/orchestra.sh — guarded exact-head integration receipt (#1774)
#
# Usage: tools/orchestra.sh check <pr> <expected-issue>    judge the exact PR/issue binding
#        tools/orchestra.sh merge <pr> <expected-issue>    recheck, rebase-merge, verify parity
#        tools/orchestra.sh --selftest    run fixture cases; no token and no network
#        tools/orchestra.sh --help | -h   print this clause, and stop
#
# Exit 0 PASS · 1 FAILED (evidence does not satisfy the gate) · 2 REFUSED

set -u

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
PRSTATE="${ORCHESTRA_PRSTATE:-$ROOT/tools/prstate.sh}"
CHECKAGE="${ORCHESTRA_CHECKAGE:-$ROOT/tools/checkage.sh}"
MANUAL_REVIEW_SHA="${ORCHESTRA_MANUAL_REVIEW_SHA:-}"

usage() {
  awk 'NR==1 {next} !/^#/ {exit} /^#$/ {if (++blank == 2) exit} {print}' "$0"
}

# verdict <state> <stable evidence fields>
verdict() {
  printf 'ORCHESTRA VERDICT %s %s\n' "$1" "$2"
  case "$1" in PASS) return 0 ;; FAILED) return 1 ;; *) return 2 ;; esac
}

is_sha() {
  printf '%s' "$1" | grep -qE '^[0-9a-f]{40}$'
}

# Resolve the public door before any function containing a repository/network read.
case "${1:-}" in
  -h|--help) [ "$#" -eq 1 ] || { verdict REFUSED "stage=usage reason=extra-arguments"; exit $?; }; usage; exit 0 ;;
  --selftest) [ "$#" -eq 1 ] || { verdict REFUSED "stage=usage reason=extra-arguments"; exit $?; }; CMD=selftest ;;
  check|merge) CMD="$1" ;;
  "") usage >&2; verdict REFUSED "stage=usage reason=missing-command"; exit $? ;;
  *) verdict REFUSED "stage=usage reason=unknown-command"; exit $? ;;
esac
if [ "$CMD" != selftest ]; then
  [ "$#" -eq 3 ] || { verdict REFUSED "stage=usage reason=expected-pr-and-issue"; exit $?; }
  printf '%s' "$2" | grep -qE '^[1-9][0-9]*$' || { verdict REFUSED "stage=usage reason=pr-not-numeric"; exit $?; }
  printf '%s' "$3" | grep -qE '^[1-9][0-9]*$' || { verdict REFUSED "stage=usage reason=issue-not-numeric"; exit $?; }
fi

repo_from_origin() {
  git remote get-url origin 2>/dev/null \
    | sed -E 's#.*github\.com[/:]##; s#\.git$##'
}

# Check-runs are read for this commit, then selected by exact NAME and greatest
# monotonically assigned id. Completion time cannot order them: an older slow run
# may complete after a newer queued run starts, and must not bless that newer run.
# check_rows <sha>
check_rows() {
  gh api "repos/$REPO/commits/$1/check-runs?per_page=100" --paginate \
    --jq '.check_runs[] | [.name,.status,(.conclusion // "-"),(.completed_at // .started_at // ""),(.id|tostring)] | @tsv' 2>/dev/null
}

# review_status <sha>: the status endpoint itself binds the receipt to this exact commit.
review_status() {
  local rows
  rows="$(gh api "repos/$REPO/commits/$1/status?per_page=100" --paginate \
    --jq '.statuses[] | [.context,.state,.created_at,(.id|tostring)] | @tsv' 2>/dev/null || true)"
  printf '%s\n' "$rows" | awk -F '\t' '
    $1 == "orchestra/review" && ($3 > newest || ($3 == newest && $4 + 0 > id + 0)) {
      newest=$3; id=$4; state=$2
    }
    END { print (state == "" ? "absent" : state) }'
}

# named_check <name> <rows> -> newest exact-name result
named_check() {
  printf '%s\n' "$2" | awk -F '\t' -v want="$1" '
    $1 == want && $5 + 0 > id + 0 {
      found=1; id=$5; status=$2; conclusion=$3
    }
    END {
      if (!found) print "absent"
      else if (status != "completed") print "pending"
      else if (conclusion != "success") print "red"
      else print "ok"
    }'
}

closing_issues() {
  gh pr view "$PR" --repo "$REPO" --json closingIssuesReferences \
    --jq '.closingIssuesReferences[].number' 2>/dev/null
}

require_issue_binding() { # stage
  local stage="$1" refs shown
  if ! refs="$(closing_issues)"; then
    verdict REFUSED "stage=$stage pr=$PR repo=$REPO issue=$EXPECTED_ISSUE reason=closing-issues-unreadable"; return 2
  fi
  shown="$(printf '%s' "$refs" | tr '\n' ',' | sed 's/,$//')"
  [ "$refs" = "$EXPECTED_ISSUE" ] \
    || { verdict FAILED "stage=$stage pr=$PR repo=$REPO expected_issue=$EXPECTED_ISSUE closing_issues=${shown:-none}"; return 1; }
  return 0
}

require_open_build_unit() { # stage
  local stage="$1" row oldifs
  if ! row="$(gh issue view "$EXPECTED_ISSUE" --repo "$REPO" --json state,labels \
      --jq '[.state,(any(.labels[]; .name == "build-unit")|tostring)] | @tsv' 2>/dev/null)"; then
    verdict REFUSED "stage=$stage pr=$PR repo=$REPO issue=$EXPECTED_ISSUE reason=issue-unreadable"; return 2
  fi
  oldifs=$IFS; IFS=$'\t'; set -- $row; IFS=$oldifs
  [ "${1:-}" = OPEN ] && [ "${2:-}" = true ] \
    || { verdict FAILED "stage=$stage pr=$PR repo=$REPO issue=$EXPECTED_ISSUE state=${1:-unreadable} build_unit=${2:-false}"; return 1; }
  return 0
}

check_pr() {
  PR="$1"
  EXPECTED_ISSUE="$2"
  REPO="$(repo_from_origin || true)"
  printf '%s' "$REPO" | grep -qE '^[A-Za-z0-9._-]+/[A-Za-z0-9._-]+$' \
    || { verdict REFUSED "stage=identity pr=$PR reason=origin-repository-unreadable"; return; }

  API_REPO="$(gh api "repos/$REPO" --jq .full_name 2>/dev/null || true)"
  [ "$API_REPO" = "$REPO" ] \
    || { verdict REFUSED "stage=identity pr=$PR repo=$REPO api=${API_REPO:-unreadable}"; return; }

  META="$(gh api "repos/$REPO/pulls/$PR" \
    --jq '[.state,(.draft|tostring),(.mergeable|tostring),.mergeable_state,.head.sha,.head.repo.full_name,.base.sha,.base.ref,(.merged|tostring),(.merge_commit_sha // "-")] | @tsv' 2>/dev/null || true)"
  [ -n "$META" ] || { verdict REFUSED "stage=read pr=$PR repo=$REPO reason=pr-unreadable"; return; }
  OLDIFS=$IFS; IFS=$'\t'; set -- $META; IFS=$OLDIFS
  STATE="${1:-}"; DRAFT="${2:-}"; MERGEABLE="${3:-}"; CLEAN="${4:-}"
  HEAD_SHA="${5:-}"; HEAD_REPO="${6:-}"; BASE_SHA="${7:-}"; BASE_REF="${8:-}"
  is_sha "$HEAD_SHA" && is_sha "$BASE_SHA" \
    || { verdict REFUSED "stage=read pr=$PR repo=$REPO reason=non-full-sha"; return; }
  if [ "$MERGEABLE" = null ] || [ "$CLEAN" = unknown ]; then
    verdict REFUSED "stage=mergeability-read pr=$PR repo=$REPO head=$HEAD_SHA mergeable=$MERGEABLE merge_state=$CLEAN"; return
  fi
  [ "$HEAD_REPO" = "$REPO" ] \
    || { verdict FAILED "stage=head-repository pr=$PR repo=$REPO head_repo=${HEAD_REPO:-missing} head=$HEAD_SHA"; return; }
  [ "$STATE" = open ] && [ "$DRAFT" = false ] && [ "$MERGEABLE" = true ] && [ "$CLEAN" = clean ] && [ "$BASE_REF" = main ] \
    || { verdict FAILED "stage=eligibility pr=$PR repo=$REPO state=$STATE draft=$DRAFT mergeable=$MERGEABLE merge_state=$CLEAN head=$HEAD_SHA base=$BASE_SHA"; return; }

  require_issue_binding issue-binding || return $?
  require_open_build_unit issue-eligibility || return $?

  git fetch --quiet --no-tags origin "pull/$PR/head" >/dev/null 2>&1 \
    || { verdict REFUSED "stage=fetch pr=$PR repo=$REPO head=$HEAD_SHA reason=fetch-failed"; return; }
  LOCAL_HEAD="$(git rev-parse FETCH_HEAD 2>/dev/null || true)"
  git cat-file -e "$HEAD_SHA^{commit}" >/dev/null 2>&1 || LOCAL_HEAD=missing
  [ "$LOCAL_HEAD" = "$HEAD_SHA" ] \
    || { verdict FAILED "stage=head-equality pr=$PR repo=$REPO remote=$HEAD_SHA local=${LOCAL_HEAD:-missing}"; return; }
  git fetch --quiet --no-tags origin main >/dev/null 2>&1 \
    || { verdict REFUSED "stage=base-fetch pr=$PR repo=$REPO base=$BASE_SHA reason=fetch-failed"; return; }
  LOCAL_BASE="$(git rev-parse origin/main 2>/dev/null || true)"
  [ "$LOCAL_BASE" = "$BASE_SHA" ] \
    || { verdict FAILED "stage=base-equality pr=$PR repo=$REPO api_base=$BASE_SHA origin_main=${LOCAL_BASE:-missing}"; return; }
  git merge-base --is-ancestor "$BASE_SHA" "$HEAD_SHA" >/dev/null 2>&1 \
    || { verdict FAILED "stage=base-ancestry pr=$PR repo=$REPO head=$HEAD_SHA base=$BASE_SHA"; return; }

  PRSTATE_OUT="$(bash "$PRSTATE" --pr "$PR" 2>/dev/null || true)"
  printf '%s\n' "$PRSTATE_OUT" | grep -qE '^PR STATE VERDICT GREEN([[:space:]]|$)' \
    || { verdict FAILED "stage=prstate pr=$PR repo=$REPO head=$HEAD_SHA result=not-green"; return; }
  CHECKAGE_OUT="$(bash "$CHECKAGE" --pr "$PR" 2>/dev/null || true)"
  printf '%s\n' "$CHECKAGE_OUT" | grep -qE '^CHECKAGE VERDICT CURRENT([[:space:]]|$)' \
    || { verdict FAILED "stage=checkage pr=$PR repo=$REPO head=$HEAD_SHA result=not-current"; return; }

  if ! ROWS="$(check_rows "$HEAD_SHA")"; then
    verdict REFUSED "stage=checks-read pr=$PR repo=$REPO head=$HEAD_SHA reason=api-unreadable"; return
  fi
  [ -n "$ROWS" ] || { verdict FAILED "stage=checks pr=$PR repo=$REPO head=$HEAD_SHA litany=absent locks=absent review=unknown"; return; }
  LITANY="$(named_check litany "$ROWS")"; LOCKS="$(named_check locks "$ROWS")"
  if ! ENABLED="$(gh api "repos/$REPO/actions/variables?per_page=100" --paginate \
      --jq '.variables[] | select(.name == "ORCHESTRA_CODEX_ENABLED") | .value' 2>/dev/null)"; then
    verdict REFUSED "stage=review-config pr=$PR repo=$REPO head=$HEAD_SHA reason=api-unreadable"; return
  fi
  REVIEW=NOT_CONFIGURED
  case "$ENABLED" in
    ""|false) ;;
    true)
      REVIEW="$(review_status "$HEAD_SHA")"
      [ "$REVIEW" = success ] || REVIEW="${REVIEW:-absent}"
      ;;
    *) verdict REFUSED "stage=review-config pr=$PR repo=$REPO head=$HEAD_SHA reason=ambiguous-or-invalid-value"; return ;;
  esac
  [ "$LITANY" = ok ] && [ "$LOCKS" = ok ] && { [ "$REVIEW" = success ] || [ "$REVIEW" = NOT_CONFIGURED ]; } \
    || { verdict FAILED "stage=checks pr=$PR repo=$REPO head=$HEAD_SHA base=$BASE_SHA litany=$LITANY locks=$LOCKS review=$REVIEW"; return; }

  END_META="$(gh api "repos/$REPO/pulls/$PR" \
    --jq '[.state,(.draft|tostring),.head.sha,.head.repo.full_name,.base.sha,.base.ref] | @tsv' 2>/dev/null || true)"
  [ -n "$END_META" ] || { verdict REFUSED "stage=check-reread pr=$PR repo=$REPO reason=metadata-unreadable"; return; }
  OLDIFS=$IFS; IFS=$'\t'; set -- $END_META; IFS=$OLDIFS
  [ "${1:-}" = open ] && [ "${2:-}" = false ] && [ "${3:-}" = "$HEAD_SHA" ] \
    && [ "${4:-}" = "$REPO" ] && [ "${5:-}" = "$BASE_SHA" ] && [ "${6:-}" = main ] \
    || { verdict FAILED "stage=check-race pr=$PR repo=$REPO state=${1:-unreadable} draft=${2:-unreadable} checked_head=$HEAD_SHA current_head=${3:-unreadable} head_repo=${4:-unreadable} checked_base=$BASE_SHA current_base=${5:-unreadable}"; return; }
  require_issue_binding check-issue-race || return $?

  verdict PASS "stage=check pr=$PR repo=$REPO issue=$EXPECTED_ISSUE head=$HEAD_SHA base=$BASE_SHA base_ref=$BASE_REF local_head=$LOCAL_HEAD prstate=GREEN checkage=CURRENT litany=success locks=success review=$REVIEW issue_binding=observed"
}

merge_pr() {
  PR="$1"
  EXPECTED_ISSUE="$2"
  CHECK_TMP="$(mktemp "${TMPDIR:-/tmp}/orchestra-check.XXXXXX")" \
    || { verdict REFUSED "stage=check pr=$PR reason=tempfile-failed"; return; }
  check_pr "$PR" "$EXPECTED_ISSUE" > "$CHECK_TMP"; RC=$?; PRE="$(cat "$CHECK_TMP")"; rm -f "$CHECK_TMP"
  [ "$RC" -eq 0 ] || { printf '%s\n' "$PRE"; return "$RC"; }
  if [ "$REVIEW" != success ]; then
    if ! is_sha "$MANUAL_REVIEW_SHA" || [ "$MANUAL_REVIEW_SHA" != "$HEAD_SHA" ]; then
      verdict REFUSED "stage=review pr=$PR repo=$REPO head=$HEAD_SHA review=NOT_CONFIGURED manual_receipt=${MANUAL_REVIEW_SHA:-absent}"
      return
    fi
    REVIEW=manual-exact-head
  fi
  PRE_TREE="$(git show -s --format=%T "$HEAD_SHA" 2>/dev/null || true)"
  is_sha "$PRE_TREE" || { verdict REFUSED "stage=merge pr=$PR repo=$REPO head=$HEAD_SHA reason=head-tree-unreadable"; return; }
  NOW="$(gh api "repos/$REPO/pulls/$PR" \
    --jq '[.state,(.draft|tostring),.head.sha,.head.repo.full_name,.base.sha,.base.ref] | @tsv' 2>/dev/null || true)"
  [ -n "$NOW" ] || { verdict REFUSED "stage=pre-merge-read pr=$PR repo=$REPO reason=metadata-unreadable"; return; }
  OLDIFS=$IFS; IFS=$'\t'; set -- $NOW; IFS=$OLDIFS
  [ "${1:-}" = open ] && [ "${2:-}" = false ] && [ "${3:-}" = "$HEAD_SHA" ] \
    && [ "${4:-}" = "$REPO" ] && [ "${5:-}" = "$BASE_SHA" ] && [ "${6:-}" = main ] \
    || { verdict FAILED "stage=pre-merge-race pr=$PR repo=$REPO state=${1:-unreadable} draft=${2:-unreadable} checked_head=$HEAD_SHA current_head=${3:-unreadable} head_repo=${4:-unreadable} checked_base=$BASE_SHA current_base=${5:-unreadable}"; return; }
  require_issue_binding pre-merge-issue-race || return $?
  require_open_build_unit pre-merge-issue || return $?
  # GitHub exposes compare-and-swap for the head only. Closing-issue metadata is
  # observed immediately before and after the merge; the API cannot lock it.
  gh pr merge "$PR" --repo "$REPO" --match-head-commit "$HEAD_SHA" --rebase >/dev/null 2>&1 \
    || { verdict FAILED "stage=merge pr=$PR repo=$REPO head=$HEAD_SHA reason=merge-command-failed"; return; }
  git fetch --quiet --no-tags origin main >/dev/null 2>&1 \
    || { verdict REFUSED "stage=post-fetch pr=$PR repo=$REPO reason=origin-main-unreadable"; return; }
  POST="$(gh api "repos/$REPO/pulls/$PR" --jq '[.state,(.merged|tostring),(.merge_commit_sha // "-")] | @tsv' 2>/dev/null || true)"
  OLDIFS=$IFS; IFS=$'\t'; set -- $POST; IFS=$OLDIFS
  PSTATE="${1:-}"; PMERGED="${2:-}"; MERGE_SHA="${3:-}"
  is_sha "$MERGE_SHA" || { verdict FAILED "stage=post-merge pr=$PR repo=$REPO merged=$PMERGED merge_sha=${MERGE_SHA:-missing}"; return; }
  LOCAL_MAIN="$(git rev-parse origin/main 2>/dev/null || true)"
  REACHABLE=no
  git merge-base --is-ancestor "$MERGE_SHA" origin/main >/dev/null 2>&1 && REACHABLE=yes
  API_TREE="$(gh api "repos/$REPO/git/commits/$MERGE_SHA" --jq .tree.sha 2>/dev/null || true)"
  LOCAL_TREE="$(git show -s --format=%T "$MERGE_SHA" 2>/dev/null || true)"

  require_issue_binding post-merge-issue || return $?
  ISSUES="$EXPECTED_ISSUE"
  PARITY=complete; ISSUE_EVIDENCE=""
  for ISSUE in $ISSUES; do
    IR="$(gh issue view "$ISSUE" --repo "$REPO" --json state,stateReason,labels \
      --jq '[.state,(.stateReason // "-"),(any(.labels[]; .name == "status:done")|tostring)] | @tsv' 2>/dev/null || true)"
    OLDIFS=$IFS; IFS=$'\t'; set -- $IR; IFS=$OLDIFS
    IS="${1:-unreadable}"; IREASON="${2:--}"; ILABEL="${3:-false}"
    OWNER="${REPO%/*}"; NAME="${REPO#*/}"
    IPROJECT="$(gh api graphql -F owner="$OWNER" -F name="$NAME" -F number="$ISSUE" \
      --raw-field query='query($owner:String!,$name:String!,$number:Int!){repository(owner:$owner,name:$name){issue(number:$number){projectItems(first:100){nodes{project{number} fieldValueByName(name:"Status"){... on ProjectV2ItemFieldSingleSelectValue{name}}}}}}}' \
      --jq '[.data.repository.issue.projectItems.nodes[] | select(.project.number == 3 and .fieldValueByName.name == "Done")] | length > 0' 2>/dev/null || true)"
    [ "$IS" = CLOSED ] && [ "$IREASON" = COMPLETED ] && [ "$ILABEL" = true ] && [ "$IPROJECT" = true ] || PARITY=incomplete
    ISSUE_EVIDENCE="${ISSUE_EVIDENCE}${ISSUE_EVIDENCE:+,}$ISSUE:$IS/$IREASON/label-$ILABEL/project-$IPROJECT"
  done
  [ "$PSTATE" = closed ] && [ "$PMERGED" = true ] && [ "$REACHABLE" = yes ] \
    && is_sha "$API_TREE" && [ "$API_TREE" = "$LOCAL_TREE" ] && [ "$LOCAL_TREE" = "$PRE_TREE" ] && [ "$PARITY" = complete ] \
    || { verdict FAILED "stage=post-merge pr=$PR repo=$REPO state=$PSTATE merged=$PMERGED merge=$MERGE_SHA origin_main=$LOCAL_MAIN reachable=$REACHABLE head_tree=$PRE_TREE api_tree=${API_TREE:-unreadable} local_tree=${LOCAL_TREE:-unreadable} parity=INCOMPLETE issues=$ISSUE_EVIDENCE"; return; }
  verdict PASS "stage=merge pr=$PR repo=$REPO head=$HEAD_SHA merge=$MERGE_SHA origin_main=$LOCAL_MAIN reachable=yes tree=$LOCAL_TREE issues=$ISSUE_EVIDENCE parity=COMPLETE head_lock=atomic issue_binding=pre/post-observed metadata_lock=unavailable"
}

selftest() {
  TMP="$(mktemp -d "${TMPDIR:-/tmp}/orchestra.XXXXXX")" || exit 1
  trap 'rm -rf "$TMP"' EXIT HUP INT TERM
  mkdir -p "$TMP/bin"; STATE_FILE="$TMP/state"
  cat > "$TMP/bin/gh" <<'EOF'
#!/usr/bin/env bash
case " $* " in
  *" pr merge "*)
    [ "${CASE:-}" = merge_fail ] && exit 1
    case " $* " in *" --match-head-commit aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa "*) ;; *) exit 1 ;; esac
    case " $* " in *" --rebase "*) ;; *) exit 1 ;; esac
    echo merged > "$ORCHESTRA_FIX_STATE"; exit 0 ;;
  *" pr view "*)
    refs=0; [ -f "$ORCHESTRA_FIX_STATE.refs" ] && refs="$(cat "$ORCHESTRA_FIX_STATE.refs")"; refs=$((refs + 1)); echo "$refs" > "$ORCHESTRA_FIX_STATE.refs"
    case "${CASE:-}" in
      issue_absent) : ;;
      issue_wrong) echo 1773 ;;
      issue_multiple) printf '1774\n1775\n' ;;
      issue_changed_check) [ "$refs" -eq 2 ] && echo 1775 || echo 1774 ;;
      issue_changed_merge) [ "$refs" -eq 3 ] && echo 1775 || echo 1774 ;;
      issue_changed_post) [ "$refs" -eq 4 ] && echo 1775 || echo 1774 ;;
      *) echo 1774 ;;
    esac
    exit 0 ;;
  *" issue view "*)
    if [ ! -s "$ORCHESTRA_FIX_STATE" ]; then
      state=OPEN; build=true
      [ "${CASE:-}" = issue_closed ] && state=CLOSED
      [ "${CASE:-}" = issue_not_build_unit ] && build=false
      printf '%s\t%s\n' "$state" "$build"
    elif [ "${CASE:-}" = parity_bad ]; then printf 'CLOSED\tCOMPLETED\tfalse\n'
    else printf 'CLOSED\tCOMPLETED\ttrue\n'; fi
    exit 0 ;;
  *" api graphql "*) echo true; exit 0 ;;
esac
case "$2" in
  repos/gokselozgur5/matrix-sim) echo gokselozgur5/matrix-sim ;;
  repos/gokselozgur5/matrix-sim/pulls/7)
    if [ -s "$ORCHESTRA_FIX_STATE" ]; then printf 'closed\ttrue\tcccccccccccccccccccccccccccccccccccccccc\n'
    else
      case " $* " in
        *'--jq [.state,(.draft|tostring),.head.sha,.head.repo.full_name,.base.sha,.base.ref'* )
          reads=0; [ -f "$ORCHESTRA_FIX_STATE.reads" ] && reads="$(cat "$ORCHESTRA_FIX_STATE.reads")"; reads=$((reads + 1)); echo "$reads" > "$ORCHESTRA_FIX_STATE.reads"
          state=open; draft=false
          { [ "${CASE:-}" = check_state_race ] && [ "$reads" -eq 1 ]; } && state=closed
          { [ "${CASE:-}" = merge_draft_race ] && [ "$reads" -eq 2 ]; } && draft=true
          if { [ "${CASE:-}" = check_head_race ] && [ "$reads" -eq 1 ]; } || { [ "${CASE:-}" = merge_head_race ] && [ "$reads" -eq 2 ]; }; then head=dddddddddddddddddddddddddddddddddddddddd; else head=aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa; fi
          if { [ "${CASE:-}" = check_base_race ] && [ "$reads" -eq 1 ]; } || { [ "${CASE:-}" = merge_base_race ] && [ "$reads" -eq 2 ]; }; then base=dddddddddddddddddddddddddddddddddddddddd; else base=bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb; fi
          printf '%s\t%s\t%s\tgokselozgur5/matrix-sim\t%s\tmain\n' "$state" "$draft" "$head" "$base"
          exit 0 ;;
      esac
      draft=false; [ "${CASE:-}" = draft ] && draft=true
      mergeable=true; merge_state=clean; [ "${CASE:-}" = mergeability_unknown ] && { mergeable=null; merge_state=unknown; }
      base_ref=main; [ "${CASE:-}" = wrong_base_ref ] && base_ref=unit/old
      head_repo=gokselozgur5/matrix-sim; [ "${CASE:-}" = fork_head ] && head_repo=somebody/fork
      printf 'open\t%s\t%s\t%s\taaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa\t%s\tbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb\t%s\tfalse\t-\n' "$draft" "$mergeable" "$merge_state" "$head_repo" "$base_ref"
    fi ;;
  repos/gokselozgur5/matrix-sim/actions/variables?per_page=100)
    case "${CASE:-}" in no_config*) : ;; config_unreadable) exit 1 ;; config_invalid) printf 'true\nfalse\n' ;; *) echo true ;; esac ;;
  repos/gokselozgur5/matrix-sim/commits/aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa/check-runs*)
    [ "${CASE:-}" = checks_unreadable ] && exit 1
    case "${CASE:-}" in
      red_ci) locks='completed	failure	2026-09-05T12:00:00Z	2' ;; pending_ci) locks='in_progress	-	2026-09-05T12:00:00Z	2' ;; absent_ci) locks='' ;;
      old_red_new_green) locks='completed	failure	2026-09-05T11:00:00Z	1\nlocks	completed	success	2026-09-05T12:00:00Z	2' ;;
      new_red_old_green) locks='completed	success	2026-09-05T11:00:00Z	1\nlocks	completed	failure	2026-09-05T12:00:00Z	2' ;;
      overlap_old_completed_late) locks='completed	success	2026-09-05T13:00:00Z	1\nlocks	in_progress	-	2026-09-05T12:00:00Z	2' ;;
      *) locks='completed	success	2026-09-05T12:00:00Z	2' ;;
    esac
    printf 'litany\tcompleted\tsuccess\t2026-09-05T12:00:00Z\t2\n'
    [ -n "$locks" ] && printf 'locks\t%b\n' "$locks"
    true
    ;;
  repos/gokselozgur5/matrix-sim/commits/aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa/status*)
    state=success; [ "${CASE:-}" = stale_review ] && state=absent
    if [ "${CASE:-}" = review_new_failure ]; then printf 'orchestra/review\tfailure\t2026-09-05T12:00:00Z\t2\norchestra/review\tsuccess\t2026-09-05T11:00:00Z\t1\n'
    elif [ "$state" = success ]; then printf 'orchestra/review\tsuccess\t2026-09-05T12:00:00Z\t2\norchestra/review\tfailure\t2026-09-05T11:00:00Z\t1\n'; fi ;;
  repos/gokselozgur5/matrix-sim/git/commits/cccccccccccccccccccccccccccccccccccccccc) echo eeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee ;;
  *) exit 1 ;;
esac
EOF
  cat > "$TMP/bin/git" <<'EOF'
#!/usr/bin/env bash
case "$1 $2 $3" in
  "remote get-url origin") echo https://github.com/gokselozgur5/matrix-sim.git ;;
  "fetch --quiet --no-tags") exit 0 ;;
  "rev-parse FETCH_HEAD ") [ "${CASE:-}" = head_mismatch ] && echo dddddddddddddddddddddddddddddddddddddddd || echo aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa ;;
  "rev-parse origin/main ")
    if [ -s "$ORCHESTRA_FIX_STATE" ]; then echo ffffffffffffffffffffffffffffffffffffffff
    elif [ "${CASE:-}" = base_mismatch ]; then echo dddddddddddddddddddddddddddddddddddddddd
    else echo bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb; fi ;;
  "cat-file -e aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa^{commit}") exit 0 ;;
  "merge-base --is-ancestor cccccccccccccccccccccccccccccccccccccccc") exit 0 ;;
  "merge-base --is-ancestor bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb") [ "${CASE:-}" = base_not_ancestor ] && exit 1 || exit 0 ;;
  "show -s --format=%T")
    if [ "${CASE:-}" = tree_mismatch ] && [ "$4" = aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa ]; then echo dddddddddddddddddddddddddddddddddddddddd
    else echo eeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee; fi ;;
  *) exit 1 ;;
esac
EOF
  cat > "$TMP/bin/prstate" <<'EOF'
#!/usr/bin/env bash
[ "${CASE:-}" = prstate_bad ] && echo 'PR STATE VERDICT RED runs=1' || echo 'PR STATE VERDICT GREEN runs=2'
EOF
  cat > "$TMP/bin/checkage" <<'EOF'
#!/usr/bin/env bash
[ "${CASE:-}" = checkage_bad ] && echo 'CHECKAGE VERDICT STALE run=1' || echo 'CHECKAGE VERDICT CURRENT run=1'
EOF
  chmod +x "$TMP/bin/gh" "$TMP/bin/git"
  chmod 644 "$TMP/bin/prstate" "$TMP/bin/checkage"  # production judges are sourced through bash too
  [ ! -x "$TMP/bin/prstate" ] && [ ! -x "$TMP/bin/checkage" ] || return 1
  PASS=0; FAIL=0
  case_is() { # name command case expected-code expected-stage
    N="$1"; CMD="$2"; C="$3"; WANT="$4"; STAGE="$5"
    : > "$STATE_FILE"; rm -f "$STATE_FILE.reads" "$STATE_FILE.refs"; [ "$CMD" = merge ] || rm -f "$STATE_FILE"
    MANUAL=""; [ "$C" = no_config_manual ] && MANUAL=aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa
    [ "$C" = no_config_bad ] && MANUAL=dddddddddddddddddddddddddddddddddddddddd
    OUT="$(PATH="$TMP/bin:$PATH" CASE="$C" ORCHESTRA_FIX_STATE="$STATE_FILE" ORCHESTRA_MANUAL_REVIEW_SHA="$MANUAL" \
      ORCHESTRA_PRSTATE="$TMP/bin/prstate" ORCHESTRA_CHECKAGE="$TMP/bin/checkage" \
      "$0" "$CMD" 7 1774 2>/dev/null)"; GOT=$?
    LINES="$(printf '%s\n' "$OUT" | wc -l | tr -d ' ')"
    if [ "$GOT" -eq "$WANT" ] && [ "$LINES" -eq 1 ] && printf '%s' "$OUT" | grep -q "stage=$STAGE"; then
      PASS=$((PASS + 1)); printf 'ORCHESTRA CASE %s PASS\n' "$N"
    else
      FAIL=$((FAIL + 1)); printf 'ORCHESTRA CASE %s FAIL want=%s/%s got=%s/%s output=[%s]\n' "$N" "$WANT" "$STAGE" "$GOT" "$LINES" "$OUT"
    fi
  }
  case_is clean-check check clean 0 check
  case_is issue-absent check issue_absent 1 issue-binding
  case_is issue-wrong check issue_wrong 1 issue-binding
  case_is issue-multiple check issue_multiple 1 issue-binding
  case_is issue-changed-during-check check issue_changed_check 1 check-issue-race
  case_is issue-closed check issue_closed 1 issue-eligibility
  case_is issue-not-build-unit check issue_not_build_unit 1 issue-eligibility
  case_is stale-review check stale_review 1 checks
  case_is draft check draft 1 eligibility
  case_is mergeability-unknown check mergeability_unknown 2 mergeability-read
  case_is wrong-base-ref check wrong_base_ref 1 eligibility
  case_is fork-head check fork_head 1 head-repository
  case_is head-mismatch check head_mismatch 1 head-equality
  case_is base-mismatch check base_mismatch 1 base-equality
  case_is base-not-ancestor check base_not_ancestor 1 base-ancestry
  case_is red-ci check red_ci 1 checks
  case_is pending-ci check pending_ci 1 checks
  case_is absent-ci check absent_ci 1 checks
  case_is checks-api-unreadable check checks_unreadable 2 checks-read
  case_is old-red-new-green check old_red_new_green 0 check
  case_is new-red-old-green check new_red_old_green 1 checks
  case_is overlap-old-completes-late check overlap_old_completed_late 1 checks
  case_is newest-review-failure check review_new_failure 1 checks
  case_is review-config-unreadable check config_unreadable 2 review-config
  case_is malformed-review-config check config_invalid 2 review-config
  case_is prstate-red check prstate_bad 1 prstate
  case_is checkage-stale check checkage_bad 1 checkage
  case_is failed-merge merge merge_fail 1 merge
  case_is issue-changed-before-merge merge issue_changed_merge 1 pre-merge-issue-race
  case_is issue-changed-after-merge merge issue_changed_post 1 post-merge-issue
  case_is check-head-race check check_head_race 1 check-race
  case_is check-base-race check check_base_race 1 check-race
  case_is check-state-race check check_state_race 1 check-race
  case_is merge-head-race merge merge_head_race 1 pre-merge-race
  case_is merge-base-race merge merge_base_race 1 pre-merge-race
  case_is merge-draft-race merge merge_draft_race 1 pre-merge-race
  case_is incomplete-parity merge parity_bad 1 post-merge
  case_is canonical-tree-mismatch merge tree_mismatch 1 post-merge
  case_is review-not-configured-check check no_config 0 check
  case_is review-not-configured-merge merge no_config 2 review
  case_is wrong-manual-review-head merge no_config_bad 2 review
  case_is exact-manual-review-head merge no_config_manual 0 merge
  ZERO="$(PATH="$TMP/bin:$PATH" "$0" check 0 1774 2>/dev/null)"; ZERO_RC=$?
  if [ "$ZERO_RC" -eq 2 ] && printf '%s' "$ZERO" | grep -q 'reason=pr-not-numeric'; then PASS=$((PASS + 1)); echo 'ORCHESTRA CASE zero-pr-refused PASS'; else FAIL=$((FAIL + 1)); echo 'ORCHESTRA CASE zero-pr-refused FAIL'; fi
  if [ "$FAIL" -eq 0 ]; then printf 'ORCHESTRA SELFTEST VERDICT PASS cases=%d failed=0\n' "$PASS"; return 0; fi
  printf 'ORCHESTRA SELFTEST VERDICT FAIL cases=%d failed=%d\n' "$((PASS + FAIL))" "$FAIL"; return 1
}

if [ "$CMD" = selftest ]; then selftest; exit $?; fi
command -v gh >/dev/null 2>&1 && command -v git >/dev/null 2>&1 \
  || { verdict REFUSED "stage=tools pr=$2 reason=gh-or-git-missing"; exit $?; }
[ -f "$PRSTATE" ] && [ -f "$CHECKAGE" ] \
  || { verdict REFUSED "stage=tools pr=$2 reason=judge-missing"; exit $?; }
if [ "$CMD" = check ]; then check_pr "$2" "$3"; exit $?; else merge_pr "$2" "$3"; exit $?; fi
