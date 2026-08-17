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
# Exit 0 when every found flag exists in the tool it names.
#
# THE UNFALSIFIABLE COUNT IS NOW JUDGED (#1311). It was REPORTED for a stated
# reason — "four tools have no selftest today and turning that into a red build
# would be a demand this unit has not argued for" — and that reason expired:
# #1307 and #1309 gave the last two their suites, and the count is 0.
#
# Zero is the only moment this costs nothing. At zero the gate blocks the next
# tool that arrives without a suite; at one it demands a unit from whoever trips
# it, which is how a gate gets argued about instead of added. Leaving a
# justification whose premise is false is the third option and the worst — it
# reads as current, which is the shape #1279 and #1284 spent two units on.
#
# WHAT THE GATE DEMANDS, precisely, because the wording is the whole risk: every
# tool must be FALSIFIABLE SOMEWHERE, not that every path is covered.
# `issuetree.sh` and `subissue.sh` both cover their DOOR and not their till —
# the token half stays out because a fixture that fakes `gh` tests the fake
# (#1273) — and both satisfy this gate. A tool with a suite that runs one case
# satisfies it too. The floor on each lane step is what guards depth; this
# guards existence.

set -uo pipefail

cd "$(dirname "$0")/.."

# A tool's body with its comment lines dropped, CAPTURED so that nothing reads it
# through a pipe (#1398).
#
# Under `set -o pipefail`, `grep -v … | grep -q` reports FAILURE whenever the -q
# quits before the upstream has finished writing: the upstream takes SIGPIPE and
# pipefail promotes that to the pipeline's status. Whether it happens is a race
# between two processes, so the boolean it decides — report this tool, or exempt
# it — was being chosen by the scheduler. On about a third of CI runs this file
# reported ITSELF as promising an exit code it never spends, because the
# exemption for a pass-through `exit $?` was skipped by a broken pipe:
#
#     grep: write error: Broken pipe
#     CODES_UNSPENT advice.sh promises exit 1 and never spends it, literals=[2 ]
#
# The mechanism is named thirty lines above the `UNIMPLEMENTED` check, where it
# was diagnosed and repaired — at that one site, and nowhere else. This is the
# rest of the class. A capture has no upstream to kill, and a here-string is fed
# by the shell rather than by a process that can be interrupted.
uncommented() {                 # uncommented <tool> — its body, comment lines dropped
  grep -vE '^[[:space:]]*#' "$1" || true
}

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
SELFTEST=no
case "${1:-}" in
  ''|--list) ;;
  --selftest) SELFTEST=yes ;;
  *) echo "FATAL unknown argument: $1 (this tool takes --list, --selftest, or nothing)" >&2; exit 2 ;;
esac

# The suite's harness is defined below the audit it exercises, because the
# function it calls is defined there — and bash defines a function when it
# reaches it, not when it parses the file. So under --selftest the ordinary
# audit still RUNS and its output is parked on fd 3 instead of the terminal,
# with the suite's own lines restored to stdout before the first case. Two
# consequences, both wanted: the suite's output is not buried under the shop's,
# and a crash in the ordinary path fails --selftest too, which is the correct
# reading — a suite that passes while the tool it belongs to dies is the
# vacuous pass this file exists to hunt.
if [ "$SELFTEST" = yes ]; then
  exec 3>&1 >/dev/null
fi

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
  if grep -qE '\b(gh|git|java|javac|bash|sh|curl) ' <<< "$1"; then
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
  # The falsifiability census counts EVERY tool here, this one included
  # (#1265). #1157's skip below is about prose — a checker reading its own
  # explanation and reporting itself — and it was applied to the whole loop,
  # which quietly exempted this file from the census it publishes. So the
  # program that prints `UNFALSIFIABLE <tool> has no --selftest` and counts it
  # was, itself, an uncounted third. It has a suite now, and it is counted
  # whether it has one or not.
  has_selftest=no
  grep -qE '\-\-(selftest|selfcheck|rulercheck|datecheck|check)\b' "$tool" && has_selftest=yes
  if [ "$has_selftest" = no ]; then
    unfalsifiable=$((unfalsifiable + 1))
    BREAKS=$((BREAKS + 1))          # judged since #1311, at the moment it cost nothing
    echo "UNFALSIFIABLE $tool has no --selftest: its advice has nowhere to be executed"
  fi

  [ "$tool" = "tools/advice.sh" ] && continue    # a checker inside its own search path (#1157)

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

  # (the UNFALSIFIABLE line is printed with the count, at the top of the loop)
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

