#!/usr/bin/env bash
# probes/bench.sh — the whole bench, one command, one verdict.
#
# Usage: probes/bench.sh [ticks]        sweep every probe (default 6000 ticks)
#        probes/bench.sh --list         print the contract table, run nothing
#        probes/bench.sh --no-build     sweep without recompiling
#        probes/bench.sh --twice        sweep, then run each probe a second
#                                       time and byte-compare the two outputs
#        probes/bench.sh --without-probes
#                                       build src/ and selftest with probes/
#                                       deleted, in a throwaway copy of HEAD
#
# The bench was fifteen programs invoked by hand while CI judged four of them,
# with the expected verdict lines inlined in a workflow file most contributors
# never open. That is the wrong home for the bench's contract: the catalog is
# in probes/README.md, the verdicts are in the probes, and the list of what
# gets judged belongs next to them. This runner is that list.
#
# Since #880 the workflow runs this script instead of keeping a second list of
# its own — the two had drifted until two thirds of the judged rows here
# reached no runner but a human's hands, and a probe CI does not run is a probe
# that guards nothing between two people remembering it. Adding a row below is
# therefore adding a lock: no YAML edit, no second place to remember.
#
# The discipline the CI lane already got right, kept exactly: a judged probe is
# judged by EXACT-LINE grep (grep -qxF), so `=0` can never match `=01`, and a
# missing verdict line fails the sweep. A reporting probe is RUN — a crash or a
# nonzero exit is a failure, but no verdict is demanded. Adding a probe is a
# one-row change in the table below, beside the probe, not in a YAML.
#
# Zero dependency by rule (D-040): javac, java, sh. No build tool, no plugin.
# Rollback is deleting this file.

set -euo pipefail

cd "$(dirname "$0")/.."

TICKS=6000
BUILD=yes
LIST=no
TWICE=no
WITHOUT=no
for arg in "$@"; do
  case "$arg" in
    --list) LIST=yes ;;
    --no-build) BUILD=no ;;
    --twice) TWICE=yes ;;
    --without-probes) WITHOUT=yes ;;
    ''|*[!0-9]*) echo "FATAL unknown argument: $arg" >&2; exit 2 ;;
    *) TICKS="$arg" ;;
  esac
done

