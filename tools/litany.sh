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
# FOUR QUESTIONS, in the order they can be answered without running a job:
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
# Exit 0 when every question is answered clean, 1 otherwise. The verdict line is
# the contract, the way a probe's is.

set -uo pipefail

FILE="${1:-.github/workflows/locks.yml}"
SELFTEST=no
[ "${1:-}" = "--selftest" ] && { SELFTEST=yes; FILE=.github/workflows/locks.yml; }

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

# Every word of the literal must appear in the sources that could print it —
# each word rather than the whole run, because a verdict is usually assembled by
# a printf with substitutions in the middle, so the full string exists only at
# runtime. A stale grep loses a WORD (a renamed verdict, a dropped field), and
# that is what this catches; a reordering it does not.
printed_words() {               # printed_words <literal>
  local word
  # `=` splits too, because a field is printed as `dialect=%s` and its VALUE
  # never appears in the source at all — asking for `dialect=gnu` verbatim finds
  # nothing and would call `tools/balance.sh` stale while it prints that exact
  # line. What survives the split is the field NAME, which is the half a rename
  # actually breaks.
  for word in $(printf '%s' "$1" | tr '=' ' '); do
    case "$word" in
      [A-Za-z_]*) ;;
      *) continue ;;                     # a value or punctuation, not a name
    esac
    [ ${#word} -lt 4 ] && continue
    grep -rqF -- "$word" tools/ probes/ src/ 2>/dev/null || return 1
  done
  return 0
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

  local pat lit checked=0 skipped=0 unmatched=0
  while IFS= read -r pat; do
    [ -z "$pat" ] && continue
    lit="$(literal_of "$pat")"
    if [ ${#lit} -lt 6 ] || ! verdict_shaped "$lit"; then
      skipped=$((skipped + 1))
      continue
    fi
    checked=$((checked + 1))
    if ! printed_words "$lit"; then
      unmatched=$((unmatched + 1))
      note "UNPRINTED grep=\"$lit\" — no tool, probe or source prints this"
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
       "unprinted=$unmatched not_verdicts=$skipped lock_gaps=$missing breaks=${#BREAKS[@]}"
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

  printf 'LITANY SELFTEST VERDICT %s cases=%d failed=%d\n' \
    "$([ "$fail" = 0 ] && printf PASS || printf FAIL)" "$((pass + fail))" "$fail"
  [ "$fail" = 0 ]
}

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