# THE OTHER WAY A CATALOG BREAKS: more than one row per tool (#1340). The
# catalog is a table with a primary key nobody declared, and every check in this
# file assumes the mapping is a function. `balance.sh` had TWO rows opening with
# its name, one stale by two units — and `row="$(grep "^| \`$name\`" …)"` returns
# both, joined by a newline, so every downstream check read the concatenation as
# one row. A flag documented in either satisfied the audit for the other; an exit
# clause in either answered for both; and `flags_phantom`'s bounded read stopped
# at the FIRST row's `Exit`, so the second row's Usage clause was never checked
# at all.
#
# Nothing counted it. `uncatalogued=` counts tools with no row; nothing counted
# tools with more than one, and the discrepancy was visible only as
# `CODES_CENSUS catalog_rows=13` beside `tools=12` — two numbers on two lines
# that nobody had compared.
rows_duplicated=0
for tool in tools/*.sh; do
  name="$(basename "$tool")"
  n="$(grep -c "^| \`$name\`" tools/README.md || true)"
  [ "$n" -le 1 ] && continue
  rows_duplicated=$((rows_duplicated + 1))
  BREAKS=$((BREAKS + 1))
  echo "DUPLICATE_ROW $name opens $n rows in tools/README.md — every check here reads them as one"
done

# The manual read in the OTHER direction (#1033).
#
# Everything above asks whether the catalog's promises are kept: a row naming
# `--foo` must belong to a tool that has one. That is the direction a human eye
# does well, because the reader is holding the row.
#
# Nothing asked the reverse — whether a flag the tool PARSES appears in its row
# at all. #971 fixed two instances of it by hand (`attribution.sh --for`,
# `balance.sh --events`/`--rulercheck`) and said plainly that it was a
# correction and not a cure. The first sweep here found seven more, four of
# them landed the same week the check was written: `--fix-cmd` and `--selftest`
# on attribution, `--age` and `--selftest` on digest-move, `--floorcheck` and
# `--shellcheck` on litany, `--pr` on prstate. Invisible capability is a
# quieter defect than a broken promise and it lasts longer: nobody types a flag
# they have never read about, so the feature is dead and the tree still pays to
# maintain it.
#
# The accepted set is read two ways, because this directory parses arguments
# two ways: a `case` arm (`--pr)`, `--for|--pr)`), and a direct comparison
# (`[ "${1:-}" = "--selftest" ]`, which is how litany.sh and baseline.sh do it).
# Comments are stripped first — a tool that discusses `--foo` in its header is
# not parsing it, and this is the fifth time in this file that a checker had to
# be kept out of its own prose.
#
# The advertised set is the WHOLE row rather than its `Usage:` clause, and that
# is a deliberate weakening. A row's prose legitimately names other programs'
# flags — `git commit --amend`, `java … --selftest`, `balance.sh --rulercheck`
# in the row for release.sh — so a strict Usage-only reading reports phantoms
# that are not phantoms, and a whole-row reading reports none of them. Since
# the promise direction is already judged above, the only question left for
# this loop is the one a whole-row read answers exactly: does the manual
# mention this flag ANYWHERE. It cannot false-positive; it can only miss a flag
# mentioned in passing but absent from Usage, and #1263 owns that gap.
#
# Lifted into a function so it can be pointed at a scratch tree (#1265). The
# whole audit used to be one straight run over `tools/`, which meant the only
# way to watch a check here fail was to break a real tool — so nothing ever
# watched, and this file audits eleven programs for exactly that omission
# while committing it. `--selftest` calls this against fixtures; the ordinary
# run calls it against the shop.
# THE VERDICT CHAIN, AS A FUNCTION (#1358). Twelve counters increment `BREAKS`
# and the chain named nine of them plus a terminal `else`. `flags_phantom` and
# `charset_drift` were in neither, so a break from either fell through to
# `A_CATALOG_ROW_PROMISES_WHAT_THE_TOOL_LACKS catalog_wrong=0` — a defect report
# naming the wrong defect, which is the #1170 shape this file cites three lines
# from the branch that did it.
#
# Every unit that adds a counter is supposed to add a word, and the two that did
# not were both added by units about something else: #1263 was about the
# extraction boundary, #1207 about connecting two checks. The chain is edited by
# units that are ABOUT verdicts, and nothing checked the rest.
#
# It is a function now because it is a PURE one — no files, no network, no
# subprocesses, only integers — so the suite can drive every branch over
# synthetic counts. That is the only part of this tool a real defect had to
# arrive to exercise.
#
# It reads the counters as globals rather than taking twelve arguments: twelve
# positional parameters is a transposition waiting to happen, which is #1204's
# argument one directory over, and the suite sets the same names.
BREAK_COUNTERS='rows_duplicated missing codes_undocumented codes_unnamed unrun unfalsifiable codes_unspent flags_undocumented codes_redefined uncatalogued flags_phantom charset_drift catalog_wrong'

verdict_word() {                # reads BREAKS and $BREAK_COUNTERS; prints one ADVICE VERDICT line
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
  elif [ "$codes_unnamed" -gt 0 ]; then
    # A ninth word (#1238), and deliberately NOT the fifth one above. That verdict
    # is for a row with no exit vocabulary at all — the reader finds nothing and
    # knows it. This one is for a row that HAS a numbered list and is missing an
    # entry from it, which reads as complete and is not: the reader stops looking
    # precisely because they found an answer. Sending them to the fifth word would
    # send them to a row that documents its codes.
    echo "ADVICE VERDICT A_ROW_NAMES_SOME_OF_A_TOOLS_CODES unnamed=$codes_unnamed"
  elif [ "$unrun" -gt 0 ]; then
    # A fourth failure and a fourth word (#1212). A suite the lane never executes
    # is not a missing flag, not a missing catalog row and not a lying one: the
    # tool is right, the document is right, and the falsification is simply never
    # performed. Naming it as any of the other three sends the reader to a file
    # where nothing is wrong.
    echo "ADVICE VERDICT A_SUITE_NOBODY_RUNS unrun=$unrun"
  elif [ "$unfalsifiable" -gt 0 ]; then
    # An eighth word (#1311). Not a wrong promise, not a missing row, not an
    # unrun suite — there is NO suite, so the tool's advice has nowhere to be
    # executed and every other check in this file is reading prose about a
    # program nobody can watch fail. Sending the reader to any of the other
    # seven would send them to a file where nothing is wrong.
    echo "ADVICE VERDICT A_TOOL_NOBODY_CAN_FALSIFY unfalsifiable=$unfalsifiable"
  elif [ "$codes_unspent" -gt 0 ]; then
    # A seventh word (#1276). The mirror of A_TOOL_SPENDS_CODES_ITS_ROW_DOES_NOT_NAME,
    # and a different defect again: the row promises a code the tool cannot give.
    # A caller branching on it waits for an answer that never comes, which reads
    # as the tool hanging rather than as the document being wrong.
    echo "ADVICE VERDICT A_ROW_PROMISES_A_CODE_THE_TOOL_NEVER_SPENDS unspent=$codes_unspent"
  elif [ "$flags_undocumented" -gt 0 ]; then
    # A sixth word (#1033). The mirror of ADVISES_A_FLAG_NOBODY_IMPLEMENTS, and a
    # different defect: there is no broken promise here, no wrong row and no
    # missing row — the tool works, the manual is merely silent about part of it.
    # Sending the reader to "a flag nobody implements" would send them looking for
    # a flag that exists and works. Invisible capability is the quieter failure and
    # the longer-lived one: nobody types a flag they have never read about.
    echo "ADVICE VERDICT A_TOOL_PARSES_A_FLAG_ITS_ROW_HIDES undocumented=$flags_undocumented"
  elif [ "$codes_redefined" -gt 0 ]; then
    # A row redefining a UNIVERSAL code is not a wrong promise about one tool — it
    # breaks a reading every caller in the tree relies on, and the reader has to be
    # sent to the exit-grammar table rather than to the row (#1241).
    echo "ADVICE VERDICT A_ROW_REDEFINES_A_UNIVERSAL_CODE redefined=$codes_redefined"
  elif [ "$rows_duplicated" -gt 0 ]; then
  # A twelfth word (#1340). Not a missing row and not a wrong one: there are TWO,
  # and every check in this file silently reads their concatenation. Sending the
  # reader to A_TOOL_NOBODY_CAN_FIND would send them looking for an absence.
  echo "ADVICE VERDICT A_TOOL_WEARS_TWO_ROWS duplicated=$rows_duplicated"
elif [ "$uncatalogued" -gt 0 ]; then
    # Three failures, three words. A catalog gap reported as "advises a flag nobody
    # implements" sends the reader to the wrong file, which is the same class of error as a
    # defect report that names the wrong defect (#1170) — and a catalog row that promises a
    # flag is a third thing again: the tool is fine, the document is lying about it.
    echo "ADVICE VERDICT A_TOOL_NOBODY_CAN_FIND uncatalogued=$uncatalogued"
  elif [ "$flags_phantom" -gt 0 ]; then
    # A tenth word (#1358). The mirror of A_TOOL_PARSES_A_FLAG_ITS_ROW_HIDES, and
    # it had no branch at all until now: a phantom fell through to the catalog
    # word because the same row usually trips `catalog_wrong` too, so the count
    # was nonzero and the wrong verdict read as plausible. That is worse than an
    # obviously wrong one — nothing in the output says the word is wrong.
    # Before `catalog_wrong` deliberately: when both fire it is one row and the
    # phantom is the specific diagnosis, so it is the one to send the reader to.
    echo "ADVICE VERDICT A_ROW_ADVERTISES_A_FLAG_THE_TOOL_REFUSES phantom=$flags_phantom"
  elif [ "$charset_drift" -gt 0 ]; then
    # An eleventh word (#1358), and the one that motivated reading the chain: a
    # charset drift co-occurs with NOTHING, so alone it printed
    # `A_CATALOG_ROW_PROMISES_WHAT_THE_TOOL_LACKS catalog_wrong=0` — a defect
    # report naming a defect that did not happen, with a zero beside it. Nobody
    # had seen it because `charset_checked=0`: nine of twelve tools print no byte
    # above 0x7f in their suites, so the comparison has never had anything to
    # compare. An unreachable branch is where a wrong one hides longest.
    echo "ADVICE VERDICT A_TOOL_ANSWERS_DIFFERENTLY_IN_ANOTHER_LOCALE drift=$charset_drift"
  elif [ "$catalog_wrong" -gt 0 ]; then
    # Was the terminal `else` and is now a branch of its own. As a fallback it
    # answered for every counter nobody had written a word for, which is how two
    # of twelve came to be named by it.
    echo "ADVICE VERDICT A_CATALOG_ROW_PROMISES_WHAT_THE_TOOL_LACKS catalog_wrong=$catalog_wrong"
  else
    # THE BRANCH THAT SHOULD NEVER PRINT (#1358). `BREAKS` moved and no counter
    # the chain reads is nonzero, which means a unit added a counter and no word
    # — the defect this whole reordering exists to end. Naming any real defect
    # here would be a guess, and a guess is what the two branches above were.
    echo "ADVICE VERDICT A_BREAK_WITH_NO_WORD breaks=$BREAKS  (a counter moved and nothing in the chain names it — add the word beside the counter)"
  fi
}

unnamed_codes() {          # unnamed_codes <script-file> <row> -> the codes it spends and the row omits
  local spends promised code out=''
  spends="$(grep -vE '^[[:space:]]*#' "$1" | grep -oE '(^|[^_a-zA-Z])exit [0-9]+' \
            | grep -oE '[0-9]+' | sort -un | tr '\n' ' ')"
  promised=" $(printf '%s' "$2" | grep -oE 'Exit [^|]*' \
               | grep -oE '(^Exit |· )[0-9]+' | grep -oE '[0-9]+' | sort -un | tr '\n' ' ')"
  for code in $spends; do
    # 0 again: falling off the end spends it, so its absence proves nothing.
    [ "$code" = 0 ] && continue
    case "$promised " in *" $code "*) ;; *) out="$out $code" ;; esac
  done
  printf '%s' "${out# }"
}


flag_audit() {                                   # flag_audit <tools-dir> <catalog>
  local dir="$1" catalog="$2" tool name row body arms compares accepted advertised flag
  flags_parsed=0
  flags_undocumented=0
  flags_phantom=0
  tools_no_flags=0
  for tool in "$dir"/*.sh; do
  [ -f "$tool" ] || continue
  name="$(basename "$tool")"
  row="$(grep "^| \`$name\`" "$catalog" || true)"
  [ -n "$row" ] || continue                      # uncatalogued, counted above
  body="$(grep -vE '^[[:space:]]*#' "$tool" || true)"
  arms="$(printf '%s\n' "$body" \
          | grep -oE '^[[:space:]]*--[a-z0-9-]+(\|--[a-z0-9-]+)*\)' | tr -d ' )' | tr '|' '\n' || true)"
  compares="$(printf '%s\n' "$body" \
          | grep -oE '=[[:space:]]*"?--[a-z0-9-]+' | grep -oE -- '--[a-z0-9-]+' || true)"
  accepted="$(printf '%s\n%s\n' "$arms" "$compares" | grep -E '^--' | sort -u || true)"
  if [ -z "$accepted" ]; then
    # Reported rather than skipped in silence: a sweep that read no flags at
    # all prints the same green line as one that read forty, and this tree has
    # met that shape in charset_checked=0 (#1207) and INSTRUMENTS_UNPROVEN
    # (#970). `issuetree.sh` takes positional arguments only and is the honest
    # zero.
    tools_no_flags=$((tools_no_flags + 1))
    echo "NO_FLAGS $tool parses no long options — positional arguments only"
    continue
  fi
  # Padded with spaces and matched by `case`, never `printf | grep -q`: -q exits
  # at the first match, the printf takes SIGPIPE, and on ubuntu-latest that
  # prints `write error: Broken pipe` and takes the exit code with it. The
  # padding is what keeps `--for` from matching inside `--format`.
  advertised=" $(printf '%s' "$row" | grep -oE -- '--[a-z][a-z0-9-]*' | sort -u | tr '\n' ' ') "
  while IFS= read -r flag; do
    [ -z "$flag" ] && continue
    flags_parsed=$((flags_parsed + 1))
    case "$advertised" in
      *" $flag "*) ;;
      *)
        flags_undocumented=$((flags_undocumented + 1))
        BREAKS=$((BREAKS + 1))
        echo "UNDOCUMENTED $tool parses '$flag' and its catalog row never names it"
        ;;
    esac
  done <<< "$accepted"

  # THE PHANTOM DIRECTION, now that the row has a boundary (#1263).
  #
  # The check above reads the WHOLE row, deliberately: rows legitimately name
  # other programs' flags (`git commit --amend`, `balance.sh --rulercheck`
  # inside release.sh's row), so a whole-row read cannot false-positive in the
  # undocumented direction and cannot be used in this one.
  #
  # The `Usage:` clause CAN, once it is bounded. It runs from `Usage: ` to the
  # first `**` or ` Exit N`, whichever comes first — the two things that end it
  # in every row here. That bound is what #1263 was opened for: three earlier
  # extraction rules each traded one error for the other, because `Usage:` is
  # prose inside a paragraph inside a table cell with the exit grammar and a
  # bold aside sharing the sentence run.
  #
  # Zero phantoms the day this landed, which is the same reading SheetFence's
  # first green run had: the law was true and nothing was keeping it.
  usage="$(printf '%s' "$row" | sed -n 's/.*Usage: //p' | sed -e 's/ \*\*.*//' -e 's/ Exit [0-9].*//')"
  [ -n "$usage" ] || continue
  while IFS= read -r flag; do
    [ -z "$flag" ] && continue
    case " $(printf '%s\n' "$accepted" | tr '\n' ' ') " in
      *" $flag "*) ;;
      *)
        flags_phantom=$((flags_phantom + 1))
        BREAKS=$((BREAKS + 1))
        echo "PHANTOM $tool advertises '$flag' in its Usage clause and parses no such flag"
        ;;
    esac
  done <<< "$(printf '%s' "$usage" | grep -oE -- '--[a-z][a-z0-9-]*' | sort -u || true)"
  done
}

