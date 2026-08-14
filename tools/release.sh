#!/usr/bin/env bash
# tools/release.sh — cut a phase release with the locks run, not remembered.
#
# Usage: tools/release.sh vX.Y.Z "Release title" path/to/notes.md
#        tools/release.sh --check            (run the locks, cut nothing)
#
# The script refuses to release unless the evidence is green RIGHT NOW:
# it rebuilds from a clean out/, runs --selftest and --bench, and stamps
# their live output into the release notes below your prose. A release
# whose locks cannot be reproduced at cut time is not a release — it is
# a hope with a tag on it. (Build-unit #138; process under D-030/D-039.)
#
# --check runs the same three locks and prints the evidence block a cut
# would stamp, then stops before the tag. Until it existed the only way to
# run these locks was to cut a release, so nobody ran them: lock 2 stopped
# matching the moment --selftest grew a line in front of its verdict, and
# main was unreleasable for 62 commits with every lane green (#972). The
# release preflight — main, clean tree, in step with origin — is skipped
# under --check on purpose: it guards the TAG, not the locks, and the tree
# an operator most wants to check is the dirty one in front of them.

set -euo pipefail

CHECK=0
[[ "${1:-}" == "--check" ]] && { CHECK=1; shift; }

if (( CHECK )); then
  [[ $# -eq 0 ]] || { echo "FATAL --check takes no other arguments" >&2; exit 2; }
else
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
fi

echo "== lock 1/3: clean compile"
rm -rf out
javac -encoding UTF-8 --release 17 -d out $(find src -name '*.java')

echo "== lock 2/3: selftest (digest double-run)"
# --selftest prints one line per compile-time law it asserts BEFORE its verdict
# — the retail order (#382), the hunt bound (#825), the dial gate (#882) — and
# that list grows every time a law becomes an assertion, which is a thing this
# repository does on purpose. So the verdict is found by an anchored grep over
# the whole capture instead of by matching its first line; the old glob anchored
# on whatever came first and refused a green main for 62 commits (#972). Both
# judges have to agree: the exit code CI reads, and the line the notes quote.
SELFTEST_OUT="$(java -cp out matrix.Main --selftest)" || {
  echo "$SELFTEST_OUT"
  echo "FATAL selftest failed" >&2
  exit 1
}
echo "$SELFTEST_OUT"
printf '%s\n' "$SELFTEST_OUT" | grep -qE '^SELFTEST OK ' \
  || { echo "FATAL selftest failed — exit 0 but no SELFTEST OK line" >&2; exit 1; }

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

# The evidence block, written once so --check shows the bytes a cut stamps
# rather than a second rendering of them. It carries EVERY line --selftest
# printed and not the verdict alone: each of those preamble lines is a lock
# that ran, and a block that quoted a fixed line count would go stale the next
# time a law becomes an assertion — which is the failure lock 2 above was
# shipped with.
locks_block() {
  echo ""
  echo "---"
  echo ""
  echo "## Locks at cut time (commit ${SHA:0:12})"
  echo ""
  echo '```'
  echo "$SELFTEST_OUT"
  echo "$BENCH_OUT"
  echo '```'
}

if (( CHECK )); then
  echo "== stamp preview (--check writes nothing and tags nothing)"
  locks_block
  echo "RELEASE CHECK 3/3 locks green VERDICT PASS commit=${SHA:0:12} tagged=none"
  exit 0
fi

STAMPED="$(mktemp)"
cat "$NOTES" > "$STAMPED"
locks_block >> "$STAMPED"

echo "== tag + release"
git tag -a "$VERSION" -m "$TITLE"
git push -q origin "$VERSION"
gh release create "$VERSION" --title "$TITLE" --notes-file "$STAMPED"
rm -f "$STAMPED"
echo "RELEASE OK $VERSION @ ${SHA:0:12}"
