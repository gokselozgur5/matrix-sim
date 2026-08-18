#!/usr/bin/env bash
# tools/release.sh — cut a phase release with the locks run, not remembered.
#
# Usage: tools/release.sh vX.Y.Z "Release title" path/to/notes.md
#        tools/release.sh --check            (run the locks, cut nothing)
#        tools/release.sh --help | -h        (print this clause, and stop)
#
# The script refuses to release unless the evidence is green RIGHT NOW: it
# rebuilds from a clean out/, runs the daemon's own locks — both selftests,
# --bench, the probe sweep and the pinned seal — and stamps their live output
# into the release notes below your prose. A release whose locks cannot be
# reproduced at cut time is not a release — it is a hope with a tag on it.
# (Build-unit #138; process under D-030/D-039.)
#
# The seven below are locks.yml's 1 through 7, run in its order, because the
# gate on the artifact that LEAVES the repository must not be the weaker of the
# two. It was: until #1052 a cut ran compile, --selftest at the standard budget
# and --bench, and never read .github/canonical-digest, never ran the full arc
# and never swept the probes — so a tree whose seal had moved undeclared, or
# whose sweep was red, could be tagged and published while a pull request
# carrying the same bytes was being refused. Which locks a release still skips,
# and why, is written down in tools/README.md instead of left to a grep.
#
# --check runs those same seven and prints the evidence block a cut would
# stamp, then stops before the tag. Until it existed the only way to run these
# locks was to cut a release, so nobody ran them: lock 2 stopped matching the
# moment --selftest grew a line in front of its verdict, and main was
# unreleasable for 62 commits with every lane green (#972). It was then a path
# with no runner of its own, which is how the divergence above opened unseen —
# so locks.yml now runs --check and judges its verdict line, and the release
# path is exercised by every pull request rather than by the next release.
# The release preflight — main, clean tree, in step with origin — is skipped
# under --check on purpose: it guards the TAG, not the locks, and the tree
# an operator most wants to check is the dirty one in front of them.

set -euo pipefail

CHECK=0
# THE DOOR IS READ BEFORE THE POSITIONAL ARGUMENTS (#1527). Below this the first argument is a version string, so an unread --help is refused as "version must be vX.Y.Z" — a message about the wrong thing.
case "${1:-}" in
  -h|--help) awk 'NR==1 {next} !/^#/ {exit} /^#$/ {if (++blank == 2) exit} {print}' "$0"; exit 0 ;;
esac

[[ "${1:-}" == "--check" ]] && { CHECK=1; shift; }

# AN UNKNOWN FLAG IS A REFUSAL, AND THIS TOOL SPENT 1 FOR IT (#1546). Below this the
# first argument is a VERSION, so `--nonsense` was refused as `FATAL version must be
# vX.Y.Z` — a refusal wearing the wrong code and a message about the wrong thing.
# `--check` above already spends 2 for an extra argument; this is the same refusal
# for the flag that opens the invocation.
case "${1:-}" in
  --*) echo "FATAL unknown flag: $1 (this tool takes vX.Y.Z \"Title\" notes.md, or --check)" >&2; exit 2 ;;
esac

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

echo "== lock 1/7: clean compile"
rm -rf out
javac -encoding UTF-8 --release 17 -d out $(find src -name '*.java')

echo "== lock 2/7: selftest (digest double-run)"
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

echo "== lock 3/7: selftest, full arc (6,000 ticks)"
# The budget above is 2,000 ticks, and the arc's last third is where the finale
# lives — overflow, flatline, peace, reboot, the door. A hardcoded 2,000 left
# all of it unasserted in CI once, which is why locks.yml carries the same run
# twice at two budgets; a cut that only ever asked the short question would ship
# the finale on nobody's word. Same anchored grep as lock 2, for lock 2's reason.
SELFTEST_ARC_OUT="$(java -cp out matrix.Main --selftest --ticks 6000)" || {
  echo "$SELFTEST_ARC_OUT"
  echo "FATAL selftest failed at 6,000 ticks" >&2
  exit 1
}
echo "$SELFTEST_ARC_OUT"
printf '%s\n' "$SELFTEST_ARC_OUT" | grep -qE '^SELFTEST OK ' \
  || { echo "FATAL selftest failed at 6,000 ticks — exit 0 but no SELFTEST OK line" >&2; exit 1; }

echo "== lock 4/7: bench (D-027 budget table)"
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