# ---------------------------------------------------------------------------
# The contract table. One row per probe:
#
#   judge <Class> '<the exact line the run must print>' [args...]
#   run   <Class> [args...]
#
# prefixed, on either of them, by:
#
#   vary  '<why its output may legitimately move>' judge|run <Class> ...
#
# The args are the probe's own, in its own order — the bench does not pretend
# every instrument takes the same ones. $TICKS is spent where a probe's cost
# scales with the arc; the census sweeps carry their own smaller ranges so the
# lane's wall clock stays a lane's, not a laboratory's.
#
# `vary` is a MODIFIER, not a third verb: it declares an exemption from the
# --twice determinism pass with the reason stated in the row rather than
# skipped in silence, and then runs whichever verb follows it. It was a verb
# while it was a synonym for "run and forgive the drift", and that spelling
# quietly made the two questions one — what the sweep DEMANDS of a row, and
# whether that row's bytes may differ between two identical runs. They are
# separate, and AllocMeter is the row that separates them: its verdict line is
# fixed while the byte counts printed beside it move with the JIT (#906). It
# stays the rarest thing in the table, because an instrument whose output moves
# between two identical runs cannot be diffed across a change, which is most of
# what a bench is for. Exactly one row wears it today, and it earns it by
# printing the JVM's noise beside the world's number instead of in place of it
# (#817): the steady figure is a median of twenty-four repeats and barely moves,
# and the cold sample published next to it moves by design.
# ---------------------------------------------------------------------------
table() {
  judge LedgerMirror 'LEDGER_ANOMALIES=0'      "$TICKS"
  judge OneTrace     'VERDICT CONTRACT_HELD'   "$TICKS"
  judge CapSentinel  'CAP_BREACHES=0'          "$TICKS"
  judge ArcBeats     'VERDICT BEATS_IN_ORDER'  "$TICKS"
  judge LinkAudit    'VERDICT CLEAN'           "$TICKS"
  judge PirateSever  'VERDICT CONTRACT_HELD'
  judge PodOptional  'VERDICT POD_OPTIONAL_HELD' "$TICKS"
  judge DoorPressure 'VERDICT DOOR_PRESSURE_HELD' "$TICKS"
  judge LineLint     'VERDICT GRAMMAR_HELD'    "$TICKS"
  judge BirthInputs  'VERDICT BIRTH_INPUTS_COMPLETE' "$TICKS"
  # Re-aimed by #764, not deleted. The row exists to pin the current KID_*
  # tuning as a contract so that widening the band goes red, and the count in
  # the judged line is the pin: the band is now open, and the number of births
  # it admits over seeds 1..20 at 600 windows is the thing a tuning moves.
  judge FateAtlas    'VERDICT BAND_OPEN admitted=5'
  judge HullRoster   'VERDICT ROSTER_TOTAL'      3000
  judge DistrictNeutral 'VERDICT DISTRICTS_DRAW_NOTHING'
  # Seed 42 explicitly rather than $TICKS: this probe's only argument is a
  # seed, and its cost is one boot, not an arc. Judged rather than run — the
  # off-pool and duplicate-quarter legs have verdicts, and a row that only
  # demanded survival would have accepted the NoSuchFieldException this probe
  # threw the moment #842 moved the pools out from under it (#892).
  judge DistrictCensus 'VERDICT CITY_CENSUSED' 42
  judge CensusBlocks 'SELFCHECK VERDICT MATH_OK'  --selfcheck
  judge SealHygiene  'VERDICT SEAL_HYGIENE_HELD sites=2 checked=24 breaks=0'
  judge ConfirmationSweep 'VERDICT CONFIRMATIONS_HELD' "$TICKS"
  judge HuntBound    'VERDICT HUNT_BOUND_HELD movers=18 breaks=0'  "$TICKS"
  # SheetBench holds three rows because it is three instruments behind one class,
  # and the table is keyed by (class, args) rather than by class. --discipline
  # prints a standalone verdict line and is judged like every other row.
  # --avalanche prints its measurements and its verdict on ONE line, so an
  # exact-line judge would pin mean_bitflip and max_axis_corr into this table
  # beside the bound the probe already prints and already checks — and the row
  # would then go red on a lawful mixer change the probe itself passed, which
  # is a false alarm the bench would have manufactured. It is `run` instead,
  # and it is judged all the same: --avalanche is the one mode in the bench
  # whose exit code is its verdict (`System.exit(avalanche())`, 1 when the
  # bound is missed), and `run` fails a row on a nonzero exit.
  judge SheetBench   'DISCIPLINE VERDICT PASS'   --discipline
  run   SheetBench   --avalanche
  # The row that loads matrix.character.Contest at all. The kernel is imported
  # by nothing in the domain, so its class-init checks — the exchange table and
  # the margin bands as ONE law since #988 — fire only when something reads the
  # class, and until this row no lane did: the sweep was green on a tree whose
  # two tables disagreed. The judged line is the law rather than a measurement,
  # so it moves only when a verdict moves a threshold, and moving one table
  # without the other reddens this row either as a missing line or as the
  # class-init throw that stops the probe before it prints one. The population
  # figures beside it are deliberately NOT pinned into this row — those are
  # #835's and they move with the mixer — which does not leave them unjudged:
  # --bands exits nonzero on BANDS_DRIFTED and a judged row fails on a nonzero
  # exit, exactly the way --avalanche's bound is read one line above.
  judge SheetBench   'BANDS LAW decisive_edge=4 exchange_opens_at=4 symmetric=true VERDICT ONE_LAW' \
        --bands
  judge DocLint      'VERDICT DOCS_TRUE'         "$TICKS"
  judge DocLint      'SELFCHECK VERDICT DOCLINT_FALSIFIABLE' --selfcheck
  judge BondBook     'VERDICT BOOK_TURNS_OVER'  "$TICKS"
  judge SameTick     'VERDICT SAME_TICK_ABSORB' "$TICKS"
  # The Room 303 clause's own row, and the one place its REFUSAL is reachable.
  # Its budget is written here rather than taken from $TICKS for the reason
  # #377 measured: at 6,000 ticks the clause fires and is never asked twice, so
  # a $TICKS row would judge half the ruling and report the other half as held.
  # 40,000 ticks under 60 deployed daemons is the scale at which a resurrected
  # mind dies a second time — 24 firings, 11 spent-edge refusals — and the row
  # costs the sweep about eight seconds.
  judge BondScenario 'VERDICT ONCE_PER_EDGE_HELD' 40000 42 60
  # The one row that reads a committed file. Its budget is written here instead of
  # taken from $TICKS because probes/beatdrift.baseline is a measurement AT a
  # budget: a sweep at 2,000 ticks reaches two of the eight beats and reads -1 for
  # the other six, and comparing that against a 6,000-tick row would report the
  # argument as drift. The probe refuses the mismatch rather than judging it —
  # `CensusBeatDrift 42,7 2000 --baseline-file probes/beatdrift.baseline` exits 2
  # with a FATAL naming both budgets. Band and denominator ride in the
  # judged line, so widening the tolerance — or reading a pin that names none of
  # the beats — is an edit to this row and not a quiet pass.
  judge CensusBeatDrift 'VERDICT DRIFT_WITHIN_BAND compared=16/16 band=200' \
        42,7 6000 --band 200 --baseline-file probes/beatdrift.baseline
  run   DrawMeter    "$TICKS"
  run   ChainDump    "$TICKS"
  run   LinkTrace    "Nadia Petrov" "$TICKS"
  run   NameCensus   42
  run   SheetDump    --all
  vary  'prints its own instrument noise: steady_max is a cold uncompiled sample by construction and lands anywhere in 2.0-7.9 KB/tick, while the steady median it sits beside holds at 367 (#817)' \
        judge AllocMeter 'VERDICT ALLOC_IN_BUDGET' 42
  judge AllocMeter   'SELFCHECK VERDICT GUARD_FIRES' --selfcheck
  # The referee's own referee, not the referee. `NeutralDiff <ticks>` needs
  # #528's sealed fixture, which is not in this tree, and a row that reads a
  # file the repository does not contain is a red sweep on every box. What is
  # judged here is the comparison itself, driven over hand-written chains with
  # no universe: the PASS branch is the only one a green tree ever reaches, so
  # the four failure verdicts are exercised here or nowhere. When the fixture
  # lands, this row gains a sibling that runs the lane for real.
  judge NeutralDiff  'SELFCHECK VERDICT REFEREE_HOLDS' --selfcheck
  run   SeedAtlas    1 5 "$TICKS"
}

