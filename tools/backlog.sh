#!/usr/bin/env bash
# tools/backlog.sh — which of the open backlog was measured, and which was recognised (#1246)
#
# Usage: tools/backlog.sh              count the open backlog by evidence, every kind with an evidence field
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
SINCE=""
case "${1:-}" in
  '') ;;
  --list) MODE=list ;;
  --selftest) MODE=selftest ;;
  --flow)
    MODE=flow
    SINCE="${2:-}"
    [ -n "$SINCE" ] || { echo "FATAL --flow wants a date: tools/backlog.sh --flow 2026-08-16" >&2; exit 2; }
    case "$SINCE" in
      [0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]) ;;
      *) echo "FATAL not a date: $SINCE (want YYYY-MM-DD)" >&2; exit 2 ;;
    esac ;;
  *) echo "FATAL unknown argument: $1 (this tool takes --list, --flow DATE, --selftest, or nothing)" >&2; exit 2 ;;
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

# Did the answer fill the page? Split out so the suite can drive it over a
# synthetic count instead of over the API (#1273) — faking `gh` would be a
# bigger apparatus than the branch is worth, and a fixture that fakes the
# transport tests the fake.
truncated() {                     # truncated <open> <limit>
  [ "$1" -ge "$2" ]
}

# THE MARKER THE QUERY ABOVE DEPENDS ON (#1251). Every issue template must carry
# an evidence field whose label opens with the shared word, so one query reaches
# every kind. The local phrasing rides BEHIND it — `Measured — what the class
# does today` — because a crown's evidence is not a measurement of a defect and
# a prompt that pretends otherwise invites `n/a`. This is `vary`'s shape and
# `# litany: unguarded <reason>`'s: one token for the machine, free prose for
# the person.
#
# JUDGED, in a tool whose issue counts are only reported. #1246's argument is
# that an issue is not an artefact CI can judge — nobody can be made to write
# prose in a field, and a lint demanding it is how a required field becomes
# `n/a`. A TEMPLATE is an artefact: it is a file in this tree, a fourth one is
# added by a pull request, and the cost of the rule is one word in a label.
# ONE ENUMERATION, READ TWICE (#1351). This walked `*.yml` and `*.yaml` while
# the census beside it counted `*.yml` with `ls`. Today all three templates are
# `.yml`, so the two agreed by accident; the day somebody writes `feature.yaml`
# the check judges four and the census says three, and a reader comparing them
# concludes the fourth was skipped — the opposite of what happened.
#
# The census rule (#1221) says what may not ride a verdict. It says nothing
# about whether a census and its verdict describe the SAME POPULATION, and this
# tree found three pairs in one day that were computed independently and
# happened to agree: `CODES_CENSUS with_returns=` beside `tools=`,
# `classes_timed=` beside `probes_on_disk=`, and this one — two globs in
# adjacent lines of one function.
#
# The repair generalises even where the fix does not: derive the census from the
# list the check walks, never from a second command.
#
# It sets two GLOBALS rather than printing, and that is forced rather than
# chosen: `x="$(f)"` runs `f` in a subshell, so a count assigned inside it dies
# with the subshell and the caller reads the initial 0. A function that must
# return two answers in bash either writes globals or writes a file, and the
# whole point here is that the two answers come from one walk.
templates_seen=0
templates_unmarked=''
read_templates() {                # read_templates <dir> — sets $templates_seen and $templates_unmarked
  local dir="${1:-.github/ISSUE_TEMPLATE}" f
  templates_seen=0
  templates_unmarked=''
  [ -d "$dir" ] || return 0
  for f in "$dir"/*.yml "$dir"/*.yaml; do
    [ -f "$f" ] || continue
    templates_seen=$((templates_seen + 1))
    grep -qE '^ *label: *Measured( |$)' "$f" \
      || templates_unmarked="$templates_unmarked $(basename "$f")"
  done
  templates_unmarked="${templates_unmarked# }"
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

  # The paging half (#1273). The defect #1246 was filed about lives in the
  # request, and the six cases above are all on the classifier — so a limit
  # edited from 1000 to 100 left every one of them green while the tool began
  # reporting a page as a backlog. These drive the predicate the request uses.
  page_() {                       # page_ <name> <open> <limit> <want 0=truncated 1=not>
    local name="$1" open="$2" limit="$3" want="$4" got
    if truncated "$open" "$limit"; then got=0; else got=1; fi
    if [ "$want" = "$got" ]; then
      pass=$((pass + 1)); printf 'BACKLOG case=%-24s want=%s got=%s OK\n' "$name" "$want" "$got"
    else
      fail=$((fail + 1)); printf 'BACKLOG case=%-24s want=%s got=%s BROKEN\n' "$name" "$want" "$got"
    fi
  }

  page_ under-the-page     508 1000 1
  page_ exactly-the-page  1000 1000 0
  page_ over-the-page     1001 1000 0
  page_ the-old-default     30   30 0   # #1246's own measurement, as it was taken

  # The flow mode's door (#1323). Its counts need the API; its refusals do not,
  # and the refusals are the part a typo meets — `--flow yesterday` must be
  # told, not silently searched for.
  date_() {                       # date_ <name> <want-code> <args...>
    local name="$1" want="$2"; shift 2
    local got=0
    bash "$0" "$@" >/dev/null 2>&1 || got=$?
    if [ "$want" = "$got" ]; then
      pass=$((pass + 1)); printf 'BACKLOG case=%-24s want=%s got=%s OK\n' "$name" "$want" "$got"
    else
      fail=$((fail + 1)); printf 'BACKLOG case=%-24s want=%s got=%s BROKEN\n' "$name" "$want" "$got"
    fi
  }
  date_ flow-needs-a-date    2 --flow
  date_ flow-refuses-prose   2 --flow yesterday
  date_ flow-refuses-partial 2 --flow 2026-08

  # The template marker (#1251). Driven over a scratch directory rather than
  # over `.github/ISSUE_TEMPLATE`, because the live one is green by the time
  # this lands and a check with no failing case is a check nobody can break on
  # purpose.
  local tmp; tmp="$(mktemp -d)"
  trap 'rm -rf "$tmp"' RETURN
  tpl_() {                        # tpl_ <name> <want-count> <label-line…>
    local name="$1" want="$2" got; shift 2
    local dir="$tmp/tpl"; rm -rf "$dir"; mkdir -p "$dir"
    local i=0
    for line in "$@"; do
      i=$((i + 1))
      printf 'name: fixture %s\nbody:\n  - type: textarea\n    attributes:\n%s\n' "$i" "$line" \
        > "$dir/t$i.yml"
    done
    read_templates "$dir"
    got="$(printf '%s' "$templates_unmarked" | wc -w | tr -d ' ')"
    if [ "$want" = "$got" ]; then
      pass=$((pass + 1)); printf 'BACKLOG case=%-24s want=%s got=%s OK\n' "$name" "$want" "$got"
    else
      fail=$((fail + 1)); printf 'BACKLOG case=%-24s want=%s got=%s BROKEN\n' "$name" "$want" "$got"
    fi
  }
  # The census and the check read the same walk (#1351). A case per direction:
  # the count must move with the number of FILES, not with the number that
  # happen to be marked, and it must not move with the extension.
  seen_() {                       # seen_ <name> <want-seen> <file-spec…>
    local name="$1" want="$2" got; shift 2
    local dir="$tmp/seen"; rm -rf "$dir"; mkdir -p "$dir"
    local spec
    for spec in "$@"; do
      printf 'name: fixture\nbody:\n  - type: textarea\n    attributes:\n      label: Measured\n' \
        > "$dir/$spec"
    done
    read_templates "$dir"
    got="$templates_seen"
    if [ "$want" = "$got" ]; then
      pass=$((pass + 1)); printf 'BACKLOG case=%-24s want=%s got=%s OK\n' "$name" "$want" "$got"
    else
      fail=$((fail + 1)); printf 'BACKLOG case=%-24s want=%s got=%s BROKEN\n' "$name" "$want" "$got"
    fi
  }
  tpl_ tpl-bare-marker       0 '      label: Measured'
  tpl_ tpl-marker-and-prose  0 '      label: Measured — what the class does today'
  tpl_ tpl-local-name-only   1 '      label: The reading behind the concern'
  # The word must OPEN the label. A field called "How this was measured" is a
  # different question and would satisfy a substring read while leaving the
  # query's anchor unwritten.
  tpl_ tpl-marker-not-first  1 '      label: How this was measured'
  tpl_ tpl-one-of-two        1 '      label: Measured' '      label: Something else'
  # An empty directory is zero unmarked, not an error: a tree with no templates
  # has nothing to be wrong about, and reporting one would be a checker
  # inventing a defect out of an absence (#1235's shape).
  tpl_ tpl-no-templates      0
  # The defect this unit is about: the census counted `*.yml` with a second
  # command while the check walked `*.yml` AND `*.yaml`. Today all three
  # templates are `.yml`, so the two agreed by accident and nothing would have
  # said otherwise until somebody wrote the other extension.
  seen_ seen-counts-yml      2 a.yml b.yml
  seen_ seen-counts-yaml     2 a.yaml b.yaml
  seen_ seen-counts-both     3 a.yml b.yaml c.yml
  seen_ seen-empty-dir       0

  echo "BACKLOG SELFTEST VERDICT $([ "$fail" -eq 0 ] && echo PASS || echo FAIL) cases=$((pass + fail)) failed=$fail"
  [ "$fail" -eq 0 ]
}

# FLOW, because the standing population says nothing about the day (#1323).
#
# A day that closed thirty-three and opened thirty-three prints the same
# `open=` as a day that closed none. The ratio is what says whether a day was
# spent draining the backlog or exploring the tree, and both are correct at
# different times — a survey unit SHOULD open more than it closes.
#
# There is no right ratio, only a visible one. Reported, never judged, for
# backlog.sh's existing reason (#1246) and for that second one.
if [ "$MODE" = flow ]; then
  if ! opened="$(gh issue list --repo "$REPO" --state all --limit 1000 \
                 --search "created:>=$SINCE" --json number --jq 'length' 2>&1)"; then
    echo "FATAL could not read the day's opened issues: $opened" >&2
    exit 3
  fi
  if ! closed="$(gh issue list --repo "$REPO" --state closed --limit 1000 \
                 --search "closed:>=$SINCE" --json number --jq 'length' 2>&1)"; then
    echo "FATAL could not read the day's closed issues: $closed" >&2
    exit 3
  fi
  echo "BACKLOG FLOW opened=$opened closed=$closed net=$((opened - closed)) since=$SINCE"
  # Both ends of the paging axis, same as the count mode (#1273): a day that
  # filled the page is a page and not a day.
  if truncated "$opened" 1000 || truncated "$closed" 1000; then
    echo "BACKLOG VERDICT TRUNCATED — a day's flow filled the page, so it is a page and not a day" >&2
    exit 5
  fi
  echo "BACKLOG VERDICT COUNTED"
  exit 0
fi

if [ "$MODE" = selftest ]; then
  selftest
  exit $?
fi

# --limit 1000 rather than the default 30, and the number is the whole point of
# this tool's existence. A count taken at the default is a count of the first
# page, and nothing in the output says so.
read_templates
if [ -n "$templates_unmarked" ]; then
  # shellcheck disable=SC2086
  printf 'TEMPLATE_UNMARKED %s has no evidence field labelled `Measured …`\n' $templates_unmarked
  echo "FATAL a template's evidence field is unreachable by the query below — one word in the label is the whole fix (#1251)" >&2
  exit 1
fi
echo "TEMPLATES_MARKED count=$templates_seen  (the same walk the check used, not a second command — #1351)"

LIMIT=1000
# EVERY KIND THAT HAS AN EVIDENCE FIELD, not just build units (#1251). Three
# templates grew one in two hours and each named it for its own kind of issue —
# `Measured`, `What the class does today`, `The reading behind the concern` — so
# a query for one of them returned "all measured" for the other two by searching
# for a word those templates do not use. The prompts still differ, because a
# crown's evidence is not a measurement of a defect; the SHARED MARKER is what
# the query needs, and the local sentence rides behind it.
#
# `label:a,b,c` is search's OR and `--label a --label b` is gh's AND, which is
# the trap: the AND form returns the issues carrying all three, which is
# usually none, and a count of none reads exactly like a clean backlog. The
# search form also deduplicates — 499 + 4 + 15 labels return 515 issues, so
# three carry two — and three tools counted twice would be a defect this tool
# exists to name in other people's numbers.
KINDS='build-unit,class-design,decision'
if ! rows="$(gh issue list --repo "$REPO" --state open --search "label:$KINDS" \
             --limit "$LIMIT" --json number,title,body,labels 2>&1)"; then
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

# The kinds, so `unmeasured=` can be read. A ratio over one population is a
# number; a ratio over three whose sizes are invisible is an average of things
# nobody can name. Census and never a verdict (#1221): a kind's size moves
# whenever somebody files.
kinds=''
for kind in $(printf '%s' "$KINDS" | tr ',' ' '); do
  kinds="$kinds $kind=$(printf '%s' "$rows" \
        | jq --arg k "$kind" '[.[] | select(any(.labels[]?; .name == $k))] | length')"
done
echo "BACKLOG_KINDS$kinds  (a search OR, deduplicated: an issue wearing two labels is one row)"

measured=$((open - unmeasured))
echo "BACKLOG repo=$REPO open=$open measured=$measured unmeasured=$unmeasured limit=$LIMIT"

# Reported, never judged. #1246 argues the lane version should not be built —
# an issue is not an artefact CI can judge — and a tool that exits nonzero on a
# backlog it cannot fix is a red light nobody can turn off, which is how a gate
# gets routed around (the #972 lesson, from the other end).
if [ "$open" -eq 0 ]; then
  echo "BACKLOG VERDICT NOTHING_READ — an empty answer is not an empty backlog" >&2
  exit 4
fi

# The other end of the same axis (#1273). NOTHING_READ catches an empty page;
# this catches a FULL one.
#
# #1246's whole finding was a count taken at an undeclared paging default, and
# the tool written to not repeat that had the same defect one layer down: the
# limit is a literal in a request the suite never makes, so editing it to 100
# leaves every case green while the tool starts reporting a page as a backlog.
# Printing `limit=` was the mitigation, and it only works on a reader who is
# reading.
#
# A count that exactly equals the limit is almost certainly truncated, and the
# tool CANNOT TELL a full page from a complete answer — which is the honest
# statement and the reason this is a refusal rather than a footnote. A backlog
# of exactly $LIMIT is possible; raising the limit is how you find out, and a
# tool that guessed would be doing the thing this whole unit is about.
if truncated "$open" "$LIMIT"; then
  echo "BACKLOG VERDICT TRUNCATED open=$open limit=$LIMIT — the answer filled the page, so it is a page and not a backlog" >&2
  exit 5
fi
echo "BACKLOG VERDICT COUNTED"
