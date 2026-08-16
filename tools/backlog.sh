#!/usr/bin/env bash
# tools/backlog.sh — which of the open backlog was measured, and which was recognised (#1246)
#
# Usage: tools/backlog.sh              count the open build-units by evidence
#        tools/backlog.sh --list       print one row per unmeasured issue
#        tools/backlog.sh --selftest   run the classifier's cases; no token, no network
#
# THE FINDING THIS EXISTS FOR. A day's record, from #1246:
#
#   filed from a run          filed from a shape
#   ----------------          ------------------
#   #1203  eleven red runs    #1215  closed as already-checked
#   #1206  39 procs in 8 s    #1212  its central table was WRONG
#   #1214  eight probes named #1216  timing claim withdrawn
#   #1233  gh returning null
#
# Three of the second column needed a correction, a withdrawal or a close. None
# of the first did. A reader picking work up cannot tell the columns apart, and
# the cost of guessing wrong is a unit spent on a shape somebody recognised.
#
# WHY THIS TOOL EXISTS RATHER THAN A LANE CHECK. An issue is not an artefact CI
# can judge, and a lint demanding prose in a field is how a required field
# becomes `n/a` (#1246 says so and this tool agrees). What is decidable is
# whether the word is there at all, which sorts the backlog by evidence rather
# than by age. The reader still does the judging.
#
# THE DEFECT THIS TOOL FOUND ON ITS FIRST RUN, IN THE ISSUE THAT ASKED FOR IT.
# #1246 opens with a measurement:
#
#   gh issue list --label build-unit --state open --json number -q 'length'
#   30
#
# `gh issue list` defaults to **30 rows**. The real figure is 508, and the
# unmeasured count it reports as 20 is 424 — an issue about the danger of
# unmeasured claims, whose own headline number was off by seventeen times,
# because the measurement was taken with a paging default nobody named. This
# tool pages explicitly and prints the denominator for that reason.

set -uo pipefail

cd "$(dirname "$0")/.."

REPO="${MATRIX_REPO:-gokselozgur5/matrix-sim}"
MODE=count
case "${1:-}" in
  '') ;;
  --list) MODE=list ;;
  --selftest) MODE=selftest ;;
  *) echo "FATAL unknown argument: $1 (this tool takes --list, --selftest, or nothing)" >&2; exit 2 ;;
esac

# Does a body carry a measurement? The word, case-insensitive, anywhere in it.
#
# Deliberately generous. A stricter reading — the template's `### Measured`
# heading — would report every issue filed before #1224 as unmeasured even when
# it quotes a transcript, which is most of the honest ones. The looser rule
# over-counts the measured side, so `unmeasured=` is a FLOOR on the problem and
# never an exaggeration of it. A tool that argues its own case by rounding up
# is worth less than no tool.
measured_body() {                 # measured_body <body>
  case "$1" in
    *[Mm]easured*) return 0 ;;
    *) return 1 ;;
  esac
}

selftest() {
  pass=0
  fail=0
  case_() {                       # case_ <name> <want 0=measured 1=not> <body>
    local name="$1" want="$2" body="$3" got
    if measured_body "$body"; then got=0; else got=1; fi
    if [ "$want" = "$got" ]; then
      pass=$((pass + 1)); printf 'BACKLOG case=%-24s want=%s got=%s OK\n' "$name" "$want" "$got"
    else
      fail=$((fail + 1)); printf 'BACKLOG case=%-24s want=%s got=%s BROKEN\n' "$name" "$want" "$got"
    fi
  }

  case_ template-heading     0 '### Measured

```
BENCH judged=52
```'
  case_ lowercase-prose      0 'the run was measured at 39 processes in 8 seconds'
  case_ capitalised-midline  0 'Nothing here is Measured yet, which is the point'
  case_ shape-only           1 'This looks like the same defect as #1203, one directory over.'
  case_ empty-body           1 ''
  case_ near-miss-word       1 'the measure of a lock is whether it can fail'

  echo "BACKLOG SELFTEST VERDICT $([ "$fail" -eq 0 ] && echo PASS || echo FAIL) cases=$((pass + fail)) failed=$fail"
  [ "$fail" -eq 0 ]
}

if [ "$MODE" = selftest ]; then
  selftest
  exit $?
fi

# --limit 1000 rather than the default 30, and the number is the whole point of
# this tool's existence. A count taken at the default is a count of the first
# page, and nothing in the output says so.
if ! rows="$(gh issue list --repo "$REPO" --label build-unit --state open \
             --limit 1000 --json number,title,body 2>&1)"; then
  echo "FATAL could not read the backlog: $rows" >&2
  exit 3
fi

open=0
unmeasured=0
while IFS= read -r line; do
  [ -z "$line" ] && continue
  open=$((open + 1))
  num="${line%%$'\t'*}"
  rest="${line#*$'\t'}"
  title="${rest%%$'\t'*}"
  body="${rest#*$'\t'}"
  if ! measured_body "$body"; then
    unmeasured=$((unmeasured + 1))
    [ "$MODE" = list ] && printf 'UNMEASURED #%s %s\n' "$num" "$(printf '%s' "$title" | cut -c1-80)"
  fi
done <<< "$(printf '%s' "$rows" | jq -r '.[] | [.number, .title, (.body // "" | gsub("[\n\t]"; " "))] | @tsv')"

measured=$((open - unmeasured))
echo "BACKLOG repo=$REPO open=$open measured=$measured unmeasured=$unmeasured limit=1000"

# Reported, never judged. #1246 argues the lane version should not be built —
# an issue is not an artefact CI can judge — and a tool that exits nonzero on a
# backlog it cannot fix is a red light nobody can turn off, which is how a gate
# gets routed around (the #972 lesson, from the other end).
if [ "$open" -eq 0 ]; then
  echo "BACKLOG VERDICT NOTHING_READ — an empty answer is not an empty backlog" >&2
  exit 4
fi
echo "BACKLOG VERDICT COUNTED"