# ---------------------------------------------------------------- selftest
#
# #1265: this file has spent its whole life auditing whether OTHER tools can
# execute their own advice, printing `UNFALSIFIABLE <tool> has no --selftest`
# and counting it, while having no way to be watched failing itself. Its own
# checks were falsified by transcripts pasted into pull requests — real
# evidence, and evidence that ages: the next edit to an extraction pattern had
# nothing to run.
#
# The cases are built rather than mocked. Each writes a two-line scratch tool
# and a one-row scratch catalog into a temp directory and points `flag_audit`
# at them, so what is exercised is the shipped function and not a copy of its
# logic — the second copy being the thing this file exists to hunt.
selftest() {
  local pass=0 fail=0
  # NOT local: the EXIT trap fires after this function returns, and under
  # `set -u` a local that has gone out of scope is an unbound variable — the
  # cleanup then dies with `tmp: unbound variable` after a green suite, which
  # is a passing run that prints an error and leaves a directory behind.
  tmp="$(mktemp -d "${TMPDIR:-/tmp}/advice.XXXXXX")"
  trap 'rm -rf "$tmp"' EXIT

  case_() {                     # case_ <name> <want-undocumented> <tool-body> <row>
    local name="$1" want="$2" toolbody="$3" row="$4" got
    rm -rf "$tmp/shop"; mkdir -p "$tmp/shop"
    printf '%s\n' "$toolbody" > "$tmp/shop/fixture.sh"
    printf '%s\n' "$row" > "$tmp/shop/README.md"
    BREAKS=0
    flag_audit "$tmp/shop" "$tmp/shop/README.md" >/dev/null 2>&1
    got="$flags_undocumented"
    if [ "$want" = "$got" ]; then
      pass=$((pass + 1)); printf 'ADVICE case=%-26s want=%s got=%s OK\n' "$name" "$want" "$got"
    else
      fail=$((fail + 1)); printf 'ADVICE case=%-26s want=%s got=%s BROKEN\n' "$name" "$want" "$got"
    fi
  }

  no_flags_case() {             # no_flags_case <name> <want-no-flags> <tool-body> <row>
    local name="$1" want="$2" toolbody="$3" row="$4" got
    rm -rf "$tmp/shop"; mkdir -p "$tmp/shop"
    printf '%s\n' "$toolbody" > "$tmp/shop/fixture.sh"
    printf '%s\n' "$row" > "$tmp/shop/README.md"
    BREAKS=0
    flag_audit "$tmp/shop" "$tmp/shop/README.md" >/dev/null 2>&1
    got="$tools_no_flags"
    if [ "$want" = "$got" ]; then
      pass=$((pass + 1)); printf 'ADVICE case=%-26s want=%s got=%s OK\n' "$name" "$want" "$got"
    else
      fail=$((fail + 1)); printf 'ADVICE case=%-26s want=%s got=%s BROKEN\n' "$name" "$want" "$got"
    fi
  }

  local ROW_WITH='| `fixture.sh` | does a thing. Usage: `tools/fixture.sh --pr N`. |'
  local ROW_WITHOUT='| `fixture.sh` | does a thing and says nothing about how. |'

  # A `case` arm, the spelling most tools here use.
  case_ case-arm-documented    0 '  --pr) PR=1 ;;' "$ROW_WITH"
  case_ case-arm-hidden        1 '  --pr) PR=1 ;;' "$ROW_WITHOUT"

  # A direct comparison, the spelling litany.sh and baseline.sh use. The first
  # draft of #1033 read only `case` arms and would have passed both of these.
  #
  # The flag is ASSEMBLED rather than written, and that is not fussiness. The
  # comparison form is `= "--pr"`, which is exactly what the extractor greps
  # for — so spelled out here it would be a literal inside the file the
  # ordinary audit reads, and `advice.sh` would report ITSELF as parsing a
  # flag its row hides. It did, on the first run of this suite. Same shape as
  # litany.sh's assembled ghost verdict, and the fifth time a checker in this
  # tree has found its own test data.
  local dash="--" pr; pr="${dash}pr"
  case_ comparison-documented  0 "[ \"\${1:-}\" = \"$pr\" ] && PR=1" "$ROW_WITH"
  case_ comparison-hidden      1 "[ \"\${1:-}\" = \"$pr\" ] && PR=1" "$ROW_WITHOUT"

  # An alternation arm: both flags are parsed, one is advertised.
  case_ alternation-half-hidden 1 '  --pr|--sha) X=1 ;;' "$ROW_WITH"

  # A flag named only in a COMMENT is not parsed, and must not be counted as
  # either kind — the self-matching shape this file has been bitten by four
  # times, in the one direction that would produce a false accusation.
  case_ comment-only-flag      0 '# --pr is discussed here and parsed nowhere' "$ROW_WITHOUT"

  # Substring safety: --for must not be satisfied by --format on the row.
  case_ prefix-not-a-match     1 '  --for) X=1 ;;' \
        '| `fixture.sh` | takes `--format` and nothing else. |'

  # An uncatalogued tool is skipped here rather than double-reported: the
  # UNCATALOGUED check above owns that defect and this loop would name the
  # same file for a second reason.
  case_ uncatalogued-skipped   0 '  --pr) PR=1 ;;' '| `other.sh` | not our fixture. |'

  # Positional-only tools are reported, not silently passed (#1207's shape).
  # The phantom direction (#1263): a Usage clause naming a flag the tool refuses.
  phantom_case() {                # phantom_case <name> <want> <tool-body> <row>
    local name="$1" want="$2" toolbody="$3" row="$4" got
    rm -rf "$tmp/shop"; mkdir -p "$tmp/shop"
    printf '%s\n' "$toolbody" > "$tmp/shop/fixture.sh"
    printf '%s\n' "$row" > "$tmp/shop/README.md"
    BREAKS=0
    flag_audit "$tmp/shop" "$tmp/shop/README.md" >/dev/null 2>&1
    got="$flags_phantom"
    if [ "$want" = "$got" ]; then
      pass=$((pass + 1)); printf 'ADVICE case=%-26s want=%s got=%s OK\n' "$name" "$want" "$got"
    else
      fail=$((fail + 1)); printf 'ADVICE case=%-26s want=%s got=%s BROKEN\n' "$name" "$want" "$got"
    fi
  }

  phantom_case usage-flag-parsed    0 "  $pr) PR=1 ;;" "$ROW_WITH"
  phantom_case usage-flag-refused   1 '  --other) X=1 ;;' "$ROW_WITH"
  # The bound is the point: a flag named AFTER the Usage clause — in the exit
  # grammar, or in a bold aside about another program — is not a promise about
  # this tool, and an unbounded read reported those as phantoms. release.sh's
  # row is the live example: it names balance.sh --rulercheck while explaining
  # which locks a cut skips.
  phantom_case flag-past-the-bound  0 '  --pr) X=1 ;;' \
        '| `fixture.sh` | does a thing. Usage: `tools/fixture.sh --pr N`. **Note:** unlike `other.sh --elsewhere`. Exit 0 fine · 2 refused. |'

  no_flags_case positional-only 1 'echo "$1"' "$ROW_WITHOUT"
  no_flags_case has-flags       0 '  --pr) PR=1 ;;' "$ROW_WITH"

  # The exit-code join (#1238). The live rows falsify it too — it found two real
  # ones the day it was written — but the tree will stop supplying examples the
  # moment they are fixed, and a check with no case left is a check nobody can
  # break on purpose.
  # The fixture is SYNTHESISED from a list of codes rather than written out, and
  # the reason is the ninth instance of the oldest bug in this file: a program
  # hunting `exit N` cannot contain `exit N`. The first draft of these cases
  # spelled their fixtures literally, and the live audit immediately reported
  # `advice.sh spends exit 3 and its row does not name it` — reading its own
  # test data as its own behaviour. `printf 'exit %s\n'` carries no digit, so
  # the format string is invisible to the grep it feeds.
  ROW_CODES='| `fixture.sh` | does a thing. Exit 0 fine · 2 refused. |'
  #
  # The parameters are positional and NOT flags, which is the same finding one
  # audit over: a `--row)` arm written here was immediately reported as
  # `A_TOOL_PARSES_A_FLAG_ITS_ROW_HIDES undocumented=1`, because the flag audit
  # reads every `--x)` in the file and cannot tell a tool's door from a suite's
  # helper. That is a real limit of the flag audit and it has its own issue; here
  # it is simply avoided.
  codes_case() {                  # codes_case <name> <want> <codes> [<prefix-line>] [<row>]
    local name="$1" want="$2" codes="$3" prefix="${4:-}" row="${5:-$ROW_CODES}" got
    : > "$tmp/codes.sh"
    [ -n "$prefix" ] && printf '%s\n' "$prefix" >> "$tmp/codes.sh"
    # shellcheck disable=SC2086
    [ -n "$codes" ] && printf 'exit %s\n' $codes >> "$tmp/codes.sh"
    got="$(unnamed_codes "$tmp/codes.sh" "$row")"
    if [ "$want" = "$got" ]; then
      pass=$((pass + 1)); printf 'ADVICE case=%-26s want=[%s] got=[%s] OK\n' "$name" "$want" "$got"
    else
      fail=$((fail + 1)); printf 'ADVICE case=%-26s want=[%s] got=[%s] BROKEN\n' "$name" "$want" "$got"
    fi
  }
  codes_case all-named             ''    '2'
  codes_case one-missing           '1'   '1 2'
  codes_case two-missing           '1 3' '1 3'
  # 0 is unjudgeable in this direction: a script that never writes it still
  # spends it by falling off the end, so its absence from a row proves nothing.
  codes_case zero-never-counted    ''    '0'
  # A `return` is not an exit. This is the case that reported `backlog.sh`'s
  # `measured_body()` predicate as a third defect until the grep was narrowed —
  # `*) return 1 ;;` there means FALSE and never reaches `$?` of the process.
  codes_case return-is-not-exit    ''    '2' 'f() { return 1; }'
  # A comment quoting a code is prose, not behaviour.
  codes_case commented-exit        ''    '2' "# a broken suite leaves $(printf 'exit %s' 1)"
  # `exit_status=1` is an assignment, and the space after the word is what keeps
  # it out — the same boundary that keeps `EXIT_REFUSED=2` out.
  codes_case assignment-not-exit   ''    '2' 'exit_status=1'
  # Numbers OUTSIDE the `Exit …` clause are not promises: a row saying "takes 1
  # argument" does not document code 1.
  codes_case number-outside-clause '1'   '1 2' '' \
        '| `fixture.sh` | takes 1 argument. Exit 0 fine · 2 refused. |'

  # THE VERDICT CHAIN (#1358). Twelve counters break and nine had words. The two
  # without them had never been executed by anything but a real defect, and one
  # of the two is unreachable today — `charset_checked=0`, so the comparison has
  # never had anything to compare, and an unreachable branch is where a wrong one
  # hides longest. Driven here over synthetic counts, which is possible at all
  # because the chain is a pure function of integers.
  verdict_case() {                # verdict_case <counter|-> <expected word>
    local set="$1" want="$2" got c
    BREAKS=0
    for c in $BREAK_COUNTERS; do eval "$c=0"; done
    if [ "$set" != '-' ]; then eval "$set=1"; BREAKS=1; fi
    got="$(verdict_word | sed -E 's/^ADVICE VERDICT ([A-Z_]+).*/\1/')"
    if [ "$want" = "$got" ]; then
      pass=$((pass + 1)); printf 'ADVICE case=%-26s want=%s got=%s OK\n' "verdict:$set" "$want" "$got"
    else
      fail=$((fail + 1)); printf 'ADVICE case=%-26s want=%s got=%s BROKEN\n' "verdict:$set" "$want" "$got"
    fi
  }
  verdict_case -                  EVERY_FLAG_ADVISED_EXISTS
  verdict_case missing            ADVISES_A_FLAG_NOBODY_IMPLEMENTS
  verdict_case codes_undocumented A_TOOL_SPENDS_CODES_ITS_ROW_DOES_NOT_NAME
  verdict_case codes_unnamed      A_ROW_NAMES_SOME_OF_A_TOOLS_CODES
  verdict_case unrun              A_SUITE_NOBODY_RUNS
  verdict_case unfalsifiable      A_TOOL_NOBODY_CAN_FALSIFY
  verdict_case codes_unspent      A_ROW_PROMISES_A_CODE_THE_TOOL_NEVER_SPENDS
  verdict_case flags_undocumented A_TOOL_PARSES_A_FLAG_ITS_ROW_HIDES
  verdict_case codes_redefined    A_ROW_REDEFINES_A_UNIVERSAL_CODE
  verdict_case uncatalogued       A_TOOL_NOBODY_CAN_FIND
  verdict_case rows_duplicated    A_TOOL_WEARS_TWO_ROWS
  verdict_case flags_phantom      A_ROW_ADVERTISES_A_FLAG_THE_TOOL_REFUSES
  verdict_case charset_drift      A_TOOL_ANSWERS_DIFFERENTLY_IN_ANOTHER_LOCALE
  verdict_case catalog_wrong      A_CATALOG_ROW_PROMISES_WHAT_THE_TOOL_LACKS
  # The case that is the whole point: `BREAKS` moved and no counter did, which is
  # what a thirteenth counter with no word looks like. It must SAY so rather than
  # blame the catalog — the state the two missing branches were silently in.
  BREAKS=1
  for c in $BREAK_COUNTERS; do eval "$c=0"; done
  got="$(verdict_word | sed -E 's/^ADVICE VERDICT ([A-Z_]+).*/\1/')"
  if [ "$got" = A_BREAK_WITH_NO_WORD ]; then
    pass=$((pass + 1)); printf 'ADVICE case=%-26s want=%s got=%s OK\n' "verdict:orphan-break" A_BREAK_WITH_NO_WORD "$got"
  else
    fail=$((fail + 1)); printf 'ADVICE case=%-26s want=%s got=%s BROKEN\n' "verdict:orphan-break" A_BREAK_WITH_NO_WORD "$got"
  fi
  # Every counter that breaks must be in the list the cases above walk. A unit
  # adding a thirteenth counter and no word is caught by the orphan case only if
  # the counter is in `BREAK_COUNTERS`; this compares the list against the file.
  spent="$(grep -n -B1 'BREAKS + 1' "$0" | grep -oE '^[0-9]+-[[:space:]]*[a-z_]+=' \
           | grep -oE '[a-z_]+=' | tr -d '=' | sort -u)"
  listed="$(printf '%s' "$BREAK_COUNTERS" | tr ' ' '\n' | sort -u)"
  orphans="$(comm -23 <(printf '%s\n' "$spent") <(printf '%s\n' "$listed") | grep -c . || true)"
  if [ "$orphans" = 0 ]; then
    pass=$((pass + 1)); printf 'ADVICE case=%-26s want=0 got=%s OK\n' "verdict:every-counter-listed" "$orphans"
  else
    fail=$((fail + 1)); printf 'ADVICE case=%-26s want=0 got=%s BROKEN\n' "verdict:every-counter-listed" "$orphans"
    comm -23 <(printf '%s\n' "$spent") <(printf '%s\n' "$listed") | sed 's/^/  COUNTER_WITH_NO_WORD /'
  fi

  echo "ADVICE SELFTEST VERDICT $([ "$fail" -eq 0 ] && echo PASS || echo FAIL) cases=$((pass + fail)) failed=$fail"
  [ "$fail" -eq 0 ]
}

