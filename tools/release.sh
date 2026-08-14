#!/usr/bin/env bash
# tools/release.sh — cut a phase release with the locks run, not remembered.
#
# Usage: tools/release.sh vX.Y.Z "Release title" path/to/notes.md
#
# The script refuses to release unless the evidence is green RIGHT NOW:
# it rebuilds from a clean out/, runs --selftest and --bench, and stamps
# their live output into the release notes below your prose. A release
# whose locks cannot be reproduced at cut time is not a release — it is
# a hope with a tag on it. (Build-unit #138; process under D-030/D-039.)

set -euo pipefail

VERSION="${1:?usage: tools/release.sh vX.Y.Z \"Title\" notes.md}"
TITLE="${2:?usage: tools/release.sh vX.Y.Z \"Title\" notes.md}"
NOTES="${3:?usage: tools/release.sh vX.Y.Z \"Title\" notes.md}"

[[ "$VERSION" =~ ^v[0-9]+\.[0-9]+\.[0-9]+$ ]] || { echo "FATAL version must be vX.Y.Z" >&2; exit 2; }
[[ -f "$NOTES" ]] || { echo "FATAL notes file not found: $NOTES" >&2; exit 2; }

BRANCH="$(git branch --show-current)"
[[ "$BRANCH" == "main" ]] || { echo "FATAL releases cut from main only (on: $BRANCH)" >&2; exit 2; }
[[ -z "$(git status --porcelain)" ]] || { echo "FATAL working tree not clean" >&2; exit 2; }
git fetch -q origin main
[[ "$(git rev-parse HEAD)" == "$(git rev-parse origin/main)" ]] || { echo "FATAL local main != origin/main" >&2; exit 2; }

echo "== lock 1/3: clean compile"
rm -rf out
javac -encoding UTF-8 --release 17 -d out $(find src -name '*.java')

echo "== lock 2/3: selftest (digest double-run)"
SELFTEST_LINE="$(java -cp out matrix.Main --selftest)"
echo "$SELFTEST_LINE"
[[ "$SELFTEST_LINE" == SELFTEST\ OK* ]] || { echo "FATAL selftest failed" >&2; exit 1; }

echo "== lock 3/3: bench (D-027 budget table)"
# Both rows are rates since #384 (>= 100 ticks/s steady and over the arc), so a
# loaded box degrades proportionally instead of falling off a 30 s cliff. A red
# row is therefore a real signal — but a box under heavy enough external load can
# still push a healthy tree under the floor, and "FATAL bench failed" alone sends
# the operator hunting a regression that is not there. Say what the row means.
BENCH_OUT="$(java -cp out matrix.Main --bench)" || {
  echo "$BENCH_OUT"
  echo "FATAL bench failed" >&2
  echo "  Both rows are ticks/s against a floor of 100 (#384); ref_box_s=30 on the arc" >&2
  echo "  row is D-027's reference-box expectation, not the verdict." >&2
  echo "  That box is a QUIET 2-core x86-64 cloud VM (Debian, OpenJDK 17, single-" >&2
  echo "  threaded), where the full arc runs ~15 s. Releases are cut on a quiet box:" >&2
  echo "  if this one is busy, re-run the bench idle before believing the red." >&2
  exit 1
}
echo "$BENCH_OUT"

SHA="$(git rev-parse HEAD)"
STAMPED="$(mktemp)"
cat "$NOTES" > "$STAMPED"
{
  echo ""
  echo "---"
  echo ""
  echo "## Locks at cut time (commit ${SHA:0:12})"
  echo ""
  echo '```'
  echo "$SELFTEST_LINE"
  echo "$BENCH_OUT"
  echo '```'
} >> "$STAMPED"

echo "== tag + release"
git tag -a "$VERSION" -m "$TITLE"
git push -q origin "$VERSION"
gh release create "$VERSION" --title "$TITLE" --notes-file "$STAMPED"
rm -f "$STAMPED"
echo "RELEASE OK $VERSION @ ${SHA:0:12}"
