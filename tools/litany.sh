#!/usr/bin/env bash
# tools/litany.sh — who judges the file that judges every pull request? (#1114)
#
# Usage: tools/litany.sh [PATH]        judge .github/workflows/locks.yml
#        tools/litany.sh --selftest    run every question against built fixtures
#
# THE FINDING THIS EXISTS FOR. In one night `locks.yml` gained floors, lost
# three swallowing captures, gained two locks, widened one and gained two
# tools — nine units, one file. Every one of those was verified by running the
# thing the lock guards. None was verified by running the LITANY, because
# nothing does: the file that judges every pull request was the one artifact in
# the repository with no judge. It cost twenty minutes twice, when an unresolved
# conflict marker made the YAML unparseable, the run failed in zero seconds with
# no jobs, and the UI said "no checks reported" — indistinguishable from a pull
# request whose CI had not started (#1109).
#
# WHERE IT RUNS, AND WHY NOT HERE. A judge that lives inside the litany cannot
# judge a litany that will not parse: the broken file is exactly the file that
# would have run the check. So this script is invoked by its OWN workflow
# (.github/workflows/litany.yml), which is small, changes almost never, and does
# not import anything from the file it reads. That is the whole reason for a
# second workflow in a repository that keeps exactly one.
#
# SIX QUESTIONS, in the order they can be answered without running a job:
#
#   1. DOES IT PARSE. Not a YAML library — this repository builds with javac and
#      nothing else (D-009), and adding a parser to answer one question would be
#      the dependency the whole tree is written to avoid. What is checked is the
#      class of breakage that has actually happened here and the class YAML
#      cannot survive: a conflict marker, a literal tab, a step with neither
#      `run:` nor `uses:`, and the three keys without which the file is not a
#      workflow at all. A real parser would be better and is not free; this
#      catches the two twenty-minute incidents and says plainly what it is.
#
#   2. DOES EVERY VERDICT GREP MATCH SOMETHING THE TREE PRINTS. A step that
#      greps for a string its tool stopped printing goes green forever — #972
#      found that in release.sh, #1040 in the selftest gate. Every literal
#      pattern in the file is searched for in the sources that could print it.
#      A pattern nobody prints is either a lock that has stopped locking or a
#      typo that has always been one.
#
#   3. DO THE LOCK NUMBERS STILL MAP TO STEPS. The tree's prose cites "lock 0"
#      through "lock 12" by number. Nothing checked that the numbers are
#      contiguous or that each is claimed by a step's comment.
#
#   4. ARE TWO STEPS NAMED THE SAME. Adding rather than editing is how this file
#      grows, and a duplicate name is silent in the Actions UI: two rows with one
#      label, and no way to tell which one failed.
#
#   5. DOES EVERY `run:` BLOCK PARSE AS THE SHELL THAT WILL RUN IT (--shellcheck).
#      `bash -n`, the runner's own parser. This file shipped with an unclosed
#      quote and could not be parsed for eleven runs (#1203).
#
#   6. AND WHAT WILL IT RUN UNDER (--shellcheck). A block with no `shell:` gets
#      GitHub's default `bash -e {0}` — -e without -u or pipefail — while its
#      neighbours set all three. Twenty-one of twenty-two blocks were uniform and
#      the odd one out was lock 0, the only lock that judges a claim (#1226). A
#      block may decline with `# litany: unguarded <reason>` on its first line.
#
# Exit 0 when every question is answered clean, 1 otherwise. The verdict line is
# the contract, the way a probe's is.

set -uo pipefail

FILE="${1:-.github/workflows/locks.yml}"
SELFTEST=no
SHELLCHECK=no
FLOORCHECK=no

# One directory per invocation, holding one file per producer command. See
# `run_producing` for why it is a directory and not an array.
PRODUCER_CACHE="$(mktemp -d)"
trap 'rm -rf "${PRODUCER_CACHE:-}"' EXIT
[ "${1:-}" = "--selftest" ] && { SELFTEST=yes; FILE=.github/workflows/locks.yml; }
[ "${1:-}" = "--shellcheck" ] && { SHELLCHECK=yes; FILE=.github/workflows/locks.yml; }
[ "${1:-}" = "--floorcheck" ] && { FLOORCHECK=yes; FILE=.github/workflows/locks.yml; }

# ---------------------------------------------------------------- question 1

