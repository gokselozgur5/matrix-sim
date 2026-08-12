#!/usr/bin/env bash
# probes/bench.sh — the whole bench, one command, one verdict.
#
# Usage: probes/bench.sh [ticks]        sweep every probe (default 6000 ticks)
#        probes/bench.sh --list         print the contract table, run nothing
#        probes/bench.sh --no-build     sweep without recompiling
#        probes/bench.sh --twice        sweep, then run each probe a second
#                                       time and byte-compare the two outputs
#
# The bench was fifteen programs invoked by hand while CI judged four of them,
# with the expected verdict lines inlined in a workflow file most contributors
# never open. That is the wrong home for the bench's contract: the catalog is
# in probes/README.md, the verdicts are in the probes, and the list of what
# gets judged belongs next to them. This runner is that list.
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
for arg in "$@"; do
  case "$arg" in
    --list) LIST=yes ;;
    --no-build) BUILD=no ;;
    --twice) TWICE=yes ;;
    ''|*[!0-9]*) echo "FATAL unknown argument: $arg" >&2; exit 2 ;;
    *) TICKS="$arg" ;;
  esac
done

# ---------------------------------------------------------------------------
# The contract table. One row per probe:
#
#   judge <Class> '<the exact line the run must print>' [args...]
#   run   <Class> [args...]
#   vary  <Class> '<why its output may legitimately move>' [args...]
#
# The args are the probe's own, in its own order — the bench does not pretend
# every instrument takes the same ones. $TICKS is spent where a probe's cost
# scales with the arc; the census sweeps carry their own smaller ranges so the
# lane's wall clock stays a lane's, not a laboratory's.
#
# `vary` is `run` plus one thing: an exemption from the --twice determinism
# pass, with the reason stated in the row rather than skipped in silence. It is
# the rarest verb and should stay that way — an instrument whose output moves
# between two identical runs cannot be diffed across a change, which is most of
# what a bench is for. Exactly one row holds it today, and it earns it by
# measuring the JVM instead of the world.
# ---------------------------------------------------------------------------
table() {
  judge LedgerMirror 'LEDGER_ANOMALIES=0'      "$TICKS"
  judge OneTrace     'VERDICT CONTRACT_HELD'   "$TICKS"
  judge CapSentinel  'CAP_BREACHES=0'          "$TICKS"
  judge ArcBeats     'VERDICT BEATS_IN_ORDER'  "$TICKS"
  judge LinkAudit    'VERDICT CLEAN'           "$TICKS"
  judge PirateSever  'VERDICT CONTRACT_HELD'
  judge LineLint     'VERDICT GRAMMAR_HELD'    "$TICKS"
  judge FateAtlas    'VERDICT MONOCULTURE'
  run   DrawMeter    "$TICKS"
  run   ChainDump    "$TICKS"
  run   LinkTrace    "Nadia Petrov" "$TICKS"
  run   NameCensus   42
  vary  AllocMeter   'reads the JDK thread-allocation counter: the number moves with JIT, not with the world' 42
  run   SeedAtlas    1 5 "$TICKS"
  run   CensusBeatDrift 42,7 "$TICKS"
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

# A judged probe: run it, print it, and demand its exact line.
judge() {
  local cls="$1" want="$2"; shift 2
  PROBES=$((PROBES + 1)); JUDGED=$((JUDGED + 1))
  if [ "$LIST" = yes ]; then
    printf 'CONTRACT %s judged="%s" args="%s"\n' "$cls" "$want" "$*"
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
    # varies= rides at the END, and only when set: every row that is not exempt
    # prints the CONTRACT line it printed before this unit, byte for byte. A field
    # inserted mid-line is a break; a field appended is evolution (D-020's law,
    # applied to the bench's own output as well as the daemon's).
    printf 'CONTRACT %s judged="-" args="%s"' "$cls" "$*"
    if [ -n "$VARIES" ]; then
      printf ' varies="%s"' "$VARIES"
    fi
    printf '\n'
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

# A reporting probe that is allowed to move: `run`, plus a stated reason.
vary() {
  local cls="$1"; VARIES="$2"; shift 2
  run "$cls" "$@"
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
  again="$(java -cp out:probes/out "$cls" "$@" 2>&1)"
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

if [ "$LIST" = yes ]; then
  table
  echo "CONTRACT probes=$PROBES judged=$JUDGED ticks=$TICKS"
  exit 0
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