PROBES=0 JUDGED=0 PASS=0 FAIL=0 RAN=0
STABLE=0 DRIFTED=0 EXEMPT=0
VARIES=''

# One row's run, printed. The three verbs differ only in what they demand of
# the output afterwards, so the invocation itself is written once: the class,
# its own args, stderr folded in, and the exit code kept rather than trusted.
ROW_OUT='' ROW_RC=0
execute() {
  local cls="$1"; shift
  set +e
  ROW_OUT="$(java -cp out:probes/out "$cls" "$@" 2>&1)"
  ROW_RC=$?
  set -e
  printf '%s\n' "$ROW_OUT"
}

# The --list row, printed by both verbs from one place.
#
# varies= rides at the END, and only when set: every row that is not exempt
# prints the CONTRACT line it printed before this unit, byte for byte. A field
# inserted mid-line is a break; a field appended is evolution (D-020's law,
# applied to the bench's own output as well as the daemon's). The only value
# that moves is judged=, on the one row that gained a verdict.
contract() {
  local cls="$1" want="$2"; shift 2
  printf 'CONTRACT %s judged="%s" args="%s"' "$cls" "$want" "$*"
  if [ -n "$VARIES" ]; then
    printf ' varies="%s"' "$VARIES"
  fi
  printf '\n'
}

# A judged probe: run it, print it, and demand its exact line.
judge() {
  local cls="$1" want="$2"; shift 2
  PROBES=$((PROBES + 1)); JUDGED=$((JUDGED + 1))
  if [ "$LIST" = yes ]; then
    contract "$cls" "$want" "$@"
    return 0
  fi
  local started; started=$(date +%s)
  printf 'PROBE %s args="%s" judged="%s"\n' "$cls" "$*" "$want"
  execute "$cls" "$@"
  if [ "$ROW_RC" -ne 0 ]; then
    FAIL=$((FAIL + 1))
    echo "FAIL $cls exited $ROW_RC"
    return 0
  fi
  if printf '%s\n' "$ROW_OUT" | grep -qxF "$want"; then
    PASS=$((PASS + 1))
    echo "PASS $cls secs=$(($(date +%s) - started))"
  else
    FAIL=$((FAIL + 1))
    echo "FAIL $cls missing verdict: $want"
  fi
  settle "$cls" "$ROW_OUT" "$@"
}

# A reporting probe: run it, print it, demand only that it survives.
# $VARIES, set by `vary` for the row it wraps, is the declared exemption from
# the determinism pass — empty for every other row.
run() {
  local cls="$1"; shift
  PROBES=$((PROBES + 1))
  if [ "$LIST" = yes ]; then
    contract "$cls" '-' "$@"
    return 0
  fi
  local started; started=$(date +%s)
  printf 'PROBE %s args="%s" judged="-"\n' "$cls" "$*"
  execute "$cls" "$@"
  if [ "$ROW_RC" -ne 0 ]; then
    FAIL=$((FAIL + 1))
    echo "FAIL $cls exited $ROW_RC"
    return 0
  fi
  RAN=$((RAN + 1))
  echo "RAN $cls secs=$(($(date +%s) - started))"
  settle "$cls" "$ROW_OUT" "$@"
}