if [ "$SELFTEST" = yes ]; then
  exec 1>&3            # the shop's audit is done; the suite speaks for itself
  selftest
  exit $?
fi

flag_audit tools tools/README.md

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
  # `exit "$(…)"` AND `return "$(…)"`. The first draft looked only for `exit`,
  # and read 0 while `prstate.sh` — the tool that motivated the counter — sits
  # one line away spelling it `return "$(code_for "$verdict")"`. A counter that
  # cannot move is what #1205 deleted from `SheetDump` four units ago, so this
  # one is falsified rather than assumed: it must report `prstate.sh` today.
  # Comments stripped first. The paragraph above QUOTES the shape it hunts, so
  # the unfiltered grep reported this file as indirect on the strength of its own
  # explanation — a checker inside its own search path, for the fifth time in
  # this file's history (#1144's ghost verdict, #1157's comments, #1222's row
  # lookup reading a neighbour, and now this).
  if grep -qE '(exit|return) +"?\$\(' <<< "$(uncommented "$tool")"; then
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
# The universal codes mean one thing each, and a row may not redefine them
# (#1241). `2` is the largest agreement in this tree — ten rows spend it for a
# refused invocation — and the value of an agreement is that a caller can branch
# on it without knowing which program answered. A row spending 2 for something
# else silently ends that.
#
# Only 2 is checked. 0 and 1 are held-and-broke everywhere and phrased twenty
# ways ("green", "CURRENT", "ARGUED or NONE"), so demanding one wording would
# reject correct rows; 3 and up are local by the table's own rule. 2 is the one
# code that is both universal AND phrased identically in every row that has it,
# which is what makes it checkable rather than a style opinion.
# The OTHER direction: a row promising a code the tool never spends (#1276).
#
# Everything above asks whether a spent code is documented. Nothing asked
# whether a documented code is spent — the same asymmetry #1033 found in the
# flag audit, one column over, and it was hiding a real defect: `issuetree.sh`'s
# row promised `1 the invocation was refused` and the tool contained no `exit 1`
# at all. The 1 came from bash's `${1:?usage}`, which is the code for A CLAIM
# THAT DOES NOT HOLD, so a missing argument was reported the way a broken
# contract is, and the row wrote that down as though it were the rule.
#
# Literal exits only, same as the census above, and `codes_indirect` is the
# escape hatch for a tool that reaches its codes through a lookup — `prstate.sh`
# does, so its promises cannot be checked this way and it is exempted by the
# same flag rather than reported as a liar.
codes_unspent=0
for tool in tools/*.sh; do
  name="$(basename "$tool")"
  row="$(grep "^| \`$name\`" tools/README.md || true)"
  [ -n "$row" ] || continue
  case "$row" in *"Exit "*) ;; *) continue ;; esac
  # `exit` ONLY, and that is a correction (#1238). The first draft read
  # `(exit|return)`, which conflates two unrelated things: a process's exit code
  # and a shell function's boolean. `backlog.sh`'s `measured_body()` ends
  # `*) return 1 ;;` to mean FALSE — the predicate the whole tool is built on —
  # and that 1 never reaches `$?` of the process. Counting it inflated `spends`,
  # and an inflated `spends` HIDES unspent findings rather than inventing them,
  # so the defect was silent in both directions of the audit.
  spends="$(grep -vE '^[[:space:]]*#' "$tool" \
            | grep -oE '(^|[^_a-zA-Z])exit [0-9]+' | grep -oE '[0-9]+' | sort -u | tr '\n' ' ')"
  # A tool whose codes are all indirect has nothing to compare against.
  [ -n "$spends" ] || continue
  # `exit "$(code_for …)"` is a lookup; `exit $?` is a PASS-THROUGH — the code
  # is whatever the last command produced, so the tool genuinely spends
  # everything that command can. Both are invisible to a literal grep, and both
  # make a promise uncheckable rather than false. `advice.sh` itself is the
  # second shape: `selftest; exit $?` spends 1 on a failing suite, and the first
  # draft of this check reported its own row as a liar.
  grep -qE '(exit|return) +("?\$\(|\$\?)' <<< "$(uncommented "$tool")" && continue
  promised="$(printf '%s' "$row" | grep -oE 'Exit [^|]*' \
              | grep -oE '(^Exit |· )[0-9]+' | grep -oE '[0-9]+' | sort -u)"
  while IFS= read -r code; do
    [ -z "$code" ] && continue
    # 0 is spent by falling off the end of a script as often as by `exit 0`,
    # so a row promising it proves nothing either way.
    [ "$code" = 0 ] && continue
    case " $spends " in
      *" $code "*) ;;
      *)
        codes_unspent=$((codes_unspent + 1))
        BREAKS=$((BREAKS + 1))
        echo "CODES_UNSPENT $name promises exit $code and never spends it, literals=[$spends]"
        ;;
    esac
  done <<< "$promised"
done

# The half #1222 asked for and did not implement, three units later (#1238).
#
# `codes_undocumented` above asks whether the row says ANYTHING about exits; the
# literal string `Exit ` satisfies it. A row reading `Exit codes vary.` passes,
# and so does one naming 0–3 for a tool that spends 0–5. That is a documentation
# floor, not a join, and #1222's own PR body said the join was missing — which
# left the other half of a merged rule living in prose on a closed pull request.
#
# This is the join, in the direction the floor cannot see: every code the tool
# spends must appear in its row. It found two, and both are the same shape — a
# `--selftest` that leaves 1 when a case fails, in a row that documents only the
# door's refusals. A crew member wiring either suite into a lane reads the row,
# sees 0 and 2, and has no written reason to expect the code the lane will
# actually branch on.
#
# Why this direction may run on tools the OTHER direction exempts: a lookup or a
# `$?` pass-through makes the literal grep UNDERCOUNT what a tool spends.
# Undercounting turns a promise-check into a liar (`prstate.sh` promises 4, 5, 6
# through `code_for` and no literal shows them), so `codes_unspent` must exempt
# them — but it can only ever cost this direction a finding it would have made.
# The asymmetry is the reason the two loops do not share a guard.
codes_unnamed=0
codes_returns=0
for tool in tools/*.sh; do
  name="$(basename "$tool")"
  row="$(grep "^| \`$name\`" tools/README.md || true)"
  [ -n "$row" ] || continue
  case "$row" in *"Exit "*) ;; *) continue ;; esac
  # Reported, never judged. A `return N` is a predicate's answer to its caller
  # UNLESS the function is the script's last statement, in which case it is the
  # process's code — and telling those apart means running the script in your
  # head, which is the interpretation #1238 asked to stay out. Both shapes live
  # here: `backlog.sh` returns 1 for false, `prstate.sh`'s `report()` returns 3
  # for a pull request it could not read.
  grep -qE '(^|[^_a-zA-Z])return [0-9]+' <<< "$(uncommented "$tool")" \
    && codes_returns=$((codes_returns + 1))
  for code in $(unnamed_codes "$tool" "$row"); do
    codes_unnamed=$((codes_unnamed + 1))
    BREAKS=$((BREAKS + 1))
    echo "CODES_UNNAMED $name spends exit $code and its row does not name it"
  done
done

codes_redefined=0
while IFS= read -r row; do
  [ -z "$row" ] && continue
  case "$row" in
    *"· 2 the invocation was refused"*) ;;
    *)
      codes_redefined=$((codes_redefined + 1))
      BREAKS=$((BREAKS + 1))
      echo "CODE_REDEFINED $(printf '%s' "$row" | grep -oE '^\| `[a-z-]+\.sh`') spends 2 for something other than a refused invocation"
      ;;
  esac
done <<< "$(grep -E '^\| `[a-z-]+\.sh`.*· 2 ' tools/README.md || true)"

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
  # DOES THIS TOOL PROMISE A SUITE, or does it merely MENTION somebody else's
  # flag (#1347). A whole-file grep answers the second question with the first
  # question's confidence: `release.sh` contains `--selftest` because lock 2 of
  # a release runs `java -cp out matrix.Main --selftest`, the DAEMON's flag,
  # quoted in the code that runs it. So the tool was asked to produce a suite,
  # refused the flag through its own positional check, and was counted as a tool
  # whose `--selftest` reaches no suite. Permanently. `no_suite=1` has read 1 all
  # day and means nothing.
  #
  # #1212 fixed the other half of this same mistake — the same grep once counted
  # `advice.sh` and `release.sh` as HAVING suites they do not have — by moving
  # the discriminator to the verdict line. That repaired the false GREEN and left
  # the false ENTRY into the loop untouched: one side of a two-sided error.
  #
  # The answer is one function over. `named_tool()` already carries a list of
  # external programs so that advice about somebody else's flags is not read as
  # advice about this tool, and `release.sh`'s occurrence sits inside
  # `java -cp out matrix.Main --selftest`, which that list catches. Same rule,
  # same list, second reader.
  # Two stages, both off a capture. The empty guard is not decoration: with no
  # mentions at all the old pipeline fed `grep -qv` no input and exited 1, so the
  # `|| continue` fired; a here-string of an empty variable is ONE EMPTY LINE,
  # which does not match the external-program pattern, so `-v` matches it and the
  # tool would be admitted. Replacing a pipe with a capture changes what "no
  # input" means, and this is the one site where that difference is a verdict.
  mentions="$(grep -E '\-\-selftest\b' <<< "$(uncommented "$tool")" || true)"
  [ -n "$mentions" ] || continue
  grep -qvE '\b(gh|git|java|javac|bash|sh|curl)\b[^|;&]*--selftest' <<< "$mentions" || continue
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
  if ! grep -qE '^([A-Z][A-Z0-9_]* )+SELFTEST VERDICT ' <<< "$utf8"; then
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
  # THIS HALF COVERS TOOLS ONLY, AND THE OTHER HALF ALREADY EXISTS (#1207).
  #
  # `charset_checked=0` has been the reading since this landed, and #1207 named
  # it correctly — the check is pointed at the population least likely to have
  # the defect, because tools print instrument lines in ASCII by convention
  # while probes print em dashes and box characters. What that issue proposed
  # next, "point the comparison at the probes", is a check the tree already
  # had and nobody had connected to it: `probes/bench.sh --twice` takes its
  # SECOND run under `LC_ALL=C` and byte-compares (#836), which is exactly the
  # move, over exactly that population, and it runs weekly in determinism.yml.
  #
  # So this counter is a genuine zero over a genuine population rather than a
  # gap: the tools have nothing above 0x7f to prove anything about, the probes
  # are covered next door, and neither fact was written where the other could
  # be found. Deleting this half would leave the day a tool DOES print a dash
  # unguarded, which is why #1207 argued against deletion.
  #
  # AND THEY ARE NOT THE SAME CHECK, which is the part a tidy-up would get
  # wrong (#1299). This one compares one tool's --selftest stdout between two
  # LOCALES, one process each. `bench.sh --twice` compares one probe's whole
  # output between two PROCESSES, the second hostile — strictly stronger on
  # the locale axis, and it catches heap addresses, unordered iteration and
  # wall-clock with it.
  #
  # What only THIS half can catch: a tool whose output moves between two runs
  # for a reason that has nothing to do with charset. It captures both runs and
  # compares them, and no other check in this tree runs a tool twice at all —
  # so folding the two together would lose the only double-run any tool gets.
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

echo "ADVICE tools=$(ls tools/*.sh | wc -l | tr -d ' ') uncatalogued=$uncatalogued rows_duplicated=$rows_duplicated catalog_wrong=$catalog_wrong charset_checked=$charset_checked charset_nothing=$charset_nothing suites=$suites no_suite=$no_suite unrun=$unrun codes_undocumented=$codes_undocumented codes_indirect=$codes_indirect codes_redefined=$codes_redefined codes_unspent=$codes_unspent codes_unnamed=$codes_unnamed lines=$found flags_checked=$checked" \
     "unimplemented=$missing unfalsifiable=$unfalsifiable" \
     "flags_parsed=$flags_parsed flags_undocumented=$flags_undocumented flags_phantom=$flags_phantom tools_no_flags=$tools_no_flags"
# The census rule (#1221): `codes_returns` is a description of the tree, not a
# claim whose change is a finding — a tool gaining a helper function that
# returns a boolean moves it, and nothing is wrong. It sits here so the number
# that CANNOT be judged is still visible beside the ones that can.
echo "CODES_CENSUS tools_with_returns=$codes_returns catalog_rows=$(grep -c '^| `[a-z-]*\.sh`' tools/README.md)  (TOOLS carrying at least one, not returns — it sits beside tools= and would read as a statement count otherwise, #1368; a return is a predicate or an exit depending on where the function sits, so it is unjudged on purpose)"
verdict_word