echo "== lock 5/7: compile probes (outside the daemon build)"
# Clean, like lock 1 and for lock 1's reason: a stale class from a probe this
# tree no longer carries would be swept and judged as if it were still here.
# probes/ never enters src/'s build — that is the bench's clause 5 — so this is
# a second javac against out/, not a wider first one (D-009 holds either way).
rm -rf probes/out
javac -encoding UTF-8 --release 17 -cp out -d probes/out probes/*.java

echo "== lock 6/7: probe sweep (probes/bench.sh — every judged row, 6,000 ticks)"
# The sweep's own table is the contract — one exact-line grep per judged probe,
# kept beside the probes so adding a lock is a row and not a YAML edit (#880).
# --no-build reuses locks 1 and 5 rather than compiling the tree a third time.
# Its 1,100 lines are printed only when it goes red, where the failing rows are
# the reason to have them; green, the summary line is the evidence, and it is
# the line the notes quote. Same anchored shape locks.yml judges it by, so a
# sweep of nothing (judged=0) cannot pass here either.
SWEEP_OUT="$(bash probes/bench.sh --no-build)" || {
  printf '%s\n' "$SWEEP_OUT"
  echo "FATAL probe sweep failed" >&2
  exit 1
}
SWEEP_LINE="$(printf '%s\n' "$SWEEP_OUT" | grep -E '^BENCH .* judged=[1-9][0-9]* .* fail=0 .* VERDICT BENCH_GREEN$' || true)"
[[ -n "$SWEEP_LINE" ]] || {
  printf '%s\n' "$SWEEP_OUT"
  echo "FATAL the sweep printed no green summary line" >&2
  exit 1
}
echo "$SWEEP_LINE"

echo "== lock 7/7: canonical digest (seed 42, 6,000 ticks)"
# The one question locks 2 and 3 cannot ask: they compare a run to another run
# of the same binary, and a seal that got SMALLER weakens both sides equally.
# Only a number from outside the run refuses that, and .github/canonical-digest
# is where that number lives. The pin's grammar — exactly one payload line, a
# reason field beside the sha — is lock 7's business in locks.yml; here the
# question is the seal itself, so the read is the payload's first field and the
# compare is a whole-string one, never a prefix.
PIN=.github/canonical-digest
[[ -f "$PIN" ]] || { echo "FATAL $PIN is missing — the seal has no home" >&2; exit 1; }
SEAL="$(grep -vE '^[[:space:]]*(#|$)' "$PIN" | awk 'NF { print $1; exit }' || true)"
printf '%s\n' "$SEAL" | grep -qxE '[0-9a-f]{64}' \
  || { echo "FATAL $PIN does not open with a 64-hex sha: ${SEAL:-<nothing>}" >&2; exit 1; }
DIGEST_WANT="DIGEST tick=6000 sha=$SEAL"
DIGEST_OUT="$(java -cp out matrix.Main --headless --ticks 6000 --seed 42 | grep -E '^DIGEST tick=6000 ' || true)"
printf 'pinned  %s\nprinted %s\n' "$DIGEST_WANT" "${DIGEST_OUT:-<no DIGEST tick=6000 line at all>}"
[[ "$DIGEST_OUT" == "$DIGEST_WANT" ]] || {
  echo "FATAL the seal moved — a release is the wrong place to find that out." >&2
  echo "  If the move is deliberate it is declared in $PIN, in a commit that says" >&2
  echo "  so, and it lands through a pull request — not in the tree being tagged." >&2
  exit 1
}

SHA="$(git rev-parse HEAD)"

# The evidence block, written once so --check shows the bytes a cut stamps
# rather than a second rendering of them. It carries EVERY line --selftest
# printed and not the verdict alone: each of those preamble lines is a lock
# that ran, and a block that quoted a fixed line count would go stale the next
# time a law becomes an assertion — which is the failure lock 2 above was
# shipped with. The sweep is the one lock quoted by its summary instead of in
# full: its rows are a page and a half, and its own file holds their contract.
# The seal is quoted as the daemon printed it, so the notes carry the sha the
# release ships rather than a claim that it matched.
locks_block() {
  echo ""
  echo "---"
  echo ""
  echo "## Locks at cut time (commit ${SHA:0:12})"
  echo ""
  echo '```'
  echo "$SELFTEST_OUT"
  echo "$SELFTEST_ARC_OUT"
  echo "$BENCH_OUT"
  echo "$SWEEP_LINE"
  echo "$DIGEST_OUT"
  echo '```'
}

if (( CHECK )); then
  echo "== stamp preview (--check writes nothing and tags nothing)"
  locks_block
  echo "RELEASE CHECK 7/7 locks green VERDICT PASS commit=${SHA:0:12} tagged=none"
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