# A row that is allowed to move: the reason, then the verb it applies to.
# Setting $VARIES around the call is the whole mechanism — `settle` reads it,
# `contract` prints it, and both verbs are unaware they were wrapped.
vary() {
  VARIES="$1"; shift
  "$@"
  VARIES=''
}

# The determinism pass (#364). The probes referee the daemon; nothing referees
# the probes. The digest leash proves the WORLD is deterministic and says
# nothing about the instruments pointed at it — and an instrument that drifts
# is worse than no instrument, because it manufactures mysteries the world
# never had and the next skeptic spends a round chasing a phantom that lives in
# the coroner, not the corpse.
#
# So: the same class, the same args, the same budget, a second time, and the
# two outputs compared byte for byte. The second run's output is deliberately
# NOT reprinted — the diff is the fact, and doubling the log to say "identical"
# helps nobody. Cost is one extra run per row inside a sweep already paid for.
#
# One thing about the second run is deliberately NOT the same: it is taken
# under LC_ALL=C. Contract clause 4 has always said a probe that reaches for a
# default locale fails this sweep on the line that moved, and until #836 that
# sentence was false — both runs stood in the same shell, so the one property
# a locale can break was the one property this pass could not see. DrawMeter's
# freeze line and DistrictNeutral's catalog separators were arriving as '?' on
# any box with no locale exported, and every row here was STABLE. The hostile
# locale costs nothing (the probes are pinned to UTF-8 by matrix.Streams.utf8)
# and makes the byte compare mean what the clause says it means.
settle() {
  [ "$TWICE" = yes ] || return 0
  local cls="$1" first="$2"; shift 2
  if [ -n "$VARIES" ]; then
    EXEMPT=$((EXEMPT + 1))
    printf 'EXEMPT %s reason="%s"\n' "$cls" "$VARIES"
    return 0
  fi
  local again rc
  set +e
  again="$(LC_ALL=C java -cp out:probes/out "$cls" "$@" 2>&1)"
  rc=$?
  set -e
  if [ "$rc" -ne 0 ]; then
    # Ran, then did not: a failure of the bench AND a difference between the
    # two runs. Both counters move, because both statements are true.
    FAIL=$((FAIL + 1)); DRIFTED=$((DRIFTED + 1))
    echo "FAIL $cls second run exited $rc"
    return 0
  fi
  if [ "$again" = "$first" ]; then
    STABLE=$((STABLE + 1))
    echo "STABLE $cls"
    return 0
  fi
  DRIFTED=$((DRIFTED + 1))
  moved "$cls" "$first" "$again"
}

# The first line that moved, with its number, both sides quoted so a trailing
# space or a vanished line is visible. Pure shell reads: the two outputs are
# already in memory, and spawning a process per line would cost more than the
# rerun that produced them. (A difference in trailing blank lines alone is
# invisible here — command substitution strips those before the compare.)
moved() {
  local cls="$1" n=0 a b ea eb
  exec 3<<<"$2"
  exec 4<<<"$3"
  while :; do
    a=''; b=''; ea=0; eb=0
    if ! IFS= read -r a <&3; then ea=1; fi
    if ! IFS= read -r b <&4; then eb=1; fi
    if [ "$ea" -eq 1 ] && [ "$eb" -eq 1 ]; then break; fi
    n=$((n + 1))
    if [ "$a" != "$b" ]; then
      printf 'DRIFT %s line=%d a="%s" b="%s"\n' "$cls" "$n" "$a" "$b"
      break
    fi
  done
  exec 3<&- 4<&-
}

