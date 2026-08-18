#!/usr/bin/env bash
# tools/advice.sh — a tool that tells you what to type owes you a working command (#1095)
#
# Usage: tools/advice.sh            audit every tool's printed advice
#        tools/advice.sh --list     print the advice lines it found, and stop
#        tools/advice.sh --help | -h   print this clause, and stop
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

# DOES THIS FILE PROMISE A SUITE OF ITS OWN? (#1376)
#
# A function rather than three piped lines inside the loop, because this gate
# decides which tools are checked AT ALL and its failure is silent by
# construction: a tool wrongly excluded is not reported as anything — it stops
# appearing in `suites=`, `no_suite=`, `unrun=` and `charset_checked=`, and no
# counter moves. It has been wrong twice, #1212 and #1347, and both times the
# falsification was a transcript in a pull request because there was nothing to
# drive.
#
# Two questions, in order. Does the file mention the flag at all, outside its
# comments — #1157's shape, a checker reading its own explanation. And is every
# mention somebody ELSE's flag: `release.sh` carries `java -cp out matrix.Main
# --selftest` because lock 2 of a release runs the daemon, and `advice.sh` itself
# runs `bash tools/x.sh --selftest` for every tool it audits. The external list is
# `named_tool()`'s, deliberately the same one, so advice about another program's
# flags and a promise of one's own are told apart by a single rule (#1347).
#
# A file that quotes another program's flag AND parses its own is asked, because
# `grep -qv` asks whether ANY mention is the tool's own. Nothing in the tree does
# both today, which is exactly why the case is written: the behaviour was unknown
# rather than correct.
# WHICH LINES ARE THE DOOR AND WHICH BELONG TO A HELPER (#1341).
#
# Bash does not make "which `case` reads $1 at top level" decidable by grep, and a
# checker that guesses is the thing this file exists to refuse — its own words. So
# the rule is stated rather than inferred, and it is a rule about THIS SHOP's
# spelling: a top-level function opens with `name() {` at column zero and closes
# with `}` at column zero. Every tool here is written that way, and an inner
# function (`case_()` inside `selftest()`) is indented, so it neither opens nor
# closes a scope by this reading.
#
# What the rule cannot do: find a door parsed inside a `main()`. No tool here has
# one, the reading would report that tool as having no flags at all, and
# `tools_no_flags=` is loud when it is wrong — which is why that counter is
# reported rather than skipped in silence (#1207). Stating the assumption is the
# option #1341 ranked first; guessing at scope is the one it refuses.
by_scope() {                    # by_scope <file> <door|helper>
  awk -v want="$2" '
    /^[a-zA-Z_][a-zA-Z0-9_]*\(\)[[:space:]]*\{[[:space:]]*$/ { inside = 1; next }
    inside && /^\}/                                          { inside = 0; next }
    (inside ? "helper" : "door") == want                     { print }
  ' "$1"
}

promises_a_suite() {            # promises_a_suite <file> — 0 it promises one, 1 it does not
  local mentions
  mentions="$(grep -E '\-\-selftest\b' <<< "$(uncommented "$1")" || true)"
  [ -n "$mentions" ] || return 1
  grep -qvE '\b(gh|git|java|javac|bash|sh|curl)\b[^|;&]*--selftest' <<< "$mentions"
}

# THE SUITE LOOP'S CLASSIFICATION, AS A FUNCTION OF ONE FILE (#1443).
#
# `tools = suites + no_suite + skipped_self + skipped_no_promise` held BY
# CONSTRUCTION — every path through the loop head incremented exactly one
# counter — and by-construction is not the same as guarded. A fifth exit added
# to the head breaks the sum silently: no verdict word, no counter, and the
# census line simply stops adding up, which is the condition both counters were
# added to make visible. Two reviews asked for this in the same words.
#
# So the head's decision becomes a total function returning ONE WORD from a
# CLOSED SET, and the cases drive every word. A path that classifies as nothing
# returns the empty string and fails the closed-set case; a path that classifies
# as something new fails it too. The remaining two terms (`suites`/`no_suite`)
# are not decidable from the file — they need the tool RUN — so they stay in the
# loop and `admitted` is the word for reaching them.
suite_class() {                 # suite_class <tool> -> self|no_promise|admitted
  [ "$1" = "tools/advice.sh" ] && { printf 'self'; return 0; }
  promises_a_suite "$1" || { printf 'no_promise'; return 0; }
  printf 'admitted'
}

# The literal exit codes a tool spends, space-separated and sorted unique.
# `exit` ONLY, and that is a correction (#1238): the first draft read
# `(exit|return)`, which conflates a process's exit code with a shell function's
# boolean. `backlog.sh`'s `measured_body()` ends `*) return 1 ;;` to mean FALSE
# and that 1 never reaches `$?` of the process. Counting it inflated the answer,
# and an inflated answer HIDES unspent findings rather than inventing them.
spends_of() {                   # spends_of <tool>
  grep -vE '^[[:space:]]*#' "$1" \
    | grep -oE '(^|[^_a-zA-Z])exit [0-9]+' | grep -oE '[0-9]+' | sort -u | tr '\n' ' '
}

# `exit "$(code_for …)"` is a lookup; `exit $?` is a PASS-THROUGH — the code is
# whatever the last command produced, so the tool genuinely spends everything
# that command can. Both are invisible to a literal grep, and both make a
# promise uncheckable rather than false.
passes_through() {              # passes_through <tool> — 0 it computes its code
  grep -qE '(exit|return) +("?\$\(|\$\?)' <<< "$(uncommented "$1")"
}

