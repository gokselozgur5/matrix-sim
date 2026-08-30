#!/usr/bin/env bash
# tools/provenance.sh — for every rule this repository enforces, who supplies
# it, who reads it, and what makes the answer mandatory? (#1769)
#
# Usage: tools/provenance.sh              the inventory and its census line
#        tools/provenance.sh --list       one row per rule, all six fields
#        tools/provenance.sh --offline    do not reach the API; server-side rows stay UNKNOWN
#        tools/provenance.sh --selftest   run the reader's cases; nothing is fetched
#        tools/provenance.sh --help | -h  print this clause, and stop
#
# THE FINDING. Every lane in this repository runs on `pull_request`, and every
# lane definition is a file a pull request can edit. Measured server-side on
# 2026-08-30: `main` has no branch protection (the API answers 404 "Branch not
# protected"), the repository has no rulesets (empty array), and there is no
# CODEOWNERS. So a green check is the judged branch's own statement about
# itself, and nothing outside the branch requires it.
#
# It is not hypothetical. #1738 records the census fence gaining an exclusion
# for `ci/` "on no authority but the repairing unit's own" — a unit widened the
# rule that judges it while repairing something else, and the widening read
# exactly like the repair. #1746 is that shape arriving a second time.
#
# WHY THIS COUNTS AND JUDGES NOTHING. #1311's rule is that a gate installs at
# zero and not at one, and nobody knew the number. #1749 held the same boundary
# when it counted verdict words and called none of them dead. The self-supplied
# count is a census field. Installing protection, writing CODEOWNERS, or gating
# on the number are three later arguments and none of them is this one.
#
# UNKNOWN IS A FIRST-CLASS OUTCOME, and the reason the field exists. Server-side
# state cannot be read from a working tree. Seeing `on: pull_request` proves a
# lane RUNS; it never proves anything REQUIRES it. A tool that inferred
# "unprotected" from a local absence would be making the same mistake in the
# other direction, so when the API cannot be reached the row says UNKNOWN and
# the census counts it as such.
#
# Nothing is built and no probe is executed. This reads the workflow files, and
# — unless --offline — asks the API three questions.

set -uo pipefail

cd "$(dirname "$0")/.."

WORKFLOWS=.github/workflows
MODE=count
ONLINE=1
REPO_SLUG="${PROVENANCE_REPO:-gokselozgur5/matrix-sim}"

usage() {
  awk 'NR==1 {next} !/^#/ {exit} /^#$/ {if (++blank == 2) exit} {print}' "$0"
}

while [ $# -gt 0 ]; do
  case "$1" in
    --list) MODE=list ;;
    --offline) ONLINE=0 ;;
    --selftest) MODE=selftest ;;
    --workflows) shift; WORKFLOWS="${1:-}" ;;
    -h|--help) usage; exit 0 ;;
    *) echo "FATAL unknown argument: $1 (this tool takes --list, --offline, --workflows DIR, --selftest, or nothing)" >&2; exit 2 ;;
  esac
  shift
done

# ---------------------------------------------------------------- readers ----

# The events a workflow answers to, comma-joined: `pull_request,push`.
#
# READ FROM THE `on:` BLOCK AND NOT FROM THE WHOLE FILE. Every one of these
# files opens with a long comment that names the triggers in prose — the litany
# explains why it is a second workflow, the determinism lane explains why it is
# not a step in locks — so a grep for `pull_request` over the file answers from
# the essay rather than from the configuration.
events_of() {                         # events_of <workflow file>
  awk '
    /^on:/            { inon = 1; next }
    inon && /^[a-z_]+:/ { inon = 0 }
    inon && /^  [a-z_]+:/ {
      key = $1; sub(":", "", key)
      out = out (out == "" ? "" : ",") key
    }
    END { print (out == "" ? "none" : out) }
  ' "$1"
}