parse_breaks() {                # parse_breaks <file> — one line per breakage
  local f="$1"
  grep -nE '^(<{7}|={7}|>{7})' "$f" | sed 's/^/PARSE conflict-marker line=/'
  grep -nP '\t' "$f" 2>/dev/null | sed 's/^/PARSE literal-tab line=/' \
    || grep -n "$(printf '\t')" "$f" | sed 's/^/PARSE literal-tab line=/'
  for key in '^name:' '^on:' '^jobs:'; do
    grep -qE "$key" "$f" || echo "PARSE missing-key ${key//^/}"
  done
  # A step is `- name:`; the next `- name:` or end of file bounds it. Each must
  # carry one of the two things a step can be.
  awk '
    /^      - name:/ {
      if (open && !acted) { print "PARSE step-does-nothing line=" start }
      open = 1; acted = 0; start = NR; next
    }
    /^        (run|uses):/ { acted = 1 }
    END { if (open && !acted) { print "PARSE step-does-nothing line=" start } }
  ' "$f"
}

# ---------------------------------------------------------------- question 2
#
# The literal patterns, extracted from the two grep forms this file uses:
# `grep -q<flags> 'PATTERN'` and `grep -q<flags> "PATTERN"`. A pattern is
# reduced to its longest run of literal characters — regex metacharacters mark
# where the literal stops — because that prefix is the part a tool must print
# verbatim, and it is what a stale grep gets wrong.
literal_of() {                  # literal_of <pattern> — its leading literal run
  printf '%s' "$1" \
    | sed -E 's/^\^//' \
    | sed 's/[][\\$.*+?(){}|].*$//' \
    | sed -E 's/[[:space:]]+$//'
}

# Which patterns are VERDICT greps at all. Two of this file's greps read a Java
# source rather than a run's output — `static void main(String[] args)` and
# `matrix.Streams.utf8();` are the charset lock scanning probes for a missing
# pin — and asking "does anything PRINT this" of them is the wrong question with
# a confident wrong answer. The tree's instrument grammar (D-020) opens every
# verdict line with an all-caps token, so that is the discriminator: it is the
# repository's own convention rather than a heuristic invented here.
verdict_shaped() {              # verdict_shaped <literal>
  printf '%s' "$1" | grep -qE '^[A-Z][A-Z0-9_]{2,}([ =]|$)'
}

# The literal's longest ADJACENT prefix must appear in the sources that could
# print it.
#
# The first version of this asked each WORD separately, and #1145 named what
# that misses: `RELEASE CHECK` passes if `RELEASE` and `CHECK` each appear
# anywhere at all — not adjacent, not in one file, not in a printf. A tool
# drifting from `RELEASE CHECK 12/12` to `RELEASE GATE 12/12` kept its lane
# green, because `CHECK` survives in a hundred other places. That is #972's
# defect exactly, and the check written to catch it could not.
#
# Adjacency works because a verdict is a printf FORMAT STRING, and the format
# string is in the source verbatim up to its first substitution:
#
#     printf 'DATECHECK VERDICT PASS dialect=%s cases=%d\n' ...
#
# So `DATECHECK VERDICT PASS` is findable as one run of characters, and only
# `dialect=gnu`'s VALUE is not — which is why the prefix shortens from the right
# until it matches, rather than being demanded whole. What is reported is how
# much of it matched, so a claim that shrank to one weak word is visible instead
# of passing as a match.
# Where a verdict can come FROM: a thing that runs. Two exclusions, both learned the
# expensive way while writing this.
#
# Documents are not sources. `RELEASE CHECK 12/12 locks green` appears in tools/README.md,
# describing the lock — so searching all of tools/ found the sentence ABOUT the verdict and
# called the verdict printed. A prose description of a lock is exactly what survives when
# the lock stops working.
#
# And this file is not a source. Its own comments quote both the live verdict and the
# renamed one it exists to refuse, so an unfiltered search finds its own explanation and
# passes. That is the second time the same shape has bitten in this script — the selfcheck's
# ghost verdict had to be assembled from fragments for the identical reason — and it is
# worth the sentence: a checker inside its own search path certifies itself.
prints() {                      # prints <literal>
  grep -rqF --include='*.java' --include='*.sh' --exclude='litany.sh' \
    -- "$1" tools/ probes/ src/ 2>/dev/null
}

