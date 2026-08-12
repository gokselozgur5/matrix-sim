#!/usr/bin/env bash
# probes/bench.sh — the whole bench, one command, one verdict.
#
# Usage: probes/bench.sh [ticks]        sweep every probe (default 6000 ticks)
#        probes/bench.sh --list         print the contract table, run nothing
#        probes/bench.sh --no-build     sweep without recompiling
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
for arg in "$@"; do
  case "$arg" in
    --list) LIST=yes ;;
    --no-build) BUILD=no ;;
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
# The args are the probe's own, in its own order — the bench does not pretend
# every instrument takes the same ones. $TICKS is spent where a probe's cost
# scales with the arc; the census sweeps carry their own smaller ranges so the
# lane's wall clock stays a lane's, not a laboratory's.
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
  run   AllocMeter   42
  run   SeedAtlas    1 5 "$TICKS"
  run   CensusBeatDrift 42,7 "$TICKS"
}

PROBES=0 JUDGED=0 PASS=0 FAIL=0 RAN=0

# A judged probe: run it, print it, and demand its exact line.
judge() {
  local cls="$1" want="$2"; shift 2
  PROBES=$((PROBES + 1)); JUDGED=$((JUDGED + 1))
  if [ "$LIST" = yes ]; then
    printf 'CONTRACT %s judged="%s" args="%s"\n' "$cls" "$want" "$*"
    return 0
  fi
  local started out rc
  started=$(date +%s)
  printf 'PROBE %s args="%s" judged="%s"\n' "$cls" "$*" "$want"
  set +e
  out="$(java -cp out:probes/out "$cls" "$@" 2>&1)"
  rc=$?
  set -e
  printf '%s\n' "$out"
  if [ "$rc" -ne 0 ]; then
    FAIL=$((FAIL + 1))
    echo "FAIL $cls exited $rc"
    return 0
  fi
  if printf '%s\n' "$out" | grep -qxF "$want"; then
    PASS=$((PASS + 1))
    echo "PASS $cls secs=$(($(date +%s) - started))"
  else
    FAIL=$((FAIL + 1))
    echo "FAIL $cls missing verdict: $want"
  fi
}

# A reporting probe: run it, print it, demand only that it survives.
run() {
  local cls="$1"; shift
  PROBES=$((PROBES + 1))
  if [ "$LIST" = yes ]; then
    printf 'CONTRACT %s judged="-" args="%s"\n' "$cls" "$*"
    return 0
  fi
  local started out rc
  started=$(date +%s)
  printf 'PROBE %s args="%s" judged="-"\n' "$cls" "$*"
  set +e
  out="$(java -cp out:probes/out "$cls" "$@" 2>&1)"
  rc=$?
  set -e
  printf '%s\n' "$out"
  if [ "$rc" -ne 0 ]; then
    FAIL=$((FAIL + 1))
    echo "FAIL $cls exited $rc"
  else
    RAN=$((RAN + 1))
    echo "RAN $cls secs=$(($(date +%s) - started))"
  fi
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
[ "$FAIL" -eq 0 ]