# THE EXIT-CODE LOOP'S CLASSIFICATION, under the same rule — and it found a real
# hole while being written (#1443). The stated identity was
#
#     tools = codes_checked + codes_exempt + codes_no_promise + codes_no_literal
#
# and there is a FIFTH path: a tool with no catalog row is skipped by
# `[ -n "$row" ] || continue` and counted by nothing here. The sum holds today
# only because `uncatalogued=0` — a different counter, computed in a different
# loop, reported under a different verdict word. An identity that depends on
# another check passing is not an identity, and the tool that lands without a
# row is precisely the tool whose codes nobody is reading.
#
# `no_row` is that path, named. It is a CENSUS field and not a break: the
# population already breaks the build through `uncatalogued`, and a second
# report of one absence is how one defect is counted twice (#1170).
code_class() {                  # code_class <tool> <row> -> no_row|no_promise|no_literal|exempt|checked
  [ -n "$2" ] || { printf 'no_row'; return 0; }
  case "$2" in *"Exit "*) ;; *) printf 'no_promise'; return 0 ;; esac
  [ -n "$(spends_of "$1")" ] || { printf 'no_literal'; return 0; }
  passes_through "$1" && { printf 'exempt'; return 0; }
  printf 'checked'
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
  # READ TO THE END OF THE CLAUSE, not to a line number (#1382, #1520): a door
  # added below it is in `--help` the moment it is in the header.
  -h|--help) awk 'NR==1 {next} !/^#/ {exit} /^#$/ {if (++blank == 2) exit} {print}' "$0"; exit 0 ;;
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
doors_pure=0
doors_shell=0
doors_network=0
refusals_ok=0
refusals_wrong=0
refusals_hung=0
doors_ok=0
doorless=0
doors_wrong=0
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
#
# EVERY NAME HERE IS JUDGED, and #1357 said one of them was not. `catalog_wrong=`
# was the counter it named — the fallback branch of the chain, reachable only when
# nothing else broke — and that was true when the issue was written and stopped
# being true in 4920977, the unit whose title is *two of twelve break counters had
# no verdict word*. It is in this list, it increments BREAKS, it has a word, and
# `verdict_case catalog_wrong` drives it.
#
# ITS ZERO IS EARNED ON THE FIRST OF THE TWO GROUNDS in tools/README.md's
# gate-at-zero rule: it has a history. b898838 drove it red synthetically at
# commissioning — `CATALOG_CLAIM tools/release.sh's row promises
# '--nonexistentflag'` — and its first live run reported a defect that was NOT one,
# release.sh's row naming a neighbour's flags, which is why the body searched is
# every tool the row names rather than only the one it is about. A counter that has
# been both red on purpose and wrong once is not a counter whose zero means nobody
# is looking.
BREAK_COUNTERS='doors_network refusals_wrong refusals_hung doorless doors_wrong rows_duplicated missing codes_undocumented codes_unnamed unrun unfalsifiable codes_unspent flags_undocumented codes_redefined uncatalogued flags_phantom charset_drift catalog_wrong'

