#!/usr/bin/env bash
# tools/advice.sh — a tool that tells you what to type owes you a working command (#1095)
#
# Usage: tools/advice.sh            audit every tool's printed advice
#        tools/advice.sh --list     print the advice lines it found, and stop
#
# THE FINDING THIS EXISTS FOR. Three units in one day fixed the same defect in
# three different tools, and it was the same defect each time: a tool printed a
# command, and the command was wrong in a way only EXECUTING it revealed.
#
#   #1012  attribution.sh printed `--reset-author`, which collapses every author
#          date it touches onto the instant of the repair. The tool exists
#          because history is evidence; its advice destroyed the evidence.
#   #972   release.sh's lock 2 matched all of --selftest's stdout against a line
#          that was no longer first, so a release could not be cut from a GREEN
#          main. A gate that fails on success gets routed around.
#   #952   balance.sh's deficit advice named a leg the arithmetic could not move.
#
# Prose is not executed, so prose rots silently — and advice is the worst prose
# to be wrong in, because it is read at the moment somebody has already decided
# to act.
#
# WHAT THIS CAN AND CANNOT DO. It cannot run the advice: `git commit --amend` in
# an audit would be a tool that damages the tree to check whether it damages the
# tree. What it does is cheaper and answers most of the same question:
#
#   1. FIND the advice. An indented command line inside an `echo` to stderr is
#      how every tool here prints "type this", and the shape is consistent
#      enough to grep because the tools were written by one hand to one house
#      style. A tool that invents a new shape is invisible here, which is why
#      the found count is printed rather than assumed.
#
#   2. NAME the tool it tells you to run. If the advice says `tools/x.sh --flag`
#      or `<something> --flag`, then that flag must EXIST in that tool — a
#      printed flag nobody implements is #952's defect exactly, and it is
#      decidable without running anything.
#
#   3. ASK whether the tool that prints advice can be falsified at all. A tool
#      with no --selftest has no place to put a case that executes its advice,
#      so its advice is unfalsifiable by construction. That is reported per
#      tool, because it is the gap the rule in tools/README.md is about.
#
# Exit 0 when every found flag exists in the tool it names. The unfalsifiable
# count is REPORTED, not judged: four tools have no selftest today and turning
# that into a red build would be a demand this unit has not argued for.

set -uo pipefail

cd "$(dirname "$0")/.."

LIST=no
[ "${1:-}" = "--list" ] && LIST=yes

# An unknown flag is refused, and `--selftest` is the reason (#1212). This tool
# had no suite and no argument parsing, so `tools/advice.sh --selftest` ran the
# ordinary audit and printed `ADVICE VERDICT EVERY_FLAG_ADVISED_EXISTS` — a
# green line from a suite that does not exist. A lane step written in good faith
# against that invocation would have passed forever while proving nothing, which
# is the vacuous pass this tool was built to hunt in other people's scripts.
#
# `release.sh` already refuses the same flag by accident, through its positional
# usage check. This makes the refusal deliberate rather than lucky, and exit 2 is
# the tree's code for a refused invocation.
case "${1:-}" in
  ''|--list) ;;
  *) echo "FATAL unknown argument: $1 (this tool takes --list, or nothing)" >&2; exit 2 ;;
esac

# An advice line: an echo whose payload starts with four or more spaces. The
# house style indents the command a reader is meant to copy, which is what
# separates it from the sentence explaining why.
advice_lines() {                # advice_lines <file>
  grep -nE '^\s*(echo|printf) .*"[[:space:]]{4,}[^"]' "$1" 2>/dev/null || true
}

# The flags a line tells you to type, and the tool it tells you to type them at.
# `tools/x.sh --flag` and `x.sh --flag` both name x.sh; a bare `--flag` names the
# tool doing the printing, since that is what "pass --for OWNER/NAME" means.
flags_of() {                    # flags_of <line>
  printf '%s' "$1" | grep -oE '\-\-[a-z][a-z-]+' || true
}