# Clause 5 of the probe contract, instrumented (#818):
#
#   Outside the build. Nothing under probes/ is compiled into the daemon.
#   src/ must build and --selftest must pass with this directory deleted.
#
# Until now that clause was enforced by nobody having broken it. CI compiles
# src/, then compiles probes/ against it, and never once asks whether the first
# step still stands when the second directory is not there.
#
# The instrument is the clause read literally: take a copy of the tree, delete
# probes/ from the COPY, build src/ and selftest there. Two properties matter
# more than the three commands. First, the copy comes from `git archive HEAD`,
# so the check judges a pinned tree (clause 6) and never the working one —
# deleting probes/ in place to test whether probes/ is needed is how you lose
# an afternoon's uncommitted work to a lock. Second, everything happens under
# mktemp: clause 1 forbids the bench from mutating the tree it is pointed at,
# and a check that breaks the contract it is checking is not a check.
#
# A tree with no .git — the pinned form the README prescribes for evidence
# runs, which is an extracted tarball — cannot be archived. Rather than fail
# there, the copy falls back to the worktree and says so on its own line, so
# the reader always knows which tree reached the verdict.
clause5() {
  local work tmp from sha started rc log builds self verdict
  started=$(date +%s)
  tmp="${TMPDIR:-/tmp}"
  work="$(mktemp -d "${tmp%/}/bench-clause5.XXXXXX")"
  if git rev-parse --verify HEAD >/dev/null 2>&1; then
    from=archive
    sha="$(git rev-parse HEAD)"
    git archive HEAD | tar -x -C "$work"
  else
    from=worktree
    sha='-'
    tar -cf - --exclude './probes' --exclude './probes/*' \
              --exclude './out' --exclude './.git' --exclude './.git/*' . \
      | tar -xf - -C "$work"
  fi
  rm -rf "$work/probes"

  printf 'CLAUSE5 source=%s sha=%s probes_present=%s ticks=%s tree=%s\n' \
    "$from" "$sha" "$([ -e "$work/probes" ] && echo yes || echo no)" \
    "$TICKS" "$work"

  builds=no
  self='-'
  log="$work/clause5.log"
  set +e
  ( cd "$work" && javac -encoding UTF-8 --release 17 -d out $(find src -name '*.java') ) >"$log" 2>&1
  rc=$?
  set -e
  if [ "$rc" -eq 0 ]; then
    builds=yes
    set +e
    ( cd "$work" && java -cp out matrix.Main --selftest --ticks "$TICKS" ) >>"$log" 2>&1
    rc=$?
    set -e
    # Exit code AND the greppable line, the same pair CI's lock 2 trusts: a
    # selftest that exits 0 without printing its verdict has not passed.
    if [ "$rc" -eq 0 ] && grep -q '^SELFTEST OK ' "$log"; then self=OK; else self=FAIL; fi
  fi

  if [ "$builds" = yes ] && [ "$self" = OK ]; then
    verdict=BENCH_STANDS_ALONE
  else
    verdict=BENCH_ENTANGLED
    echo "--- the deleted-probes tree said: ---"
    tail -20 "$log"
    echo "--- tree kept for reading: $work ---"
  fi
  printf 'CLAUSE5 secs=%s\n' "$(($(date +%s) - started))"
  printf 'BENCH clause5 src_builds=%s selftest=%s VERDICT %s\n' "$builds" "$self" "$verdict"
  [ "$verdict" = BENCH_STANDS_ALONE ] || return 1
  rm -rf "$work"
}

if [ "$LIST" = yes ]; then
  table
  echo "CONTRACT probes=$PROBES judged=$JUDGED ticks=$TICKS"
  exit 0
fi

# The clause-5 check is its own errand: it builds a different tree from the one
# the sweep builds, and it runs no probe. It answers alone.
if [ "$WITHOUT" = yes ]; then
  if clause5; then exit 0; else exit 1; fi
fi

SWEEP_START=$(date +%s)
if [ "$BUILD" = yes ]; then
  # The bench compiles against the daemon it dissects, from nothing, with the
  # same two lines the workflow and the release script use. Probes stay outside
  # the build (contract clause 5): src/ never sees probes/.
  javac -encoding UTF-8 --release 17 -d out $(find src -name '*.java')
  javac -encoding UTF-8 --release 17 -cp out -d probes/out probes/*.java
  echo "BUILD daemon+bench ok secs=$(($(date +%s) - SWEEP_START))"
fi

table

echo "BENCH probes=$PROBES judged=$JUDGED pass=$PASS fail=$FAIL ran=$RAN" \
     "ticks=$TICKS secs=$(($(date +%s) - SWEEP_START))" \
     "VERDICT $([ "$FAIL" -eq 0 ] && echo BENCH_GREEN || echo BENCH_RED)"

# Two verdicts, because they are two facts: the world can be perfectly
# deterministic while its instruments are not, and the sweep that noticed the
# second must not report it as the first. probes = stable + drift + exempt.
if [ "$TWICE" = yes ]; then
  echo "BENCH determinism probes=$PROBES stable=$STABLE drift=$DRIFTED exempt=$EXEMPT" \
       "VERDICT $([ "$DRIFTED" -eq 0 ] && echo INSTRUMENTS_STABLE || echo INSTRUMENTS_DRIFTED)"
fi

[ "$FAIL" -eq 0 ] && [ "$DRIFTED" -eq 0 ]