# The commands that can be RUN to produce a verdict, keyed by the token that opens it.
#
# Every one is no-token, no-network and finishes in seconds — that is the entry rule, not
# a coincidence. `BENCH` is absent because the sweep costs two minutes and builds the
# daemon; `RELEASE` is absent because `release.sh --check` runs that sweep; `GOLDEN` and
# `EXIT` need the teleprinter compiled. Those keep the prefix rule and are counted.
#
# The map is a case rather than an array so this stays POSIX-shaped and greppable.
producer_of() {                 # producer_of <first-token> -> a command, or empty
  case "$1" in
    BASELINE)   printf 'bash tools/baseline.sh --selftest' ;;
    # DATECHECK is deliberately absent, and finding out why is worth the four lines.
    # `locks.yml` greps `dialect=gnu`, and this box prints `dialect=bsd` — correctly:
    # the lane's whole point is that the runner proves the GNU half while the operator
    # proves the BSD one (#901). So running the producer HERE and demanding the lane's
    # pattern would report a defect that is the check working. A verdict whose text
    # depends on the platform cannot be judged by running it somewhere else, and the
    # prefix rule — which reads the format string, not the value — is right for it.
    RULERCHECK) printf 'bash tools/balance.sh --rulercheck' ;;
    JUDGECHECK) printf 'bash tools/balance.sh --judgecheck' ;;
    ADVICE)     printf 'bash tools/advice.sh' ;;
    DIGEST)     printf 'bash tools/digest-move.sh --selftest' ;;
    ATTRIBUTION) printf 'bash tools/attribution.sh --selftest' ;;
    *) printf '' ;;
  esac
}

# Runs the producer for a literal, or fails when there is none. Output on stdout; the
# caller decides whether the pattern is in it.
run_producing() {               # run_producing <literal>
  local cmd
  # Already inside another tool's run: do not run producers. `advice.sh` runs
  # every tool's --selftest, this selftest runs `advice.sh` as a producer, and
  # the pair recursed without end — thirty-nine processes in eight seconds
  # (#1206). Declining here is not a weakening: the caller falls through to the
  # prefix rule, which reads the format string instead of the value and is
  # already the right answer for a verdict that cannot be produced where it is
  # being read (see DATECHECK in `producer_of`).
  [ "${MATRIX_TOOL_DEPTH:-0}" -eq 0 ] || return 1
  cmd="$(producer_of "$(printf '%s' "$1" | awk '{print $1}')")"
  [ -n "$cmd" ] || return 1

  # Each producer runs once per invocation, not once per pattern. `judge` is
  # called ~30 times by the selftest and every call re-ran all six producers;
  # `advice.sh` alone takes 9 s, so the suite could not finish inside the five
  # minutes `litany.yml` allows it — which nobody knew, because that workflow
  # has never parsed (#1203) and its selftest step has therefore never run.
  #
  # A file, not an associative array: `declare -A` is bash 4, and the operator's
  # machine ships bash 3.2. A producer's output is a fact about a tree that does
  # not change while this script runs, so caching it changes no verdict.
  local slot="$PRODUCER_CACHE/$(printf '%s' "$cmd" | tr -c 'a-zA-Z0-9' '-')"
  if [ ! -f "$slot" ]; then
    { eval "$cmd" > "$slot" 2>&1; } || true
  fi
  cat "$slot"
}

printed_prefix() {              # printed_prefix <literal> -> echoes the matched prefix, or empty
  local words rest prefix
  # Split on space AND `=`, because a field's name is printed and its value is not.
  words="$(printf '%s' "$1" | tr '=' ' ')"
  rest="$words"
  while [ -n "$rest" ]; do
    prefix="$rest"
    if [ ${#prefix} -ge 8 ] && prints "$prefix"; then
      printf '%s' "$prefix"
      return 0
    fi
    # Drop the last word and try again: the tail is where the runtime values live.
    case "$rest" in
      *\ *) rest="${rest% *}" ;;
      *) rest="" ;;
    esac
  done
  return 1
}

grep_patterns() {               # grep_patterns <file>
  grep -oE "grep -q[a-zA-Z]* '[^']+'" "$1" | sed -E "s/^grep -q[a-zA-Z]* '//; s/'$//"
  grep -oE "grep -q[a-zA-Z]* \"[^\"\$]+\"" "$1" | sed -E 's/^grep -q[a-zA-Z]* "//; s/"$//'
}