named_tool() {                  # named_tool <line> <default>
  local named
  named="$(printf '%s' "$1" | grep -oE '(tools/)?[a-z-]+\.sh' | head -1 || true)"
  if [ -n "$named" ]; then
    printf 'tools/%s' "$(basename "$named")"
    return
  fi
  # No script named, so the flag belongs to the tool printing the line — UNLESS
  # the line tells you to run somebody else's program. `gh auth switch --user X`
  # is advice about gh, and its flags are gh's to implement. The first run of
  # this audit reported balance.sh advising `--user` "at tools/balance.sh, which
  # does not mention it", which was true and not a defect.
  if printf '%s' "$1" | grep -qE '\b(gh|git|java|javac|bash|sh|curl) '; then
    printf 'external'
    return
  fi
  printf '%s' "$2"
}

found=0
checked=0
missing=0
unfalsifiable=0
BREAKS=0

for tool in tools/*.sh; do
  [ "$tool" = "tools/advice.sh" ] && continue    # a checker inside its own search path (#1157)
  has_selftest=no
  grep -qE '\-\-(selftest|selfcheck|rulercheck|datecheck|check)\b' "$tool" && has_selftest=yes
  [ "$has_selftest" = no ] && unfalsifiable=$((unfalsifiable + 1))

  while IFS= read -r hit; do
    [ -z "$hit" ] && continue
    found=$((found + 1))
    line="${hit#*:}"
    [ "$LIST" = yes ] && printf 'ADVICE %s %s\n' "$tool" "$(printf '%s' "$line" | cut -c1-90)"
    target="$(named_tool "$line" "$tool")"
    [ -f "$target" ] || continue                 # advice about git, gh, java — not ours to check
    while IFS= read -r flag; do
      [ -z "$flag" ] && continue
      checked=$((checked + 1))
      # Searched OUTSIDE the advice lines, which is not a detail. A tool that
      # advises its own flag satisfies a naive grep with the advice itself: the
      # sentence telling you to type `--foo` contains `--foo`, so an unimplemented
      # flag certifies itself. Found by writing the falsification, watching it
      # pass, and looking at why — the third time this shape has appeared in a
      # checker today (#1144's ghost verdict, #1157's own comments).
      # Captured before it is searched, rather than piped into `grep -q`. Under
      # `set -o pipefail` the -q exits at the first match, the upstream grep takes
      # SIGPIPE, and the PIPELINE reports failure — so every flag that exists
      # reported as missing, and the four that were genuinely fine looked broken.
      # A capture has no upstream to kill. tools/README.md's capture rule says the
      # same thing from the other direction.
      body="$(grep -vE '^\s*(echo|printf) .*"[[:space:]]{4,}[^"]' "$target" || true)"
      # Matched in the shell, with no pipe at all. The previous two drafts both died on
      # the same mechanism from opposite ends: `grep -v … | grep -q` failed under pipefail
      # because -q's early exit SIGPIPEs the upstream, and `printf … | grep -q` failed
      # because -q's early exit SIGPIPEs the PRINTF — which on this box was silent and on
      # ubuntu-latest printed `write error: Broken pipe` and took the exit code with it.
      # It went green locally and red in CI, which is #836's shape one tool over: a
      # platform difference in a script nobody ran on the other platform. `case` reads a
      # variable and cannot be interrupted by a reader that stops reading.
      case "$body" in
        *"$flag"*) ;;
        *)
          missing=$((missing + 1))
          BREAKS=$((BREAKS + 1))
          echo "UNIMPLEMENTED $tool advises '$flag' at $target, which does not mention it"
          ;;
      esac
    done <<< "$(flags_of "$line")"
  done <<< "$(advice_lines "$tool")"

  [ "$has_selftest" = no ] && echo "UNFALSIFIABLE $tool has no --selftest: its advice has nowhere to be executed"
done

# Every tool is in the catalog, or nobody can find out what it is for (#1188). This is
# #1177's check one directory over: `probes/bench.sh`'s roster asks the same question of
# `probes/README.md`, and it found five probes with a bench row and no catalog row. Here
# it found two — `litany.sh` and `advice.sh` itself, both landed today, both in the lane
# and in no document.
#
# The catalog is the only place that says what a tool is FOR. A tool absent from it has a
# verdict line and no explanation of what the verdict means, which is the state
# LedgerMirror's sweep was in before #1130.
uncatalogued=0
for tool in tools/*.sh; do
  name="$(basename "$tool")"
  grep -q "\`$name\`" tools/README.md && continue
  uncatalogued=$((uncatalogued + 1))
  BREAKS=$((BREAKS + 1))
  echo "UNCATALOGUED $name has no row in tools/README.md — nobody can find out what it is for"
done

# A catalog row PROMISES flags, and nothing checked that the tool has them (#1192). The
# readers added today catch a MISSING row; a WRONG row is prose about a program written by
# the person who wrote the program, and nothing compares the two ever again — which is
# exactly the shape #1130 found in LedgerMirror's javadoc.
#
# What is checkable without inventing a language is the quotable part. Each row opens with
# the tool it describes, so every `--flag` on that row is a promise about THAT tool, and a
# flag the tool does not mention is a promise it cannot keep. The prose around it is not
# judged and could not be.
# A tool that spends exit codes owes its row a list of them (#1222).
#
# `checkage.sh` and `prstate.sh` document theirs — 0 CURRENT · 1 STALE · 2 refused, and so
# on — because a caller branching on `$?` needs the vocabulary. Four tools spend codes and
# say nothing: `attribution.sh` spends six, `balance.sh` six, and both rows are silent.
# An undocumented code is a contract with no reader, and this tree branches on `$?` in
# workflows, in `release.sh`, and in every crew member's shell.
#
# Only LITERAL exits are counted. `prstate.sh` reaches its codes through `code_for`, so a
# grep for `exit [0-9]` finds three of its seven — the shape is reported as `indirect` and
# not judged, because inferring a code from a lookup table means interpreting the script,
# and a checker that guesses is the thing this file exists to refuse.
codes_undocumented=0
codes_indirect=0
for tool in tools/*.sh; do
  name="$(basename "$tool")"
  spends="$(grep -oE '(^|[^_a-zA-Z])exit [0-9]+|EXIT_[A-Z_]+=[0-9]+|EXIT_[A-Z_]+ = [0-9]+' "$tool" \
            | grep -oE '[0-9]+$' | sort -un | tr '\n' ' ')"
  # Trim, and ignore a tool whose only literal code is 0 — every script ends by
  # succeeding, and "exits 0 on success" is not a vocabulary worth a row.
  spends="$(printf '%s' "$spends" | sed -E 's/^ +| +$//g')"
  [ -n "$spends" ] && [ "$spends" != "0" ] || continue
  # The row this tool OPENS, not every row that mentions it. `release.sh`'s row
  # names `attribution.sh` while explaining which locks a cut skips, so a bare
  # grep returns two rows and the wrong one's `Exit …` answers for the right
  # one — a checker reading a neighbour's homework, which is the same shape the
  # `rowbody` comment below was written for.
  row="$(grep "^| \`$name\`" tools/README.md || true)"
  [ -n "$row" ] || continue     # UNCATALOGUED already said this, above
  if printf '%s' "$tool" | grep -q . && grep -qE 'exit +"?\$\(' "$tool"; then
    codes_indirect=$((codes_indirect + 1))
    echo "CODES $name indirect: reaches at least one exit through a lookup, literals=[$spends]"
  fi
  case "$row" in
    *"Exit "*) ;;
    *)
      codes_undocumented=$((codes_undocumented + 1))
      BREAKS=$((BREAKS + 1))
      echo "CODES_UNDOCUMENTED $name spends [$spends] and its row says nothing about exit codes"
      ;;
  esac
done

catalog_wrong=0
while IFS= read -r row; do
  [ -z "$row" ] && continue
  rowtool="tools/$(printf '%s' "$row" | grep -oE '^\| `[a-z-]+\.sh`' | grep -oE '[a-z-]+\.sh' | head -1)"
  [ -f "$rowtool" ] || continue
  # The body searched is every tool the ROW names, not only the one it is about. A row
  # legitimately mentions another tool's flags: `release.sh`'s explains which locks a cut
  # skips, and names `balance.sh --datecheck` and `--rulercheck` among them. The first run
  # of this check reported that as a broken promise, which it is not — a row describing a
  # neighbour is describing a neighbour.
  rowbody="$(cat "$rowtool")"
  while IFS= read -r other; do
    [ -z "$other" ] && continue
    [ -f "tools/$other" ] && rowbody="$rowbody
$(cat "tools/$other")"
  done <<< "$(printf '%s' "$row" | grep -oE '[a-z-]+\.sh' | sort -u)"
  while IFS= read -r flag; do
    [ -z "$flag" ] && continue
    case "$rowbody" in
      *"$flag"*) ;;
      *)
        catalog_wrong=$((catalog_wrong + 1))
        BREAKS=$((BREAKS + 1))
        echo "CATALOG_CLAIM $rowtool's row promises '$flag' and the tool does not mention it"
        ;;
    esac
  done <<< "$(printf '%s' "$row" | grep -oE '`\-\-[a-z][a-z-]+`' | tr -d '`' | sort -u)"
done <<< "$(grep -E '^\| `[a-z-]+\.sh`' tools/README.md)"

# A shell verdict is bytes too (#1196, #836). Lock 8 exists because with no locale
# exported, JDK 17 resolves the default charset to ANSI_X3.4-1968 and every em dash a
# main() printed became `?`. It forces LC_ALL=C, compares the bytes, and scans all 45
# probes for their `Streams.utf8()` pin. The TOOLS were in none of that — and their
# verdict lines carry em dashes, arrows and bullets that CI greps.
#
# Only the tools with a --selftest are run: that mode is no-token, no-network and finishes
# in seconds by the rule tools/README.md sets for it, which makes it the one invocation
# safe to make twice from inside an audit. The rest are counted, not skipped in silence.
#
# `carries` is the guard lock 8 learned the hard way: a comparison of two ASCII outputs
# passes and proves nothing. A tool whose selftest prints no byte above 0x7f is reported
# as `nothing-to-prove` rather than as a pass.
charset_checked=0
charset_nothing=0
charset_drift=0
suites=0
no_suite=0
unrun=0
for tool in tools/*.sh; do
  [ "$tool" = "tools/advice.sh" ] && continue
  grep -qE '\-\-selftest\b' "$tool" || continue
  # MATRIX_TOOL_DEPTH: this is a tool running other tools, and one of the tools
  # it runs — `litany.sh` — runs tools of its own to check their verdicts
  # (#1169). Without a depth to read, the two call each other without end:
  # thirty-nine processes in eight seconds from one bare `advice.sh` (#1206).
  # `timeout 60` bounds a leaf and not a tree. The depth is exported rather
  # than a name being excluded here, because the next tool that audits tools
  # will close the same cycle by a different route, and an exclusion list is a
  # thing to forget.
  utf8="$(MATRIX_TOOL_DEPTH=$((${MATRIX_TOOL_DEPTH:-0} + 1)) \
          timeout 60 bash "$tool" --selftest 2>&1 || true)"
  ascii="$(LC_ALL=C MATRIX_TOOL_DEPTH=$((${MATRIX_TOOL_DEPTH:-0} + 1)) \
          timeout 60 bash "$tool" --selftest 2>&1 || true)"
  # Does that invocation reach a SUITE, or did the tool merely tolerate the
  # flag? `grep -qE -- --selftest` above says only that the string appears in
  # the file — and it appears in THIS file for every tool it runs, which is how
  # `advice.sh` and `release.sh` were both counted as having suites they do not
  # have (#1212). The verdict line is the discriminator: a real suite prints
  # `<NAME> SELFTEST VERDICT …`, and a tool that ran its ordinary self prints
  # something else or refuses the flag outright.
  if ! printf '%s' "$utf8" | grep -qE '^([A-Z][A-Z0-9_]* )+SELFTEST VERDICT '; then
    no_suite=$((no_suite + 1))
    echo "SUITE $tool none: --selftest reaches no suite (the flag is not a promise)"
    continue
  fi
  suites=$((suites + 1))
  # A suite the lane never runs is a falsification nobody performs. `litany.yml`
  # and `locks.yml` are read for the invocation, not for the tool's name: the
  # tool appears in the lane whenever the lane runs it at all, and the question
  # here is narrower.
  if ! grep -qE "$(basename "$tool") --selftest" .github/workflows/*.yml 2>/dev/null; then
    unrun=$((unrun + 1))
    BREAKS=$((BREAKS + 1))
    echo "SUITE_UNRUN $tool has a suite no workflow executes"
  fi
  if ! printf '%s' "$utf8" | LC_ALL=C grep -q '[^ -~]'; then
    charset_nothing=$((charset_nothing + 1))
    echo "CHARSET $tool nothing-to-prove: its selftest prints no byte above 0x7f"
    continue
  fi
  charset_checked=$((charset_checked + 1))
  if [ "$utf8" != "$ascii" ]; then
    charset_drift=$((charset_drift + 1))
    BREAKS=$((BREAKS + 1))
    echo "CHARSET_DRIFT $tool differs between UTF-8 and LC_ALL=C"
  fi
done

echo "ADVICE tools=$(ls tools/*.sh | wc -l | tr -d ' ') uncatalogued=$uncatalogued catalog_wrong=$catalog_wrong charset_checked=$charset_checked charset_nothing=$charset_nothing suites=$suites no_suite=$no_suite unrun=$unrun codes_undocumented=$codes_undocumented codes_indirect=$codes_indirect lines=$found flags_checked=$checked" \
     "unimplemented=$missing unfalsifiable=$unfalsifiable"
if [ "$BREAKS" -eq 0 ]; then
  echo "ADVICE VERDICT EVERY_FLAG_ADVISED_EXISTS"
elif [ "$missing" -gt 0 ]; then
  echo "ADVICE VERDICT ADVISES_A_FLAG_NOBODY_IMPLEMENTS unimplemented=$missing"
elif [ "$codes_undocumented" -gt 0 ]; then
  # A fifth word (#1222). A tool spending codes its row does not name is not a
  # missing flag, a missing row, a lying row, or an unrun suite: everything is
  # present and one contract is unwritten. `A_CATALOG_ROW_PROMISES_WHAT_THE_TOOL_LACKS`
  # would send the reader looking for a promise, and there is none — that is the
  # defect.
  echo "ADVICE VERDICT A_TOOL_SPENDS_CODES_ITS_ROW_DOES_NOT_NAME undocumented=$codes_undocumented"
elif [ "$unrun" -gt 0 ]; then
  # A fourth failure and a fourth word (#1212). A suite the lane never executes
  # is not a missing flag, not a missing catalog row and not a lying one: the
  # tool is right, the document is right, and the falsification is simply never
  # performed. Naming it as any of the other three sends the reader to a file
  # where nothing is wrong.
  echo "ADVICE VERDICT A_SUITE_NOBODY_RUNS unrun=$unrun"
elif [ "$uncatalogued" -gt 0 ]; then
  # Three failures, three words. A catalog gap reported as "advises a flag nobody
  # implements" sends the reader to the wrong file, which is the same class of error as a
  # defect report that names the wrong defect (#1170) — and a catalog row that promises a
  # flag is a third thing again: the tool is fine, the document is lying about it.
  echo "ADVICE VERDICT A_TOOL_NOBODY_CAN_FIND uncatalogued=$uncatalogued"
else
  echo "ADVICE VERDICT A_CATALOG_ROW_PROMISES_WHAT_THE_TOOL_LACKS catalog_wrong=$catalog_wrong"
fi
[ "$BREAKS" -eq 0 ]