verdict_word() {                # reads BREAKS and $BREAK_COUNTERS; prints one ADVICE VERDICT line
  if [ "$BREAKS" -eq 0 ]; then
    echo "ADVICE VERDICT EVERY_FLAG_ADVISED_EXISTS"
  elif [ "$doors_network" -gt 0 ]; then
    # A seventeenth word (#1555), and it is about THIS FILE rather than about the
    # tool it names. `help_audit` and `refusal_audit` execute every tool on the
    # argument that its door is reached before anything happens; a door below a
    # `gh` call makes that argument false, and the audit then makes a network call
    # while checking whether a flag is refused. Sending the reader to a door word
    # would send them to a door that works.
    echo "ADVICE VERDICT A_DOOR_BELOW_THE_NETWORK reached=$doors_network"
  elif [ "$refusals_wrong" -gt 0 ]; then
    # A fifteenth word (#1552). Not a flag word and not a row word: the tool
    # refused, correctly, and spent the wrong code doing it. Every text-against-
    # text check in this file was satisfied while `litany.sh` and `release.sh`
    # left 1 for an unknown flag — their rows documented `Exit … 1 …` truthfully,
    # for the OTHER thing 1 means in those tools. Sending the reader to a row
    # word would send them to a document that is correct.
    echo "ADVICE VERDICT A_REFUSAL_SPENDS_THE_WRONG_CODE wrong=$refusals_wrong"
  elif [ "$refusals_hung" -gt 0 ]; then
    # A sixteenth (#1552), and a different failure entirely: the tool did not
    # refuse at all — it READ the flag as nothing and did its job. That is the
    # hazard #1410's door audit hit on `backlog.sh`, and it is why every
    # invocation here is watched. "It refused with the wrong code" sends a
    # reader to an exit line; this sends them to the parser.
    echo "ADVICE VERDICT A_TOOL_THAT_DOES_NOT_REFUSE hung=$refusals_hung"
  elif [ "$doorless" -gt 0 ]; then
    # A thirteenth word (#1410). Its own, and not a flag word: `--help` is the one
    # flag whose absence neither existing direction can see. `flags_undocumented`
    # reads arms against the row and `flags_phantom` reads the row against the
    # arms; a tool with NO door has nothing on either side to disagree, so both
    # are silent and both are correct. Sending the reader to either would send
    # them to a file where the rows and the parser agree perfectly.
    echo "ADVICE VERDICT A_TOOL_THAT_WILL_NOT_SAY_WHAT_IT_DOES doorless=$doorless"
  elif [ "$doors_wrong" -gt 0 ]; then
    # A fourteenth (#1410), and the quieter of the pair. The door opens and the
    # answer is not the clause — a stale line range, a wrong file, a truncated
    # block. `checkage.sh` printed one line too many for its whole life and
    # nobody saw it (#1520), because nobody diffs `--help` against the header.
    # Distinct from `doorless` for the reason every word in this chain is
    # distinct: "it will not answer" sends a reader to the parser, and the
    # parser is fine.
    echo "ADVICE VERDICT A_DOOR_THAT_DOES_NOT_PRINT_THE_CLAUSE wrong=$doors_wrong"
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



# THE DOOR EVERY TOOL NOW HAS, AND NOTHING WAS WATCHING (#1410).
#
# Thirteen tools and `probes/bench.sh` learned to answer `--help` across #1517,
# #1520, #1522, #1525, #1527 and #1529 — and every one of those units was verified
# by a transcript pasted into a pull request. A paste is not a lock. The doors can
# rot one at a time, silently, and the next tool written can simply not have one.
#
# THE CHECK IS THE ONE THING NEITHER EXISTING DIRECTION COULD SEE. `flags_parsed`
# reads a tool's ARMS and `flags_phantom` reads its ROW; both are satisfied by a
# `-h|--help)` arm that prints nothing, prints the wrong file, or exits 2. What
# makes this checkable at all is that the answer has a KNOWN correct value: the
# block reader over the tool's own header, which is what every door here runs.
#
# SO THIS ONE EXECUTES THE TOOL, and that is a departure this file argues against
# elsewhere: it will not run a tool's ADVICE, because `git commit --amend` inside
# an audit is a tool damaging the tree to check whether it damages the tree. A
# `--help` door is the one invocation in the shop that is a pure function of the
# file — it prints a comment block and exits, before any parser, any network call
# and any write. `release.sh --help` cannot cut a tag; that is the whole point of
# where the arm sits.
#
# THREE OUTCOMES, and the middle one is why `doorless` is not the only counter:
#   doorless=    the tool refuses --help, or exits nonzero for it
#   doors_wrong= it answers, and the answer is not its header clause
#   doors_ok=    it answers with exactly the clause the block reader prints
#
# A door printing the wrong thing is the quieter failure and the longer-lived one:
# `checkage.sh` spent its whole life printing one line too many (#1520) and nobody
# saw it, because nobody diffs `--help` against the header.


# IS THE DOOR REACHABLE BEFORE ANYTHING HAPPENS? (#1555)
#
# Two checks in this file EXECUTE every tool — `help_audit` feeds `--help` and
# `refusal_audit` feeds a flag nobody parses. #1410's argument for the first was
# that a `--help` door is a pure function of the file: it prints a comment block
# and exits before any parser, any network call and any write, so
# `release.sh --help` cannot cut a tag. That was true of every door the day it was
# written and it was never a property the audit ENFORCED. #1552 could not make the
# argument at all — a refusal happens after whatever the parser does first, and
# `subissue.sh` had its flags read AFTER two `gh` calls until #1309 moved them.
#
# THE WATCHDOG BOUNDS DURATION, NOT EFFECT. A tool that reaches an API before its
# refusal has already made the call when the kill lands. This file's own rule
# elsewhere is stricter and says so — *it will not run the advice, because
# `git commit --amend` inside an audit would be a tool damaging the tree to check
# whether it damages the tree* — and two checks now sit on the other side of that
# line with an argument rather than a guarantee.
#
# So the argument becomes a check. In the tool's DOOR scope — `by_scope` already
# isolates the top-level parser from helper bodies — no side-effecting command may
# appear above the first arm the audits invoke. That is decidable from the text
# and it is the same shape of question `promises_a_suite` already asks.
#
# WHAT COUNTS, AND WHY THE LIST IS SPLIT. The first draft used one list — `gh`,
# `curl`, `git`, `rm`, `mv`, `mkdir`, `tee` — and broke the build on three tools,
# none of which is a hazard:
#
#   baseline.sh   `git rev-parse --show-toplevel` at door line 5   (a READ)
#   litany.sh     `trap 'rm -rf "$PRODUCER_CACHE"' EXIT` at 11     (REGISTERS a trap)
#   subissue.sh   `trap 'rm -rf "$tmp"' EXIT` at 10                (the same)
#
# A trap does not run where it is written, and a `git rev-parse` reads. Breaking on
# those teaches the next author to spell around the checker, which is exactly what
# #1207 says a bound must not do. So:
#
#   NETWORK is the break. `gh` and `curl` have unbounded effect and a watchdog
#   cannot undo a request that has already left. This is the set the audits'
#   argument is actually about.
#   SHELL is a report. Another program above the door is worth seeing — it is how
#   a network call arrives later — and it is not a defect today.
#
# Trap registrations are excluded from both: the body of a trap runs at exit, which
# is after the door has printed and left, and every one in this shop removes a
# temp directory the tool itself made.
DOOR_NETWORK='gh|curl|wget'
DOOR_SHELL='git|java|javac|mktemp|mv|rm|mkdir|touch|tee'

door_purity() {                 # door_purity <tools-dir>
  local dir="$1" tool body arm_line net_line shell_line
  doors_pure=0
  doors_shell=0
  doors_network=0
  for tool in "$dir"/*.sh; do
    [ -f "$tool" ] || continue
    # Comments out, trap REGISTRATIONS out, then the door scope only — `by_scope`
    # already separates the top-level parser from helper bodies, and the audits'
    # invocation reaches nothing else before the tool starts working.
    body="$(grep -vE '^[[:space:]]*#' <<< "$(by_scope "$tool" door)" | grep -vE '^[[:space:]]*trap[[:space:]]' || true)"
    arm_line="$(printf '%s\n' "$body" | grep -nE -- '--help\)' | head -1 | cut -d: -f1)"
    if [ -z "$arm_line" ]; then
      # No door at all is `help_audit`'s finding; reporting it twice would be one
      # defect counted twice (#1170).
      continue
    fi
    net_line="$(printf '%s\n' "$body" | grep -nE "(^|[^-a-zA-Z_])($DOOR_NETWORK)[[:space:]]" | head -1 | cut -d: -f1)"
    shell_line="$(printf '%s\n' "$body" | grep -nE "(^|[^-a-zA-Z_])($DOOR_SHELL)[[:space:]]" | head -1 | cut -d: -f1)"
    if [ -n "$net_line" ] && [ "$net_line" -lt "$arm_line" ]; then
      doors_network=$((doors_network + 1))
      BREAKS=$((BREAKS + 1))
      echo "DOOR_BELOW_THE_NETWORK $tool reaches the network at door line $net_line, above its --help arm at $arm_line"
    elif [ -n "$shell_line" ] && [ "$shell_line" -lt "$arm_line" ]; then
      doors_shell=$((doors_shell + 1))
      echo "DOOR_BELOW_A_COMMAND $tool runs another program at door line $shell_line, above its --help arm at $arm_line"
    else
      doors_pure=$((doors_pure + 1))
    fi
  done
}

# IS A REFUSAL A 2? (#1552)
#
# `tools/README.md`'s exit grammar makes 2 the refusal for every shell tool and 1
# *the claim does not hold*. Three checks in this file already read that grammar —
# `codes_unnamed`, `codes_unspent`, `codes_redefined` — and all three read the ROW
# against the SOURCE. None reads what the tool DOES when refused, which is why
# #1546 needed a person: `litany.sh` and `release.sh` spent 1 for an unknown flag
# while both rows documented `Exit … 1 …` truthfully, for the other thing 1 means
# in those tools. Every text-against-text check was satisfied.
#
# `probes/DoorRefusal` has asked exactly this of `probes/` since #1481. This is its
# counterpart, and it arrives now for the reason #1410's door audit arrived: until
# every tool had a door there was no reason to believe running them was safe.
#
# THE HAZARD IS THE SAME ONE, AND SHARPER. A tool that does not refuse an unknown
# flag does not print an error — it RUNS. #1410's first draft hung on `backlog.sh`
# paging the issues API a thousand rows at a time, and there is no textual pre-test
# for "has a refusal" the way there is for "has a `--help)` arm". So every
# invocation is watched, and a tool that outlives the watchdog is a FINDING rather
# than a skipped row: not refusing is precisely the defect.
#
# NO `timeout(1)`. It is GNU coreutils and not on a stock macOS, and this script
# runs on the operator's box and on `ubuntu-latest` — #901's dialect lesson, which
# `balance.sh` pays for in two `date` spellings. The watchdog is a background sleep
# and a kill, which is POSIX shell.
REFUSAL_WAIT=10
# ASSEMBLED, NOT WRITTEN, AND THIS FILE HAS NOW BEEN BITTEN FIVE TIMES. The flag
# extractor reads `=[[:space:]]*"?--[a-z0-9-]+` as a comparison, so spelling this
# constant out loud makes `advice.sh` report ITSELF for parsing a flag its row hides
# — which it did, on the first run of this audit. The suite assembles `--pr` from
# `$dash` for the same reason (#1033, #1157, #1222, #1265, #1276), and a checker
# finding its own test data is this file's oldest recurring joke.
REFUSAL_DASH=--
NOBODY_PARSES_THIS="${REFUSAL_DASH}zzz-no-tool-knows-this"

refuse_rc() {                   # refuse_rc <tool> — prints the exit code, or `hung`
  local tool="$1" pid watcher rc
  # JOB CONTROL, SO THE WATCHDOG CAN KILL A FAMILY. Without `set -m` the child runs
  # in this shell's process group and `kill $pid` reaches only the `bash` wrapper —
  # a grandchild survives, and `advice.sh` then waits for it at exit. The suite's
  # own hang fixture demonstrated that: a one-second watchdog and a thirty-second
  # suite. With job control the background job leads its own group and `kill -- -PID`
  # takes the family, which is what a tool shelling out to `gh` would be.
  set -m
  bash "$tool" "$NOBODY_PARSES_THIS" >/dev/null 2>&1 </dev/null &
  pid=$!
  set +m
  ( sleep "$REFUSAL_WAIT"; kill -9 -- -"$pid" 2>/dev/null ) >/dev/null 2>&1 &
  watcher=$!
  wait "$pid"; rc=$?
  kill "$watcher" 2>/dev/null || true
  # -9 leaves 137 through bash's 128+signal convention. A tool that really exits
  # 137 on a bad flag is indistinguishable here and has bigger problems.
  if [ "$rc" = 137 ]; then
    printf 'hung\n'
  else
    printf '%s\n' "$rc"
  fi
}

refusal_audit() {               # refusal_audit <tools-dir>
  local dir="$1" tool rc
  refusals_ok=0
  refusals_wrong=0
  refusals_hung=0
  for tool in "$dir"/*.sh; do
    [ -f "$tool" ] || continue
    rc="$(refuse_rc "$tool")"
    case "$rc" in
      2)    refusals_ok=$((refusals_ok + 1)) ;;
      hung) refusals_hung=$((refusals_hung + 1)); BREAKS=$((BREAKS + 1))
            echo "NO_REFUSAL $tool ran for ${REFUSAL_WAIT}s on an unknown flag instead of refusing it" ;;
      *)    refusals_wrong=$((refusals_wrong + 1)); BREAKS=$((BREAKS + 1))
            echo "REFUSED_WRONG $tool left $rc for an unknown flag; this tree's refusal is 2" ;;
    esac
  done
}

help_audit() {                                   # help_audit <tools-dir> [extra-tools...]
  local dir="$1"; shift
  local tool out want
  doors_ok=0
  doorless=0
  doors_wrong=0
  for tool in "$dir"/*.sh "$@"; do
    [ -f "$tool" ] || continue
    # The reader the doors themselves run: to the END of the comment block, never
    # to a line number (#1382). Quoted here rather than imported, because a shared
    # helper would mean the check and its subject read the file the same way BY
    # CONSTRUCTION and the comparison would be vacuous.
    want="$(awk 'NR==1 {next} !/^#/ {exit} /^#$/ {if (++blank == 2) exit} {print}' "$tool")"
    # THE TEXTUAL TEST COMES FIRST, AND IT IS NOT AN OPTIMISATION (#1410). Running
    # a tool that has no door does not print a refusal — it runs the TOOL. The
    # first draft of this audit executed every file unconditionally and
    # `tools/backlog.sh` with its arm deleted did not refuse `--help`: it read the
    # flag as nothing and started paging the issues API, a thousand results at a
    # time, and the audit hung. An auditor whose failure case is "perform the
    # subject's whole job" is not one this shop can run in a lane.
    #
    # So: no arm, no execution. The read is a grep for `--help)` on a line that is
    # not a comment — looser than the arm reader `flag_audit` uses, and on purpose.
    # That one demands the arm at line start, which is the shop's spelling but not
    # the only working one; a folded `case … in -h|--help) … esac` is a door that
    # works, and reporting it NO_DOOR would be this check inventing a defect out of
    # a style. Comments are excluded because a file that only DISCUSSES `--help)` is
    # a file with no door, and running it is the thing this paragraph is about.
    if ! grep -qE '^[^#]*--help\)' "$tool"; then
      doorless=$((doorless + 1))
      BREAKS=$((BREAKS + 1))
      echo "NO_DOOR $tool has no --help arm to run"
      continue
    fi
    if ! out="$(bash "$tool" --help 2>/dev/null)"; then
      doorless=$((doorless + 1))
      BREAKS=$((BREAKS + 1))
      echo "NO_DOOR $tool has a --help arm and leaves nonzero for it"
      continue
    fi
    if [ "$out" != "$want" ]; then
      doors_wrong=$((doors_wrong + 1))
      BREAKS=$((BREAKS + 1))
      echo "DOOR_WRONG $tool answers --help with something other than its header clause"
      continue
    fi
    doors_ok=$((doors_ok + 1))
  done
}

flag_audit() {                                   # flag_audit <tools-dir> <catalog>
  local dir="$1" catalog="$2" tool name row body helper arms compares accepted advertised flag
  flags_parsed=0
  flags_undocumented=0
  flags_phantom=0
  tools_no_flags=0
  flags_in_helpers=0
  for tool in "$dir"/*.sh; do
  [ -f "$tool" ] || continue
  name="$(basename "$tool")"
  row="$(grep "^| \`$name\`" "$catalog" || true)"
  [ -n "$row" ] || continue                      # uncatalogued, counted above
  # THE DOOR, AND ONLY THE DOOR (#1341). This read the whole file, and the audit's
  # premise — a `--x)` arm is a promise to a user — holds for a tool's argument
  # parser and fails for everything else in it: a suite helper's parameter, an
  # inner parse over a synthetic argv, a fixture body. `advice.sh` reported ITSELF
  # for `--row)`, a parameter to a function inside `--selftest`, unreachable from
  # a command line, and #1238 dodged it by making that helper positional — a
  # workaround in one file, not a repair.
  #
  # The expensive direction is the second one. The natural "fix" for a false
  # positive is to document the private parameter in the catalog; the row then
  # promises a flag the door refuses, `flags_phantom` fires, and two checks
  # disagree about one string. Nobody reads that as a defect in the audit — they
  # read it as noise and add an exemption.
  body="$(grep -vE '^[[:space:]]*#' <<< "$(by_scope "$tool" door)" || true)"
  helper="$(grep -vE '^[[:space:]]*#' <<< "$(by_scope "$tool" helper)" || true)"
  arms="$(printf '%s\n' "$body" \
          | grep -oE '^[[:space:]]*-{1,2}[a-z0-9-]+(\|-{1,2}[a-z0-9-]+)*\)' | tr -d ' )' | tr '|' '\n' || true)"
  compares="$(printf '%s\n' "$body" \
          | grep -oE '=[[:space:]]*"?--[a-z0-9-]+' | grep -oE -- '--[a-z0-9-]+' || true)"
  accepted="$(printf '%s\n%s\n' "$arms" "$compares" | grep -E '^--' | sort -u || true)"

  # REPORTED, NOT DROPPED, and that is the whole difference between a bound and an
  # exemption (#1207). A flag parsed only inside a function body is not judged
  # against the catalog, and it is still counted and named — so the day the rule
  # is wrong about a tool, the output says which flags it stopped looking at
  # instead of going quietly smaller. `flags_in_helpers=` never breaks the build.
  while IFS= read -r flag; do
    [ -z "$flag" ] && continue
    case "$(printf '%s\n' "$accepted")" in
      *"$flag"*) continue ;;
    esac
    flags_in_helpers=$((flags_in_helpers + 1))
    echo "HELPER_FLAG $tool parses '$flag' inside a function body — not a door, not judged"
  done <<< "$(printf '%s\n' "$helper" \
              | grep -oE '^[[:space:]]*-{1,2}[a-z0-9-]+(\|-{1,2}[a-z0-9-]+)*\)' | tr -d ' )' | tr '|' '\n' \
              | grep -E '^--' \
              | sort -u || true)"
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

  # A SHORT ALIAS OPENING THE ARM (#1518). The reader was anchored on `--`, so
  # `-h|--help)` was not an arm at all and the long flag inside it was invisible —
  # silently in the safe direction (three real doors uncounted) and loudly in the
  # wrong one: a tool that implements the door AND documents it was reported as a
  # phantom, which is the two-checks-disagreeing shape #1263's comment warns about.
  # Assembled from `$dash` for the same reason the comparison cases are: written
  # out, the arm is a literal in this file and `advice.sh` reports itself.
  local helparm; helparm="  -h|${dash}help) X=1 ;;"
  local ROW_HELP='| `fixture.sh` | does a thing. Usage: `tools/fixture.sh '"${dash}help"'`. |'
  case_         short-alias-door       0 "$helparm" "$ROW_HELP"
  case_         short-alias-hidden     1 "$helparm" "$ROW_WITHOUT"

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
  # The mirror of short-alias-door, and the case that actually cost a unit: a
  # correct tool with a correct row, reported as advertising a flag it refuses.
  # The door carries a LONG flag beside the short-aliased one on purpose — with
  # only the aliased arm the tool reads as having no flags at all, and the
  # early `tools_no_flags` continue skips the phantom loop entirely, so the
  # defect hides behind a different report. Every real tool in the shop has
  # other doors, which is why it fired there and not in a one-arm fixture.
  local pairarm; pairarm="  ${dash}pr) P=1 ;;
  -h|${dash}help) X=1 ;;"
  local ROW_PAIR='| `fixture.sh` | does a thing. Usage: `tools/fixture.sh '"${dash}pr N | ${dash}help"'`. |'
  phantom_case short-alias-not-phantom 0 "$pairarm" "$ROW_PAIR"
  # The bound is the point: a flag named AFTER the Usage clause — in the exit
  # grammar, or in a bold aside about another program — is not a promise about
  # this tool, and an unbounded read reported those as phantoms. release.sh's
  # row is the live example: it names balance.sh --rulercheck while explaining
  # which locks a cut skips.
  phantom_case flag-past-the-bound  0 '  --pr) X=1 ;;' \
        '| `fixture.sh` | does a thing. Usage: `tools/fixture.sh --pr N`. **Note:** unlike `other.sh --elsewhere`. Exit 0 fine · 2 refused. |'

  no_flags_case positional-only 1 'echo "$1"' "$ROW_WITHOUT"
  no_flags_case has-flags       0 '  --pr) PR=1 ;;' "$ROW_WITH"

  # THE DOOR AUDIT'S OWN CASES (#1410). Three tools in a scratch shop, one per
  # outcome, because the two failures are different defects and a suite that only
  # drove `doorless` would let `doors_wrong` be an unreachable branch — the state
  # `charset_drift` sat in for its whole life (#1358).
  door_case() {                 # door_case <name> <want-ok/less/wrong> <tool-body>
    local name="$1" want="$2" body="$3" got
    rm -rf "$tmp/shop"; mkdir -p "$tmp/shop"
    printf '%s\n' "$body" > "$tmp/shop/fixture.sh"
    BREAKS=0
    help_audit "$tmp/shop" >/dev/null 2>&1
    got="$doors_ok/$doorless/$doors_wrong"
    if [ "$want" = "$got" ]; then
      pass=$((pass + 1)); printf 'ADVICE case=%-26s want=%s got=%s OK\n' "$name" "$want" "$got"
    else
      fail=$((fail + 1)); printf 'ADVICE case=%-26s want=%s got=%s BROKEN\n' "$name" "$want" "$got"
    fi
  }

  # The clause is two comment lines under the shebang; the door prints them with
  # the same block reader every real door here runs.
  local CLAUSE='#!/usr/bin/env bash
# fixture.sh — does a thing
#
# Usage: fixture.sh [--pr N]'
  local READER='awk '"'"'NR==1 {next} !/^#/ {exit} /^#$/ {if (++blank == 2) exit} {print}'"'"' "$0"; exit 0'

  door_case door-open      1/0/0 "$CLAUSE
case \"\${1:-}\" in -h|--help) $READER ;; esac"
  # No arm at all: the tool refuses, or treats --help as an argument and leaves
  # nonzero. Either way there is no door.
  door_case door-absent    0/1/0 "$CLAUSE
echo \"FATAL unknown argument: \$1\" >&2; exit 2"
  # THE QUIET ONE. The door opens, exits 0, prints something — and it is not the
  # clause. A line range that has drifted looks exactly like this from outside,
  # and nothing before #1410 could tell it from a correct door.
  door_case door-wrong-text 0/0/1 "$CLAUSE
case \"\${1:-}\" in -h|--help) sed -n '2,2p' \"\$0\"; exit 0 ;; esac"
  # A door that prints the clause and then leaves nonzero is doorless, not wrong:
  # a caller checking \$? never reads the text.
  door_case door-bad-exit  0/1/0 "$CLAUSE
case \"\${1:-}\" in -h|--help) awk 'NR==1 {next} !/^#/ {exit} {print}' \"\$0\"; exit 1 ;; esac"
  # THE CASE THE FIRST DRAFT'S HANG ARGUES FOR. A file that only DISCUSSES the door
  # in a comment has no door, and must be reported WITHOUT being run — the body here
  # would otherwise take a minute and print nothing this audit asked for, which is
  # what `backlog.sh` did with its arm deleted.
  door_case door-in-comment 0/1/0 "$CLAUSE
# the arm would be -h|--help) here if this tool had one
sleep 60"

  # THE REFUSAL AUDIT'S OWN CASES (#1552). Three tools in a scratch shop, one per
  # outcome. The hang case is the one that earns the watchdog, and it is bounded
  # so the suite does not pay ten seconds for it.
  refusal_case() {              # refusal_case <name> <want-ok/wrong/hung> <tool-body>
    local name="$1" want="$2" body="$3" got saved="$REFUSAL_WAIT"
    rm -rf "$tmp/shop"; mkdir -p "$tmp/shop"
    printf '%s\n' "$body" > "$tmp/shop/fixture.sh"
    BREAKS=0
    REFUSAL_WAIT=1
    refusal_audit "$tmp/shop" >/dev/null 2>&1
    REFUSAL_WAIT="$saved"
    got="$refusals_ok/$refusals_wrong/$refusals_hung"
    if [ "$want" = "$got" ]; then
      pass=$((pass + 1)); printf 'ADVICE case=%-26s want=%s got=%s OK\n' "$name" "$want" "$got"
    else
      fail=$((fail + 1)); printf 'ADVICE case=%-26s want=%s got=%s BROKEN\n' "$name" "$want" "$got"
    fi
  }

  refusal_case refusal-is-two    1/0/0 'echo "FATAL unknown flag: $1" >&2; exit 2'
  # #1546's live shape: the tool DOES refuse and spends the code that means the
  # claim it judges did not hold, so a caller cannot tell a typo from a finding.
  refusal_case refusal-is-one    0/1/0 'echo "FATAL unknown flag: $1" >&2; exit 1'
  # And the hazard: no refusal at all, so the flag is read as nothing and the tool
  # does its job. This is what `backlog.sh` did to #1410's first door audit.
  refusal_case refusal-never     0/0/1 'sleep 30'

  # THE DOOR-PURITY CASES (#1555). Four, because the rule has three outcomes and a
  # trap registration is the false positive that broke the first draft on three
  # real tools.
  purity_case() {               # purity_case <name> <want-pure/shell/network> <tool-body>
    local name="$1" want="$2" body="$3" got
    rm -rf "$tmp/shop"; mkdir -p "$tmp/shop"
    printf '%s\n' "$body" > "$tmp/shop/fixture.sh"
    BREAKS=0
    door_purity "$tmp/shop" >/dev/null 2>&1
    got="$doors_pure/$doors_shell/$doors_network"
    if [ "$want" = "$got" ]; then
      pass=$((pass + 1)); printf 'ADVICE case=%-26s want=%s got=%s OK\n' "$name" "$want" "$got"
    else
      fail=$((fail + 1)); printf 'ADVICE case=%-26s want=%s got=%s BROKEN\n' "$name" "$want" "$got"
    fi
  }

  local ARM='case "${1:-}" in -h|--help) echo hi; exit 0 ;; esac'
  purity_case door-first        1/0/0 "$ARM"
  # The break: an unbounded effect above the door, so the audits above would make
  # a network call while asking whether a flag is refused.
  purity_case door-below-network 0/0/1 "root=\$(gh api user)
$ARM"
  # Reported, not broken: another program above the door is worth seeing — it is
  # how a network call arrives later — and is not a defect today.
  purity_case door-below-command 0/1/0 "root=\$(git rev-parse --show-toplevel)
$ARM"
  # THE FALSE POSITIVE THAT COST THE FIRST DRAFT THREE TOOLS. A trap does not run
  # where it is written; its body runs at exit, after the door has printed and
  # left, and every one in this shop removes a temp directory the tool made.
  purity_case trap-is-not-an-effect 1/0/0 "trap 'rm -rf /tmp/x' EXIT
$ARM"

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
  verdict_case doors_network      A_DOOR_BELOW_THE_NETWORK
  verdict_case refusals_wrong     A_REFUSAL_SPENDS_THE_WRONG_CODE
  verdict_case refusals_hung      A_TOOL_THAT_DOES_NOT_REFUSE
  verdict_case doorless           A_TOOL_THAT_WILL_NOT_SAY_WHAT_IT_DOES
  verdict_case doors_wrong        A_DOOR_THAT_DOES_NOT_PRINT_THE_CLAUSE
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

  # ---- the suite gate (#1376) ----------------------------------------------
  #
  # Six shapes, five of which have actually occurred in this tree. The gate is
  # a pure function of one file's text, so the cases are the file's text — the
  # shipped function is called, never a copy of its expression.
  gate_case() {                 # gate_case <name> <want yes|no> <tool-body>
    local f="$tmp/gate.sh" got
    printf '%s\n' "$3" > "$f"
    if promises_a_suite "$f"; then got=yes; else got=no; fi
    if [ "$2" = "$got" ]; then
      pass=$((pass + 1)); printf 'ADVICE case=%-26s want=%-3s got=%-3s OK\n' "$1" "$2" "$got"
    else
      fail=$((fail + 1)); printf 'ADVICE case=%-26s want=%-3s got=%-3s BROKEN\n' "$1" "$2" "$got"
    fi
  }

  gate_case gate:own-case-arm        yes '  --selftest) MODE=selftest ;;'
  # #1347: release.sh quotes the DAEMON's flag because lock 2 of a release runs it.
  gate_case gate:quotes-the-daemon   no  'java -cp out matrix.Main --selftest'
  # advice.sh runs every tool's suite, so its own file is full of somebody else's flag.
  gate_case gate:quotes-another-tool no  'timeout 60 bash "$tool" --selftest'
  # #1157's shape: a checker reading its own explanation.
  gate_case gate:comment-only        no  '# the --selftest flag is discussed here and parsed nowhere'
  # balance.sh: its three suites are spelled --datecheck, --rulercheck, --judgecheck.
  gate_case gate:no-mention-at-all   no  '  --datecheck) MODE=datecheck ;;'
  # THE ONE NOTHING IN THE TREE DOES, which is why it is written down: a tool that
  # runs another program's suite AND has one of its own must still be asked. The
  # expression's behaviour here was unknown rather than correct.
  gate_case gate:quotes-and-parses   yes 'bash tools/x.sh --selftest
  --selftest) MODE=selftest ;;'

  # ---- the door and the helper (#1341) -------------------------------------
  #
  # THE FALSE POSITIVE AND THE TRUE POSITIVE IN THE SAME SUITE, which is #1341's
  # explicit ask: one without the other is how an exemption becomes a hole. A
  # bound that only ever proves it stopped looking is indistinguishable from a
  # check that stopped working.
  scope_case() {                # scope_case <name> <want-undoc> <want-helpers> <tool-body> <row>
    local name="$1" want="$2 $3" got
    rm -rf "$tmp/shop"; mkdir -p "$tmp/shop"
    printf '%s\n' "$4" > "$tmp/shop/fixture.sh"
    printf '%s\n' "$5" > "$tmp/shop/README.md"
    BREAKS=0
    flag_audit "$tmp/shop" "$tmp/shop/README.md" >/dev/null 2>&1
    got="$flags_undocumented $flags_in_helpers"
    if [ "$want" = "$got" ]; then
      pass=$((pass + 1)); printf 'ADVICE case=%-26s want=[%s] got=[%s] OK\n' "$name" "$want" "$got"
    else
      fail=$((fail + 1)); printf 'ADVICE case=%-26s want=[%s] got=[%s] BROKEN\n' "$name" "$want" "$got"
    fi
  }

  # THE CASE THAT IS #1341's OWN TRANSCRIPT: a private parameter to a function
  # inside a suite, reported as an undocumented door flag. #1238 dodged it by
  # making that helper positional; this is the repair.
  scope_case scope:flag-in-a-helper 0 1 '  --pr) PR=1 ;;
helper() {
  case "$1" in
    --row) ROW=1 ;;
  esac
}' "$ROW_WITH"
  # THE TRUE POSITIVE, unchanged by the bound: a flag in the DOOR that the row
  # does not name is still the finding this audit exists for.
  scope_case scope:flag-in-the-door 1 0 '  --row) ROW=1 ;;' "$ROW_WITH"
  # A flag in both places is the door's, counted once, and NOT reported as a
  # helper flag — which is the live shape in this file: `--selftest)` is the door
  # at line 221 and a fixture STRING inside the suite, and the whole-file read
  # counted the fixture as a parse.
  scope_case scope:same-flag-both   0 0 '  --pr) PR=1 ;;
helper() {
  case "$1" in
    --pr) PR=1 ;;
  esac
}' "$ROW_WITH"

  # The rule itself, driven directly. It is a rule about THIS SHOP's spelling —
  # `name() {` at column zero opens, `}` at column zero closes — so the cases are
  # that spelling, including the one that would break a naive reading.
  scope_lines() {               # scope_lines <name> <door|helper> <want> <body>
    local name="$1" got
    printf '%s\n' "$4" > "$tmp/scope.sh"
    got="$(by_scope "$tmp/scope.sh" "$2" | tr -d ' \n')"
    if [ "$3" = "$got" ]; then
      pass=$((pass + 1)); printf 'ADVICE case=%-26s want=[%s] got=[%s] OK\n' "$name" "$3" "$got"
    else
      fail=$((fail + 1)); printf 'ADVICE case=%-26s want=[%s] got=[%s] BROKEN\n' "$name" "$3" "$got"
    fi
  }

  scope_lines scope:door-lines   door   'AB' 'A
f() {
X
}
B'
  scope_lines scope:helper-lines helper 'X'  'A
f() {
X
}
B'
  # AN INDENTED INNER FUNCTION NEITHER OPENS NOR CLOSES A SCOPE, and this is the
  # case that keeps the rule usable: `case_()` inside `selftest()` is written that
  # way in three tools here, and a reading that let its `}` close the outer scope
  # would hand the rest of the suite back to the door.
  # Asked of the DOOR rather than the helper, because the door is where the damage
  # would land: a reading that let the inner `}` close the outer scope would hand
  # `Y` and everything after it back to the door and judge a suite's parameters as
  # promises. The helper side would still contain `X` and look fine.
  scope_lines scope:inner-function door 'AB' 'A
f() {
X
  g() {
  }
Y
}
B'

  # ---- the two identities (#1443) ------------------------------------------
  #
  # `tools = suites + no_suite + skipped_self + skipped_no_promise` and
  # `tools = codes_checked + codes_exempt + codes_no_promise + codes_no_literal
  #  + codes_no_row` both held by construction, which is a property of control
  # flow and not an assertion. These cases assert the property the sums rest on:
  # each head classifies a file into EXACTLY ONE word from a closed set.
  #
  # Driving the counters themselves would need the loops parameterised on a
  # directory the way `flag_audit` is. Driving the classifier is the smaller
  # move and covers the same failure — a fifth exit added to a head either
  # returns a word these cases do not expect, or returns nothing at all, and
  # both are a BROKEN row here rather than a census line that quietly stops
  # adding up.
  class_case() {                # class_case <name> <fn> <want> <tool-body> [<row>]
    local f="$tmp/class.sh" got
    printf '%s\n' "$4" > "$f"
    if [ "$2" = suite_class ]; then got="$(suite_class "$f")"; else got="$(code_class "$f" "${5:-}")"; fi
    if [ "$3" = "$got" ]; then
      pass=$((pass + 1)); printf 'ADVICE case=%-26s want=%-10s got=%-10s OK\n' "$1" "$3" "$got"
    else
      fail=$((fail + 1)); printf 'ADVICE case=%-26s want=%-10s got=%-10s BROKEN\n' "$1" "$3" "$got"
    fi
  }

  # `self` cannot be driven through a fixture: the arm compares the PATH against
  # this file's own, which is the whole point of the arm. Asked of the real name.
  if [ "$(suite_class tools/advice.sh)" = self ]; then
    pass=$((pass + 1)); printf 'ADVICE case=%-26s want=%-10s got=%-10s OK\n' class:suite-self self self
  else
    fail=$((fail + 1)); printf 'ADVICE case=%-26s want=%-10s got=%-10s BROKEN\n' \
        class:suite-self self "$(suite_class tools/advice.sh)"
  fi
  class_case class:suite-no-promise suite_class no_promise '  --datecheck) MODE=x ;;'
  class_case class:suite-admitted   suite_class admitted   '  --selftest) MODE=selftest ;;'

  # The row is the second argument here, and the fixtures below are SYNTHESISED
  # from a code list for the ninth instance of the oldest bug in this file: a
  # program hunting `exit N` cannot contain `exit N`. `printf 'exit %s\n'` carries
  # no digit, so the format string is invisible to the grep it feeds.
  ROW_CLASS='| `class.sh` | does a thing. Exit 0 fine · 2 refused. |'
  class_case class:code-no-row      code_class no_row     "$(printf 'exit %s\n' 2)" ''
  class_case class:code-no-promise  code_class no_promise "$(printf 'exit %s\n' 2)" \
        '| `class.sh` | does a thing and promises no codes. |'
  class_case class:code-no-literal  code_class no_literal 'echo hello'              "$ROW_CLASS"
  # `exempt` needs BOTH a literal code and a pass-through, and the case is
  # written that way because the first draft was not: a fixture whose only exit
  # was `exit $?` came back `no_literal`, since the empty-spends test runs first.
  # That ordering is the shipped behaviour and it is right — with no literal
  # there is nothing to compare and the exemption has no work to do — but the two
  # words are reachable from the same file and only one of them says why. This is
  # `advice.sh`'s own shape: literal refusals plus `selftest; exit $?`.
  class_case class:code-exempt      code_class exempt     "$(printf 'exit %s\n' 2)"'
exit $?'                                                                            "$ROW_CLASS"
  class_case class:code-checked     code_class checked    "$(printf 'exit %s\n' 2)" "$ROW_CLASS"

  # THE CASE THAT IS THE WHOLE POINT: every tool in the REAL shop classifies to a
  # word in each closed set. A fixture proves the words exist; this proves nothing
  # falls between them — which is what a fifth exit added to a head looks like
  # from the outside, and it is the only shape a synthetic fixture cannot show.
  local stray=0 t w
  for t in tools/*.sh; do
    w="$(suite_class "$t")"
    case "$w" in self|no_promise|admitted) ;; *) stray=$((stray + 1)); echo "  UNCLASSED suite_class $t -> '$w'" ;; esac
    w="$(code_class "$t" "$(grep "^| \`$(basename "$t")\`" tools/README.md || true)")"
    case "$w" in no_row|no_promise|no_literal|exempt|checked) ;; *) stray=$((stray + 1)); echo "  UNCLASSED code_class $t -> '$w'" ;; esac
  done
  if [ "$stray" = 0 ]; then
    pass=$((pass + 1)); printf 'ADVICE case=%-26s want=0 got=%s OK\n' class:closed-over-the-shop "$stray"
  else
    fail=$((fail + 1)); printf 'ADVICE case=%-26s want=0 got=%s BROKEN\n' class:closed-over-the-shop "$stray"
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
# The bench is not in `tools/` and is the one a stranger is most likely to run, so it
# is named rather than swept — `probes/` holds fifty-seven Java files and one shell
# program, and a glob there would ask a probe for a door it has no reason to have.
help_audit tools probes/bench.sh
door_purity tools
refusal_audit tools

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
# THE IDENTITY THIS CHECK OWES ITS READER (#1434), the same one #1377 gave the
# suite loop one loop over — and it was one term short until #1443 wrote the
# cases that drive it:
#
#     tools = codes_checked + codes_exempt + codes_no_promise + codes_no_literal
#            + codes_no_row
#
# The fifth term is the tool with no catalog row, skipped here and counted by
# nothing. The four-term sum held only while `uncatalogued=0` — a counter from
# another loop, under another verdict word — which makes it an identity that
# depends on a different check passing, and the tool that lands without a row is
# exactly the one whose codes nobody is reading. `codes_no_row` is a census field
# rather than a break: that population already fails the build through
# `uncatalogued`, and one absence reported twice is one defect counted twice.
#
# `codes_unspent=0` read as a statement about the shop and was a statement about
# four of thirteen tools: nine carry a pass-through somewhere, and one line skips
# the WHOLE FILE for it. That silence cost a real defect today - #1432 found
# advice.sh promising `1 one does not` and always exiting 0 on its main path,
# which is precisely what this check exists to catch and never looked at,
# because `selftest; exit $?` for its own door exempted the file.
#
# The exemption is right and the silence is not: a pass-through`s codes genuinely
# cannot be read from a literal grep (#1238), and a tool skipped for a good
# reason still has to be counted, or the counter reporting on the rest reads as a
# report on everything.
codes_unspent=0
codes_checked=0
codes_exempt=0
codes_no_promise=0
codes_no_literal=0
codes_no_row=0
for tool in tools/*.sh; do
  name="$(basename "$tool")"
  row="$(grep "^| \`$name\`" tools/README.md || true)"
  # One word from a closed set, and the counters are its only readers: the head
  # no longer decides anything a case cannot drive (#1443). `advice.sh` itself is
  # the live `exempt` — `selftest; exit $?` spends 1 on a failing suite, and the
  # first draft of this check reported its own row as a liar.
  case "$(code_class "$tool" "$row")" in
    no_row)     codes_no_row=$((codes_no_row + 1)); continue ;;
    no_promise) codes_no_promise=$((codes_no_promise + 1)); continue ;;
    no_literal) codes_no_literal=$((codes_no_literal + 1)); continue ;;
    exempt)     codes_exempt=$((codes_exempt + 1)); continue ;;
  esac
  codes_checked=$((codes_checked + 1))
  spends="$(spends_of "$tool")"
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
# THE IDENTITY THIS LINE OWES ITS READER (#1377):
#
#     tools = suites + no_suite + skipped_self + skipped_no_promise
#
# It did not close. The line printed `tools=12 suites=9 no_suite=0` and left a
# gap of three that a reader could only reconcile by knowing three facts kept in
# three places — that this file skips itself at the top of the loop, that
# `release.sh` quotes the daemon's flag and promises no suite of its own
# (#1347), and that `balance.sh` spells its three suites `--datecheck`,
# `--rulercheck` and `--judgecheck`. None of that was on the line.
#
# It matters because #1347 NARROWED the gate deciding who enters this loop, and
# the risk a narrowing carries is excluding a real suite — which had no counter
# at all. A tool dropped by the gate simply stopped appearing in `suites=`,
# `no_suite=`, `unrun=` and `charset_checked=`, and nothing moved. The
# falsification #1347 offered was running all twelve by hand and printing which
# entered: correct, and it exists only in a merged pull request body, which is
# this file's own complaint about other people's evidence.
#
# `probes = stable + drift + exempt + unchecked` is the same shape one directory
# over, and #970's argument for its fourth field applies verbatim.
#
# `skipped_self` is always 1 and always for the same reason, which by #1357's
# rule makes it a bad gate and does not make it a bad FIELD: a term that is
# always 1 is exactly what a reader forgets when doing the subtraction by hand.
skipped_self=0
skipped_no_promise=0
for tool in tools/*.sh; do
  # One word from a closed set (#1443). The two words below are the head's whole
  # decision; `admitted` means the remaining two terms are not decidable from the
  # file and the tool has to be RUN.
  #
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
  case "$(suite_class "$tool")" in
    self)       skipped_self=$((skipped_self + 1)); continue ;;
    no_promise) skipped_no_promise=$((skipped_no_promise + 1)); continue ;;
  esac
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

echo "ADVICE tools=$(ls tools/*.sh | wc -l | tr -d ' ') uncatalogued=$uncatalogued rows_duplicated=$rows_duplicated catalog_wrong=$catalog_wrong charset_checked=$charset_checked charset_nothing=$charset_nothing suites=$suites no_suite=$no_suite skipped_self=$skipped_self skipped_no_promise=$skipped_no_promise unrun=$unrun codes_undocumented=$codes_undocumented codes_indirect=$codes_indirect codes_redefined=$codes_redefined codes_unspent=$codes_unspent codes_checked=$codes_checked codes_exempt=$codes_exempt codes_no_promise=$codes_no_promise codes_no_literal=$codes_no_literal codes_no_row=$codes_no_row codes_unnamed=$codes_unnamed lines=$found flags_checked=$checked" \
     "unimplemented=$missing unfalsifiable=$unfalsifiable" \
     "flags_parsed=$flags_parsed flags_undocumented=$flags_undocumented flags_phantom=$flags_phantom tools_no_flags=$tools_no_flags flags_in_helpers=$flags_in_helpers" \
     "doors_ok=$doors_ok doorless=$doorless doors_wrong=$doors_wrong" \
     "refusals_ok=$refusals_ok refusals_wrong=$refusals_wrong refusals_hung=$refusals_hung" \
     "doors_pure=$doors_pure doors_shell=$doors_shell doors_network=$doors_network"
# The census rule (#1221): `codes_returns` is a description of the tree, not a
# claim whose change is a finding — a tool gaining a helper function that
# returns a boolean moves it, and nothing is wrong. It sits here so the number
# that CANNOT be judged is still visible beside the ones that can.
echo "CODES_CENSUS tools_with_returns=$codes_returns catalog_rows=$(grep -c '^| `[a-z-]*\.sh`' tools/README.md)  (TOOLS carrying at least one, not returns — it sits beside tools= and would read as a statement count otherwise, #1368; a return is a predicate or an exit depending on where the function sits, so it is unjudged on purpose)"
verdict_word
# AND THE CODE THE ROW PROMISES (#1432). The row has said `1 one does not` for
# as long as it has existed, and `verdict_word` only PRINTS — it was the last
# statement of the script, so the status was the last echo's and a break verdict
# left with 0:
#
#     $ ( cd /tmp/nt2 && bash x/advice.sh ) | tail -1
#     ADVICE VERDICT A_TOOL_NOBODY_CAN_FALSIFY unfalsifiable=1
#     $ echo $?
#     0
#
# `codes_unspent` is the check that asks exactly this and could not see it: the
# file contains `selftest; exit $?` for its own door, and one pass-through
# exempts the WHOLE file rather than the path it sits on.
exit $((BREAKS > 0 ? 1 : 0))