# One row per named step. A step is the unit of enforcement here: it is what
# fails, what the summary names, and what a reader goes looking for.
steps_of() {                          # steps_of <workflow file>
  grep -n '^      - name:' "$1" \
    | sed 's/^\([0-9]*\):      - name: /\1\t/'
}

# Does a pull request branch's own diff decide what this rule says? For a rule
# whose source is a tracked file, the answer is yes unless something outside the
# branch says otherwise, and that "something" is the server-side question below.
tracked() {                           # tracked <path>
  git ls-files --error-unmatch "$1" >/dev/null 2>&1
}

# ------------------------------------------------------- server-side reads ----

# Three questions, each answered VERIFIED:<value> or UNKNOWN:<why>. A failure to
# reach the API is not evidence of absence and never becomes one.
ask() {                               # ask <api path> <jq filter> <absent value>
  local body status
  if [ "$ONLINE" -eq 0 ]; then
    echo "UNKNOWN:not-asked"
    return
  fi
  body="$(gh api "$1" 2>&1)"
  status=$?
  if [ "$status" -ne 0 ]; then
    case "$body" in
      *"Branch not protected"*|*'"status":"404"'*|*'HTTP 404'*) echo "VERIFIED:$3" ;;
      *) echo "UNKNOWN:api-unreachable" ;;
    esac
    return
  fi
  printf '%s' "$body" | jq -r "$2" 2>/dev/null | head -1 | sed 's/^/VERIFIED:/'
}

server_side() {
  PROTECTION="$(ask "repos/$REPO_SLUG/branches/main/protection" \
      'if .required_status_checks then "required-checks" else "protected-no-checks" end' \
      'none')"
  RULESETS="$(ask "repos/$REPO_SLUG/rulesets" \
      'if length == 0 then "none" else "\(length) ruleset(s)" end' 'none')"
  if tracked CODEOWNERS || tracked .github/CODEOWNERS || tracked docs/CODEOWNERS; then
    CODEOWNERS="VERIFIED:present"
  else
    CODEOWNERS="VERIFIED:absent"
  fi
}

# ------------------------------------------------------------- the report ----

row() {                               # row <rule> <source> <event> <mutable> <required> <status>
  printf 'PROVENANCE rule=%s source=%s event=%s mutable_by_pr=%s required_by=%s %s\n' \
    "$1" "$2" "$3" "$4" "$5" "$6"
}