# Where a verdict can legitimately come from: the tools, the probes, the daemon.
printed_somewhere() {           # printed_somewhere <literal>
  local lit="$1"
  [ ${#lit} -lt 6 ] && return 0          # too short to be a verdict; not judged
  grep -rqF -- "$lit" tools/ probes/ src/ 2>/dev/null
}

# ---------------------------------------------------------------- the run

BREAKS=()
note() { BREAKS+=("$1"); echo "$1"; }

judge() {                       # judge <file> — prints rows, fills BREAKS
  local f="$1" line
  while IFS= read -r line; do
    [ -n "$line" ] && note "$line"
  done < <(parse_breaks "$f")

  local steps names dupes
  steps=$(grep -cE '^      - name:' "$f")
  names=$(grep -E '^      - name:' "$f" | sed -E 's/^      - name: //')
  dupes=$(printf '%s\n' "$names" | sort | uniq -d)
  if [ -n "$dupes" ]; then
    while IFS= read -r line; do
      [ -n "$line" ] && note "DUPLICATE step name=\"$line\""
    done <<< "$dupes"
  fi

  local pat lit matched ran_output checked=0 skipped=0 unmatched=0 shortened=0 executed=0
  while IFS= read -r pat; do
    [ -z "$pat" ] && continue
    lit="$(literal_of "$pat")"
    if [ ${#lit} -lt 6 ] || ! verdict_shaped "$lit"; then
      skipped=$((skipped + 1))
      continue
    fi
    checked=$((checked + 1))
    # Ask the RUN before asking the tree (#1169). Three units narrowed the same
    # approximation — each word searched separately (#1144), the longest adjacent prefix
    # (#1157), a floor on what survived (#1168) — and none of them can see a verdict whose
    # FORMAT changed while its words survived: `cases=%d` becoming `cases %d` leaves every
    # prefix matching. That is #1040's actual shape, the defect this whole line of work
    # descends from.
    #
    # The root is that all three read a format string, which is evidence about what a tool
    # MIGHT print. The only evidence about what it DOES print is a run. Several of the
    # commands the litany greps have a no-token, no-network mode that prints the very line
    # in question, so for those the approximation can be replaced by the answer.
    #
    # Only those. A pattern whose producer needs a token, a pull request or the daemon's
    # build is left to the prefix rule, and the count of each is on the verdict line — the
    # gap between "checked by running" and "checked by reading" is a number rather than an
    # assumption.
    if ran_output="$(run_producing "$lit")"; then
      if printf '%s\n' "$ran_output" | grep -qE -- "$pat"; then
        executed=$((executed + 1))
        continue
      fi
      unmatched=$((unmatched + 1))
      note "UNPRINTED grep=\"$lit\" — its producer was RUN and printed no line matching it"
      continue
    fi
    matched="$(printed_prefix "$lit")"
    if [ -z "$matched" ]; then
      unmatched=$((unmatched + 1))
      note "UNPRINTED grep=\"$lit\" — no run of it is printed by any tool, probe or source"
    elif [ "${#matched}" -lt "${#lit}" ]; then
      shortened=$((shortened + 1))
      # How much of the claim survived, judged rather than merely reported (#1158). The
      # prefix shortens from the right until it matches, and until now the only floor was
      # eight characters — which is one word, so `DATECHECK PASS VERDICT dialect=gnu`
      # would shorten to `DATECHECK` and pass. A reordered verdict loses everything after
      # its first token and the check calls that a match.
      #
      # The bound is on what SURVIVED, not on how much was dropped — and the first draft
      # got that backwards. "At most one dropped token" looked right and reddened
      # `RELEASE CHECK 12/12 locks green`, which is honest: `release.sh` prints
      # `RELEASE CHECK %d/%d locks green`, so a substitution in the MIDDLE takes every
      # word after it with it. Three dropped tokens there is correct behaviour.
      #
      # What separates that from a reordered verdict is what is LEFT. `RELEASE CHECK` is
      # two words in the tool's own order; `DATECHECK PASS VERDICT dialect=gnu` collapses
      # to `DATECHECK` alone, which is one word that happens to be long. So: two words
      # survive, or one word of sixteen characters — `SNAPSHOT_MATCHES_DIGEST` is a whole
      # verdict in one token and there is no second word to ask for.
      local got_words
      got_words="$(printf '%s' "$matched" | tr '= ' '\n\n' | grep -c '[^[:space:]]' || true)"
      if [ "$got_words" -lt 2 ] && [ "${#matched}" -lt 16 ]; then
        note "OVERSHORTENED grep=\"$lit\" matched=\"$matched\" survived=$got_words — one short word is not evidence the tool prints this line"
      else
        echo "PREFIX grep=\"$lit\" matched=\"$matched\" survived=$got_words (the tail is runtime values)"
      fi
    fi
  done < <(grep_patterns "$f")

  # Lock numbers: contiguous from 0, each claimed by a comment in the file.
  local nums lo hi missing=0 n
  nums=$(grep -oE 'lock [0-9]+' "$f" | awk '{print $2}' | sort -n -u)
  lo=$(printf '%s\n' "$nums" | head -1)
  hi=$(printf '%s\n' "$nums" | tail -1)
  for ((n = lo; n <= hi; n++)); do
    printf '%s\n' "$nums" | grep -qx "$n" || { missing=$((missing + 1)); note "LOCKGAP lock $n is cited by no comment"; }
  done

  echo "LITANY file=$f steps=$steps locks=$((hi - lo + 1)) greps_checked=$checked" \
       "executed=$executed unprinted=$unmatched shortened=$shortened not_verdicts=$skipped" \
       "lock_gaps=$missing breaks=${#BREAKS[@]}"
}

# ---------------------------------------------------------------- selftest

selftest() {
  local pass=0 fail=0
  tmp="$(mktemp -d "${TMPDIR:-/tmp}/litany.XXXXXX")"
  trap 'rm -rf "$tmp"' EXIT

  case_() {                     # case_ <name> <want-breaks 0|1+> <mutate-sed>
    local name="$1" want="$2" mutate="$3" got
    cp "$FILE" "$tmp/w.yml"
    [ -n "$mutate" ] && eval "$mutate"
    BREAKS=()
    got=$(judge "$tmp/w.yml" 2>&1 | tail -1 | sed -E 's/.* breaks=([0-9]+)$/\1/')
    if { [ "$want" = 0 ] && [ "$got" = 0 ]; } || { [ "$want" != 0 ] && [ "$got" != 0 ]; }; then
      pass=$((pass + 1)); printf 'LITANY case=%-22s want=%s got=%s OK\n' "$name" "$want" "$got"
    else
      fail=$((fail + 1)); printf 'LITANY case=%-22s want=%s got=%s BROKEN\n' "$name" "$want" "$got"
    fi
  }

  case_ the-tree-as-it-is    0 ''
  case_ conflict-marker      1 'printf "<<<<<<< HEAD\n" >> "$tmp/w.yml"'
  case_ literal-tab          1 'printf "\tfoo: bar\n" >> "$tmp/w.yml"'
  case_ duplicate-step-name  1 'printf "      - name: compile src\n        run: true\n" >> "$tmp/w.yml"'
  case_ step-that-does-nothing 1 'printf "      - name: a step with no body\n" >> "$tmp/w.yml"'
  # The ghost verdict is ASSEMBLED rather than written, and that is not fussiness:
  # spelled out here it would be a literal inside tools/, the very directory
  # question 2 searches — so the fixture would find itself and the case would
  # pass while proving nothing. A check whose test data lives inside its own
  # search path is the shape #898 is about.
  local ghost="NOBODY"; ghost="${ghost}PRINTS THIS EVER"; ghost="${ghost}LINE"
  case_ stale-verdict-grep   1 'echo "        run: grep -q \"$ghost\" x" >> "$tmp/w.yml"'
  # #1145: a RENAMED verdict, which the word-by-word form could not see. Both words of
  # "RELEASE GATE" exist in the tree — GATE is all over the release tooling — so only
  # ADJACENCY refuses it. The live spelling beside it is the row that proves the case is
  # about the rename and not about the sentence being long.
  local live="RELEASE CHECK 12/12 locks green"
  local renamed="RELEASE GATE 12/12 locks green"
  case_ renamed-verdict-grep 1 'echo "        run: grep -q \"$renamed\" x" >> "$tmp/w.yml"'
  case_ live-verdict-grep    0 'echo "        run: grep -q \"$live\" x" >> "$tmp/w.yml"'
  # #1158: a REORDERED verdict. Every word is real and adjacent to nothing it belongs
  # beside, so the prefix collapses to the first token — eight characters, over the old
  # floor, and it passed. The pair below is the whole case: one dropped token is a runtime
  # value, three is a different line.
  local reordered="DATECHECK PASS VERDICT dialect=gnu"
  local one_value="DATECHECK VERDICT PASS dialect=gnu"
  case_ reordered-verdict    1 'echo "        run: grep -q \"$reordered\" x" >> "$tmp/w.yml"'
  case_ one-runtime-value    0 'echo "        run: grep -q \"$one_value\" x" >> "$tmp/w.yml"'

  # Question 5 answers on a different axis — a directory of workflows rather
  # than one file's meaning — so its cases build their own tree instead of
  # mutating `locks.yml`. Four shapes: the clean block, and the three ways the
  # runner's own parser says no. The unclosed quote is the exact defect that
  # shipped; it is here so it cannot ship twice (#1203).
  shell_case() {                # shell_case <name> <want-bad> <block-body>
    local wf="$tmp/wf"
    rm -rf "$wf"; mkdir -p "$wf"
    # Written the way the tree writes a step — `- name:` then `run: |` on its
    # own line — because a fixture in a shape the real files never take proves
    # nothing about the real files.
    {
      printf 'name: fixture\njobs:\n  j:\n    steps:\n      - name: s\n        run: |\n'
      printf '%s\n' "$3" | sed 's/^/          /'
    } > "$wf/fixture.yml"
    local got
    got="$(shellcheck_workflows "$wf" | sed -n 's|.*unparseable=\([0-9]*\).*|\1|p')"
    shell_verdict "$1" "$2" "$got"
  }
  shell_case_inline() {         # shell_case_inline <name> <want-bad> <block-body>
    local wf="$tmp/wf"
    rm -rf "$wf"; mkdir -p "$wf"
    {
      printf 'name: fixture\njobs:\n  j:\n    steps:\n      - run: |\n'
      printf '%s\n' "$3" | sed 's/^/          /'
    } > "$wf/fixture.yml"
    local got
    got="$(shellcheck_workflows "$wf" | sed -n 's|.*unparseable=\([0-9]*\).*|\1|p')"
    shell_verdict "$1" "$2" "$got"
  }
  shell_verdict() {             # shell_verdict <name> <want> <got>
    if [ "$3" = "$2" ]; then
      pass=$((pass + 1))
    else
      fail=$((fail + 1))
      echo "SELFTEST FAIL $1 wanted unparseable=$2 got=${3:-none}"
    fi
  }
  # Question 6 has its own counter and therefore its own reader. Same fixtures,
  # same extractor — only the number being asked for differs, which is why the
  # helper takes the field name rather than a second copy of the case runner.
  shell_guard_case() {          # shell_guard_case <name> <want-unguarded> <block-body>
    local wf="$tmp/wf"
    rm -rf "$wf"; mkdir -p "$wf"
    {
      printf 'name: fixture\njobs:\n  j:\n    steps:\n      - name: s\n        run: |\n'
      printf '%s\n' "$3" | sed 's/^/          /'
    } > "$wf/fixture.yml"
    local got
    got="$(shellcheck_workflows "$wf" | sed -n 's|.*unguarded=\([0-9]*\).*|\1|p')"
    shell_verdict "$1" "$2" "$got"
  }
  shell_guard_case guarded       0 'set -euo pipefail
echo ok'
  shell_guard_case unguarded     1 'echo ok'
  # The exemption, declared. A block that must survive a failing command says so
  # on its first line rather than by omitting a line — `vary`'s move in
  # probes/bench.sh, where the reason rides the row instead of being skipped.
  shell_guard_case declared      0 '# litany: unguarded — this step must survive a failing probe
echo ok'
  # And a blank first line must not read as a declaration: the check reads the
  # first NON-BLANK line, and a fixture that opens with whitespace is how a
  # YAML-formatted block actually arrives.
  shell_guard_case blank-first   1 '
echo ok'

  shell_case clean-block      0 'set -euo pipefail
echo ok'
  shell_case unclosed-quote   1 "set -euo pipefail
grep -q 'PASS cases=[1-9] failed=0 file"
  shell_case unbalanced-brace 1 'set -euo pipefail
foo || { echo no >&2; exit 1;'
  shell_case dangling-if      1 'set -euo pipefail
if [ -f x ]; then echo y'
  # The dash spelling, which the first draft of `extract_blocks` did not match:
  # its blocks were skipped in silence, so three of the four cases above passed
  # by finding nothing at all. `blocks=` exists to make that visible, and the
  # case exists so the pattern cannot narrow again.
  shell_case_inline dash-step 1 'set -euo pipefail
if [ -f x ]; then echo y'

  printf 'LITANY SELFTEST VERDICT %s cases=%d failed=%d\n' \
    "$([ "$fail" = 0 ] && printf PASS || printf FAIL)" "$((pass + fail))" "$fail"
  [ "$fail" = 0 ]
}

# --------------------------------------------------------------- question 5
#
# DOES EVERY `run:` BLOCK PARSE AS THE SHELL THAT WILL RUN IT.
#
# The other four questions read `locks.yml` for meaning. This one reads every
# workflow for syntax, and it is here because the judge itself shipped broken:
# `litany.yml` landed with an unclosed single quote inside a grep pattern, and
# with the six lines after it duplicated — a perl substitution whose replacement
# contained `$'`, which perl expands to POSTMATCH, eating the closing quote and
# pasting the rest of the match back in. GitHub could not parse the file, so it
# produced no check at all; `gh pr checks` listed only `locks`, and the absence
# read exactly like "this workflow is not required here". Eleven runs went red
# in zero seconds before anyone read the run list (#1203).
#
# `bash -n` is the whole test. It costs milliseconds, it is the same parser the
# runner uses, and it catches the entire class: unterminated quotes, unbalanced
# braces, a heredoc with no terminator, an `if` with no `fi`.
#
# Block scalars only — `run: |` and `run: >`. A one-line `run: foo` is subject
# to YAML's own quoting rules, and reconstructing what the runner would hand the
# shell means implementing YAML; the count says how many blocks were read so a
# reader can tell coverage from silence.

extract_blocks() {              # extract_blocks <file> <dir> — one file per run: block
  awk -v dir="$2" -v tag="$3" '
    # Both spellings. `        run: |` is how every step in this tree writes it,
    # and `      - run: |` is the one-key step YAML also allows — the selftest
    # caught the second being missed, which is the whole reason the suite builds
    # fixtures instead of asserting against the tree it is checking.
    /^[[:space:]]*-?[[:space:]]*run:[[:space:]]*[|>]/ {
      match($0, /^[[:space:]]*/)
      indent = RLENGTH
      n++
      out = dir "/" tag "-" n ".sh"
      inblock = 1
      next
    }
    inblock {
      if ($0 ~ /^[[:space:]]*$/) { print "" > out; next }
      match($0, /^[[:space:]]*/)
      if (RLENGTH <= indent) { inblock = 0 }
      else { print substr($0, indent + 1) > out; next }
    }
  ' "$1"
}

shellcheck_workflows() {        # shellcheck_workflows [dir] — defaults to the real one
  local dir="${1:-.github/workflows}"
  local tmp blocks=0 bad=0 f b err
  tmp="$(mktemp -d)"
  # SHELL_TMP, not the local: an EXIT trap fires after the function's frame is
  # gone, so a trap quoting `$tmp` dies on `set -u` at the moment of leaving —
  # after the verdict has printed, which is the worst place to put a crash.
  SHELL_TMP="$tmp"
  trap 'rm -rf "${SHELL_TMP:-}"' EXIT
  for f in "$dir"/*.yml "$dir"/*.yaml; do
    [ -f "$f" ] || continue
    extract_blocks "$f" "$tmp" "$(basename "$f" | tr -c 'a-zA-Z0-9' '-')"
  done
  local unguarded=0 first
  for b in "$tmp"/*.sh; do
    [ -f "$b" ] || continue
    blocks=$((blocks + 1))
    if ! err="$(bash -n "$b" 2>&1)"; then
      bad=$((bad + 1))
      # The block's own file name carries the workflow it came from, and the
      # shell's message carries the line within it. Both, on one line.
      echo "SHELL_UNPARSEABLE $(basename "$b") ${err##*: }"
    fi
    # `bash -n` answers "does it parse". This answers "what will it run under",
    # and the two questions live one line apart in the same extractor (#1226).
    #
    # A `run:` block with no `shell:` gets GitHub's default, `bash -e {0}` — so
    # it has -e and lacks -u and pipefail, silently, while twenty-one blocks
    # beside it set all three. `evidence baseline` was that block: lock 0, the
    # only lock that judges a CLAIM rather than a compilation, running under
    # weaker guarantees than the ones that judge a compile, with `RUNNER_TEMP`
    # unquoted where every neighbour spells `${RUNNER_TEMP:-/tmp}`.
    #
    # A block may decline, the way a bench row declines the determinism pass:
    # `# litany: unguarded <reason>` on its first line says the difference out
    # loud. Omitting the guard silently is what this refuses.
    first="$(grep -m1 -vE '^[[:space:]]*$' "$b" || true)"
    case "$first" in
      *'set -euo pipefail'*) ;;
      *'# litany: unguarded'*) ;;
      *)
        unguarded=$((unguarded + 1))
        echo "SHELL_UNGUARDED $(basename "$b") opens with: $(printf '%s' "$first" | cut -c1-60)"
        ;;
    esac
  done
  printf 'LITANY SHELL VERDICT %s blocks=%d unparseable=%d unguarded=%d\n' \
    "$([ "$bad" = 0 ] && [ "$unguarded" = 0 ] && printf PASS || printf FAIL)" \
    "$blocks" "$bad" "$unguarded"
  [ "$bad" = 0 ] && [ "$unguarded" = 0 ]
}

# --------------------------------------------------------------- question 7
#
# DOES EVERY SUITE GATE CARRY A FLOOR.
#
# `cases=[1-9][0-9]*` asserts that SOME cases ran and nothing about how many.
# A suite that lost every case but one — a bad rebase, a deleted `case_` line, a
# helper renamed so the calls below it silently no-op — prints `cases=1
# failed=0` and the lane says PASS. #1040 named that for the baseline gate and
# #1073 for the exit-grammar gate; both grew a floor. Five gates did not, and
# nothing said so (#1213).
#
# Two shapes today were caught by a human reading output rather than by a lane:
# `litany.sh`'s own new question passed three of four cases by finding NOTHING
# (#1203), and `prstate.sh`'s suite could not express an empty-pattern case at
# all because `${7:-main}` turned empty into "main" (#1210). Neither would have
# been caught by a floor either — a floor catches disappearance, not vacuity.
# It is the cheaper half of a real problem, and the half nothing was doing.
#
# What is checked: a step that greps a `SELFTEST VERDICT PASS cases=` line must
# also compare that count against a `floor=`. The two must be in the same block,
# because a floor declared in a different step guards a different suite.

floor_breaks() {                # floor_breaks <file> — one line per unfloored gate
  local f="$1"
  awk '
    /^      - name:/ { name = $0; sub(/^[[:space:]]*- name:[[:space:]]*/, "", name); gate = 0; floored = 0 }
    /SELFTEST VERDICT PASS cases=/ { gate = 1 }
    /floor=[0-9]/ { floored = 1 }
    # A step ends where the next begins, and the report is emitted then — so a
    # gate whose floor sits below its grep is still counted as floored.
    /^      - name:/ || /^jobs:/ {
      if (prev_gate && !prev_floored) { print "FLOOR ungated step=" prev_name }
      prev_gate = 0; prev_floored = 0; prev_name = name
    }
    { if (gate) prev_gate = 1; if (floored) prev_floored = 1; if (name != "") prev_name = name }
    END { if (prev_gate && !prev_floored) { print "FLOOR ungated step=" prev_name } }
  ' "$f"
}

floorcheck_workflows() {
  local dir="${1:-.github/workflows}" f gates=0 unfloored=0 row
  for f in "$dir"/*.yml "$dir"/*.yaml; do
    [ -f "$f" ] || continue
    # `grep -c` prints a count and exits 1 when that count is zero, so `|| echo 0`
    # appends a SECOND number and the arithmetic sees "0\n0". The count is always
    # printed; the exit code is the thing to ignore.
    local n
    n="$(grep -c 'SELFTEST VERDICT PASS cases=' "$f" 2>/dev/null)" || true
    gates=$((gates + ${n:-0}))
    while IFS= read -r row; do
      [ -n "$row" ] || continue
      unfloored=$((unfloored + 1))
      echo "$row file=$(basename "$f")"
    done <<< "$(floor_breaks "$f")"
  done
  printf 'LITANY FLOOR VERDICT %s gates=%d unfloored=%d\n' \
    "$([ "$unfloored" = 0 ] && printf PASS || printf FAIL)" "$gates" "$unfloored"
  [ "$unfloored" = 0 ]
}

if [ "$FLOORCHECK" = yes ]; then
  floorcheck_workflows
  exit $?
fi

if [ "$SHELLCHECK" = yes ]; then
  shellcheck_workflows
  exit $?
fi

if [ "$SELFTEST" = yes ]; then
  selftest
  exit $?
fi

[ -f "$FILE" ] || { echo "FATAL no litany at $FILE" >&2; exit 1; }
judge "$FILE"
if [ "${#BREAKS[@]}" -eq 0 ]; then
  echo "LITANY VERDICT PASS"
else
  echo "LITANY VERDICT FAIL breaks=${#BREAKS[@]}"
fi
[ "${#BREAKS[@]}" -eq 0 ]
