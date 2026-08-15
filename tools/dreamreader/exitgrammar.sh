#!/usr/bin/env bash
# tools/dreamreader/exitgrammar.sh — the teleprinter's exit grammar, checked (#1011)
#
# Usage: bash tools/dreamreader/exitgrammar.sh
#
# The reader publishes an exit grammar in three places — its --help last lines,
# its class Javadoc, and its row in tools/README.md. Published is not obeyed:
# before this suite existed, five different conditions reached exit 2 and three
# reached exit 1, so the one number the tool hands to whoever reads it could not
# separate REFUSED from ABSENT from DRIFTED. The obvious use of the tool is a
# sweep — render every name in a roster, skip the ones this seed did not grow —
# and that loop read 2 as "not in this seed" and walked past a misspelled flag
# in silence, having rendered nobody.
#
# One invocation per code, and the row prints what it wanted beside what it got.
# Every fixture is built here in a temp directory: the suite blesses its own
# golden page from a fresh render and drifts a copy of it, so it judges the
# GRAMMAR and never the blessing. tools/dreamreader/golden/ is lock 11's
# subject, and a suite that also failed when the world's sentences moved would
# report that finding twice under the wrong name.
#
# The reader is compiled here too, into the temp directory, against the daemon's
# out/ — the grammar under test is the one in the tree, not whatever class file
# a previous build left in tools/dreamreader/out/. Needs no token and no
# network: it renders a 600-tick day eleven times and reads numbers.
#
# Exit 0 when every case holds, 1 when any case fails or the build is not there.

set -euo pipefail

ROOT="$(git rev-parse --show-toplevel 2>/dev/null || { cd "$(dirname "$0")/../.." && pwd; })"
cd "$ROOT"

if ! command -v javac >/dev/null; then
  echo "FATAL javac is not on PATH; the suite compiles the reader it judges." >&2
  exit 1
fi
if [ ! -f out/matrix/Main.class ]; then
  echo "FATAL the daemon is not built; tools/ compiles against out/." >&2
  echo "      javac -encoding UTF-8 --release 17 -d out \$(find src -name '*.java')" >&2
  exit 1
fi

TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

javac -encoding UTF-8 --release 17 -cp out -d "$TMP/classes" tools/dreamreader/*.java

CP="out:$TMP/classes"
# The pilot lock 11 renders, at a day short enough to run eleven times: the
# suite needs one name the record holds and one it does not, and nothing else
# about the world.
PILOT="Otto Aydin"
SEED=1
TICKS=600

BLESSED="$TMP/blessed.page"
DRIFTED="$TMP/drifted.page"
ABSENT="$TMP/no-such-golden.page"
BESIDE="$TMP/beside.page"

java -cp "$CP" DreamReader --pilot "$PILOT" --seed "$SEED" --ticks "$TICKS" > "$BLESSED"
{ echo "a line the blessing never had"; cat "$BLESSED"; } > "$DRIFTED"

PASS=0
FAIL=0

# case_ <name> <wanted exit> <file that must exist after, or -> <args...>
case_() {
  local name="$1" want="$2" wrote="$3"; shift 3
  local got note="" ok=1
  set +e
  java -cp "$CP" DreamReader "$@" >"$TMP/out" 2>"$TMP/err"
  got=$?
  set -e
  [ "$got" = "$want" ] || ok=0
  if [ "$wrote" != "-" ]; then
    if [ -s "$wrote" ]; then
      note="  (--out wrote $(wc -c < "$wrote" | tr -d ' ') bytes)"
    else
      ok=0
      note="  (--out wrote nothing)"
    fi
  fi
  if [ "$ok" = 1 ]; then
    PASS=$((PASS + 1))
    printf 'EXIT %-28s want=%s got=%s OK%s\n' "$name" "$want" "$got" "$note"
  else
    FAIL=$((FAIL + 1))
    printf 'EXIT %-28s want=%s got=%s FAIL%s\n' "$name" "$want" "$got" "$note"
    head -1 "$TMP/err" | sed 's/^/       | /'
  fi
}

case_ day_rendered              0 - --pilot "$PILOT"      --seed "$SEED" --ticks "$TICKS"
case_ nobody_by_that_name       2 - --pilot "Nobody Here" --seed "$SEED" --ticks "$TICKS"
case_ unknown_flag              3 - --pilot "$PILOT"      --seed "$SEED" --tick "$TICKS"
case_ unknown_voice             3 - --pilot "$PILOT"      --seed "$SEED" --voice warm
case_ dangling_flag             3 - --pilot "$PILOT"      --seed "$SEED" --ticks
case_ no_pilot                  3 -                       --seed "$SEED" --ticks "$TICKS"
case_ unreadable_number         3 - --pilot "$PILOT"      --seed "$SEED" --ticks twelve
# A budget of zero was the last way to get a confident success out of an empty
# run (#1112): the reader folded a day of nothing and exited 0, the grammar's
# word for "a day rendered". Negative is the same refusal one step further, and
# it is listed separately because a guard written as `== 0` passes it.
case_ zero_ticks                3 - --pilot "$PILOT"      --seed "$SEED" --ticks 0
case_ negative_ticks            3 - --pilot "$PILOT"      --seed "$SEED" --ticks -5
# --seed keeps the whole of long, and this row is what stops the guard above
# from spreading to a flag that does not want it: seed 0 is a universe, and the
# answer that comes back is 2 — "nobody by that name in THAT world", the pilot
# simply is not born there — and not 3, which would mean the flag was refused.
# The distinction is the whole row: a guard copied onto --seed would print
# usage here instead of reading a real and empty universe.
case_ zero_seed                 2 - --pilot "$PILOT"      --seed 0        --ticks "$TICKS"
case_ golden_ok                 0 - --pilot "$PILOT"      --seed "$SEED" --ticks "$TICKS" --check-golden "$BLESSED"
case_ golden_drifted            1 - --pilot "$PILOT"      --seed "$SEED" --ticks "$TICKS" --check-golden "$DRIFTED"
case_ golden_missing            4 - --pilot "$PILOT"      --seed "$SEED" --ticks "$TICKS" --check-golden "$ABSENT"
case_ nobody_under_check_golden 2 - --pilot "Nobody Here" --seed "$SEED" --ticks "$TICKS" --check-golden "$BLESSED"
# The last row is not a code but the other half of the same silence: --out
# beside --check-golden used to be dropped on the floor by the same early exit.
case_ out_beside_check_golden   0 "$BESIDE" --pilot "$PILOT" --seed "$SEED" --ticks "$TICKS" \
                                  --out "$BESIDE" --check-golden "$BLESSED"

printf 'EXIT VERDICT %s cases=%d fail=%d\n' \
  "$([ "$FAIL" = 0 ] && printf PASS || printf FAIL)" "$((PASS + FAIL))" "$FAIL"
[ "$FAIL" = 0 ]