report() {
  local rules=0 self_supplied=0 unknown=0 required=0 lane_rules=0
  local requirement status

  server_side

  # WHAT MAKES A LANE'S RESULT MANDATORY is one answer for every step in it, so
  # it is read once and applied to each. A required status check is named at the
  # workflow level, not the step level, and neither protection nor a ruleset can
  # single out one step of a lane.
  case "$PROTECTION" in
    VERIFIED:required-checks) requirement=branch-protection; status=VERIFIED ;;
    VERIFIED:*)
      case "$RULESETS" in
        VERIFIED:none) requirement=nothing; status=VERIFIED ;;
        VERIFIED:*)    requirement=ruleset; status=VERIFIED ;;
        *)             requirement=unread; status=UNKNOWN ;;
      esac
      ;;
    *) requirement=unread; status=UNKNOWN ;;
  esac

  for workflow in "$WORKFLOWS"/*.yml; do
    [ -e "$workflow" ] || continue
    local event mutable
    event="$(events_of "$workflow")"
    if tracked "$workflow"; then mutable="$workflow"; else mutable=untracked; fi
    while IFS=$'\t' read -r line name; do
      [ -n "$name" ] || continue
      rules=$((rules + 1)); lane_rules=$((lane_rules + 1))
      # AN UNTRACKED LANE FILE IS UNKNOWN AND NOT ZERO. The first writing let it
      # fall through every counter — counted in `rules=`, added to none of the
      # three — and the identity below is what said so, on the first fixture that
      # was not a checkout. Who supplies a rule whose file git has never seen is
      # genuinely not answerable from here, and that is what the field is for.
      if [ "$status" = UNKNOWN ] || [ "$mutable" = untracked ]; then
        unknown=$((unknown + 1))
      elif [ "$requirement" = nothing ]; then
        self_supplied=$((self_supplied + 1))
      else
        required=$((required + 1))
      fi
      if [ "$MODE" = list ]; then
        row "$(printf '%s' "$name" | tr ' ' '_')" "$workflow:$line" "$event" \
            "$mutable" "$requirement" "$status"
      fi
    done <<< "$(steps_of "$workflow")"
  done

  # The three server-side facts are rules in their own right — they are what a
  # later unit would change — so they are rows and not a footnote.
  rules=$((rules + 3))
  for fact in "branch_protection $PROTECTION" "rulesets $RULESETS" "codeowners $CODEOWNERS"; do
    local key="${fact%% *}" value="${fact#* }"
    local verdict="${value%%:*}" detail="${value#*:}"
    case "$verdict" in
      UNKNOWN) unknown=$((unknown + 1)) ;;
      *) [ "$detail" = none ] || [ "$detail" = absent ] \
           && self_supplied=$((self_supplied + 1)) || required=$((required + 1)) ;;
    esac
    if [ "$MODE" = list ]; then
      row "$key" "github:$REPO_SLUG" "server-side" "no" "$detail" "$verdict"
    fi
  done

  # THE IDENTITY IS ASSERTED, NOT ARGUED. Every rule read leaves through exactly
  # one of the three counters; a fourth outcome added to the loop breaks this
  # line rather than quietly making `self_supplied=` mean less.
  if [ $((self_supplied + required + unknown)) -ne "$rules" ]; then
    echo "PROVENANCE VERDICT CENSUS_DOES_NOT_ADD_UP rules=$rules self_supplied=$self_supplied required=$required unknown=$unknown" >&2
    return 4
  fi
  # NOTHING READ MEANS NO LANE WAS READ, and the first writing of this line asked
  # `rules -eq 0` — which the three server-side rows make impossible, so the
  # finding could never fire. A floor that its own bookkeeping lifts off zero is
  # the unreachable-guard shape #1741 counted, authored here by accident and
  # caught by the case rather than by reading.
  if [ "$lane_rules" -eq 0 ]; then
    echo "PROVENANCE VERDICT NOTHING_READ workflows=$WORKFLOWS" >&2
    return 4
  fi

  echo "PROVENANCE_CENSUS workflows=$(ls "$WORKFLOWS"/*.yml 2>/dev/null | wc -l | tr -d ' ')" \
       "rules=$rules repo=$REPO_SLUG online=$ONLINE"
  # COUNTED, not PASSED. There is no threshold here and installing one is a
  # later unit's argument (#1311: a gate installs at zero, and this is the
  # reading that finds out whether zero is where it would install).
  echo "PROVENANCE VERDICT COUNTED rules=$rules self_supplied=$self_supplied required=$required unknown=$unknown"
  return 0
}

# --------------------------------------------------------------- selftest ----

selftest() {
  local pass=0 fail=0 tmp got
  tmp="$(mktemp -d "${TMPDIR:-/tmp}/provenance.XXXXXX")"
  trap 'rm -rf "${tmp:-}"' EXIT

  check() {                           # check <name> <want> <got>
    if [ "$2" = "$3" ]; then
      pass=$((pass + 1)); printf 'PROVENANCE case=%-26s want=[%s] got=[%s] OK\n' "$1" "$2" "$3"
    else
      fail=$((fail + 1)); printf 'PROVENANCE case=%-26s want=[%s] got=[%s] BROKEN\n' "$1" "$2" "$3"
    fi
  }

  # The reading that matters: the `on:` block, not the file. Every workflow here
  # opens with an essay that names its triggers in prose, so this case is the
  # one that separates the configuration from the commentary.
  printf '%s\n' '# a comment naming pull_request and schedule in prose' \
                'name: sample' 'on:' '  push:' '    branches: [main]' 'jobs:' > "$tmp/prose.yml"
  check events-ignore-prose 'push' "$(events_of "$tmp/prose.yml")"

  printf '%s\n' 'name: two' 'on:' '  pull_request:' '    branches: [main]' \
                '  push:' '    branches: [main]' 'jobs:' > "$tmp/two.yml"
  check events-two 'pull_request,push' "$(events_of "$tmp/two.yml")"

  printf '%s\n' 'name: cron' 'on:' '  schedule:' '    - cron: "0 3 * * *"' 'jobs:' > "$tmp/cron.yml"
  check events-schedule 'schedule' "$(events_of "$tmp/cron.yml")"

  # `on:` closing at the next top-level key. Without that, `jobs:` and every key
  # under it would read as a trigger.
  printf '%s\n' 'name: bounded' 'on:' '  push:' 'jobs:' '  build:' '    steps: []' > "$tmp/bounded.yml"
  check events-stop-at-jobs 'push' "$(events_of "$tmp/bounded.yml")"

  printf '%s\n' 'name: none' 'jobs:' > "$tmp/none.yml"
  check events-absent 'none' "$(events_of "$tmp/none.yml")"

  # A step is a named step at the lane's indentation. A `name:` on the workflow
  # or on a job is not one, and a reader that counts those inflates the
  # population with things nothing can fail.
  printf '%s\n' 'name: lane' 'jobs:' '  locks:' '    name: not a step' '    steps:' \
                '      - name: first' '      - run: unnamed' '      - name: second' > "$tmp/steps.yml"
  got="$(steps_of "$tmp/steps.yml" | cut -f2 | tr '\n' '|')"
  check steps-named-only 'first|second|' "$got"

  # UNKNOWN is not a value the reader may invent. With --offline every
  # server-side answer must be UNKNOWN and none of them may become `none`.
  got="$(ONLINE=0; ask "repos/x/y/branches/main/protection" '.' none)"
  check offline-is-unknown 'UNKNOWN:not-asked' "$got"

  # A 404 on branch protection is the one API failure that IS evidence: the
  # endpoint answers 404 precisely when the branch is unprotected. Every other
  # failure is unreachability and stays UNKNOWN.
  gh() { echo '{"message":"Branch not protected","status":"404"}'; return 1; }
  got="$(ask "repos/x/y/branches/main/protection" '.' none)"
  check protection-404-verified 'VERIFIED:none' "$got"

  gh() { echo 'error connecting to api.github.com'; return 1; }
  got="$(ask "repos/x/y/branches/main/protection" '.' none)"
  check unreachable-is-unknown 'UNKNOWN:api-unreachable' "$got"
  unset -f gh
  # RESTORED, NOT UNSET: `tracked` is a real function of this tool, and unsetting
  # it took the tool apart rather than the stub, which the two cases below then
  # reported as `command not found`.
  tracked() { git ls-files --error-unmatch "$1" >/dev/null 2>&1; }

  # The census must add up over a fixture whose answer is known by construction:
  # two lanes, three steps, plus the three server-side rows.
  mkdir -p "$tmp/wf"
  printf '%s\n' 'name: a' 'on:' '  pull_request:' 'jobs:' '  j:' '    steps:' \
                '      - name: one' '      - name: two' > "$tmp/wf/a.yml"
  printf '%s\n' 'name: b' 'on:' '  push:' 'jobs:' '  j:' '    steps:' \
                '      - name: three' > "$tmp/wf/b.yml"
  got="$(WORKFLOWS="$tmp/wf" ONLINE=0 MODE=count; WORKFLOWS="$tmp/wf" ONLINE=0 MODE=count report 2>&1 | tail -1)"
  check census-adds-up \
    'PROVENANCE VERDICT COUNTED rules=6 self_supplied=1 required=0 unknown=5' "$got"


  # `tracked` decides who can edit a rule's source, so it gets its own cases
  # rather than being taken on faith by the two above, which stub it.
  if tracked tools/counters.sh; then got=yes; else got=no; fi
  check tracked-sees-a-tracked-file 'yes' "$got"
  if tracked tools/no-such-tool.sh; then got=yes; else got=no; fi
  check tracked-refuses-an-absent-file 'no' "$got"

  # THE ONLINE COUNTING PATH NEEDS A CASE, and it did not have one until a
  # mutation walked past the whole suite. Every census case above runs
  # `--offline`, so `self_supplied` and `required` were incremented by a branch
  # no fixture reached: replacing that branch with `if false` left eleven cases
  # green. That is #1092's rule — a bound with one reader needs a case that
  # exercises its refusal — and #1556's, one population over. The stub answers
  # as the API does, so the arithmetic below is the arithmetic the real run
  # performs.
  gh() { echo '{"message":"Branch not protected","status":"404"}'; return 1; }
  tracked() { case "$1" in *CODEOWNERS*) return 1 ;; *) return 0 ;; esac; }   # the fixture lanes are "tracked"; CODEOWNERS stays absent
  got="$(WORKFLOWS="$tmp/wf" MODE=count; WORKFLOWS="$tmp/wf" ONLINE=1 MODE=count report 2>&1 | tail -1)"
  check online-unprotected-self-supplied \
    'PROVENANCE VERDICT COUNTED rules=6 self_supplied=6 required=0 unknown=0' "$got"

  # And the other side of the same branch: when something server-side DOES
  # require the result, those rules are not self-supplied and the count has to
  # move. Without this case the tool could report every repository on earth as
  # self-supplied and the suite would agree.
  gh() { echo '{"required_status_checks":{"contexts":["locks"]}}'; return 0; }
  got="$(WORKFLOWS="$tmp/wf" MODE=count; WORKFLOWS="$tmp/wf" ONLINE=1 MODE=count report 2>&1 | tail -1)"
  check online-required-not-self \
    'PROVENANCE VERDICT COUNTED rules=6 self_supplied=1 required=5 unknown=0' "$got"
  unset -f gh
  # RESTORED, NOT UNSET: `tracked` is a real function of this tool, and unsetting
  # it took the tool apart rather than the stub, which the two cases below then
  # reported as `command not found`.
  tracked() { git ls-files --error-unmatch "$1" >/dev/null 2>&1; }

  # An untracked lane file, online, with `tracked` NOT stubbed — so the fixture
  # under /tmp is what it really is. Without this case the untracked arm was
  # unfalsifiable: both cases above stub `tracked` true, and every offline case
  # is already UNKNOWN for a different reason, so deleting the arm left fifteen
  # cases green while three rules fell through every counter and the identity
  # line was the only thing that would ever have noticed.
  gh() { echo '{"message":"Branch not protected","status":"404"}'; return 1; }
  got="$(WORKFLOWS="$tmp/wf" MODE=count; WORKFLOWS="$tmp/wf" ONLINE=1 MODE=count report 2>&1 | tail -1)"
  check untracked-lane-is-unknown \
    'PROVENANCE VERDICT COUNTED rules=6 self_supplied=3 required=0 unknown=3' "$got"
  unset -f gh
  # Nothing read is the finding, not a clean result over an empty set (#1207).
  mkdir -p "$tmp/empty"
  got="$(WORKFLOWS="$tmp/empty" ONLINE=0 MODE=count; WORKFLOWS="$tmp/empty" ONLINE=0 MODE=count report 2>&1 | tail -1)"
  check empty-is-a-finding 'PROVENANCE VERDICT NOTHING_READ workflows='"$tmp/empty" "$got"

  echo "PROVENANCE SELFTEST VERDICT $([ "$fail" -eq 0 ] && echo PASS || echo FAIL) cases=$((pass + fail)) failed=$fail"
  [ "$fail" -eq 0 ]
}

if [ "$MODE" = selftest ]; then
  selftest
  exit $?
fi

report
exit $?
