#!/usr/bin/env bash
# tools/digest-move.sh — a seal that moves arrives with its argument, or it does not
# arrive (#884, RULING #212).
#
# Usage: tools/digest-move.sh [--base <ref>]     default base: origin/main
#
# WHAT THIS EXISTS FOR. Lock 7 refuses an UNDECLARED move: the run prints a sha the pin
# does not hold, and the build goes red. There is exactly one way to make it green again
# and it takes four seconds — paste the printed sha into .github/canonical-digest. That
# keystroke is identical whether the mover reasoned about the move or was merely tired of
# the red X, and afterwards the tree cannot tell the two apart. #884 named the failure
# that follows: every red build answered by regenerating the pin, the seal following main
# wherever it goes, and a control group that is a transcript with a longer changelog.
#
# So this asks the question lock 7 cannot: the pin moved — was that a decision?
#
# THE MECHANISM. The gate is armed by the act it governs. If the pin's sha is the same at
# HEAD as at the base, this prints NONE and exits 0; a unit that does not touch the
# world's bytes never meets it. If the sha differs, the branch must carry a commit whose
# message holds the line
#
#     Declared digest move: <old sha> -> <new sha>
#
# with both shas in full and an issue reference in the same message, and the pin's own
# reason field must name an issue too. Both shas on one line is the load-bearing part:
# it makes the chain of heads a query rather than an archaeology dig —
#
#     git log --format=%B | grep '^Declared digest move:'
#
# prints every head this repository has ever had, in order. Before this, recovering that
# list meant rebuilding old commits one at a time; #899 did exactly that, five times.
#
# WHY THE COMMIT AND NOT A LEDGER FILE. .github/canonical-digest already argues that the
# move history belongs to git because git "cannot drift from the value it explains". A
# second file holding the same shas would drift the first time someone edited one of them.
# The commit message is welded to the diff that moves the bytes: `git log -p --
# .github/canonical-digest` shows the argument and the bytes it explains as one object.
#
# WHAT IT DOES NOT DO. It does not judge whether the argument is a GOOD argument — no
# script can, and pretending otherwise would be the well-argued lie this gate exists to
# make harder. It judges that an argument was written, that it names what it moved FROM
# (which cannot be written without looking, and looking is the whole intervention), and
# that it points at a thread where the reasoning lives. A crew can still walk through this
# gate on purpose. That is the design: on purpose is the only way through.

set -euo pipefail

PIN=.github/canonical-digest
BASE=origin/main

while [ $# -gt 0 ]; do
  case "$1" in
    --base) BASE="${2:-}"; [ -n "$BASE" ] || { echo "FATAL --base wants a ref" >&2; exit 2; }; shift 2 ;;
    -h|--help) sed -n '2,5p' "$0"; exit 0 ;;
    *) echo "FATAL unknown argument: $1" >&2; exit 2 ;;
  esac
done

git rev-parse --git-dir >/dev/null 2>&1 || { echo "FATAL not inside a git worktree" >&2; exit 2; }
cd "$(git rev-parse --show-toplevel)" || exit 2

# The payload rule is .github/canonical-digest's own, restated here because this script
# reads the file too and a disagreement between the two readers would be worse than
# either being wrong alone: exactly one non-comment, non-blank line; <64 lowercase hex>
# then the reason it is that value.
payload_of() { # payload_of <text> -> the single payload line, or empty on a malformed file
  local text="$1" body n
  body="$(printf '%s\n' "$text" | grep -vE '^[[:space:]]*(#|$)' || true)"
  n="$(printf '%s\n' "$body" | grep -c '[^[:space:]]' || true)"
  [ "$n" = 1 ] || return 1
  printf '%s\n' "$body" | grep '[^[:space:]]'
}

sha_of() { printf '%s\n' "$1" | awk '{print $1}'; }

[ -f "$PIN" ] || { echo "FATAL $PIN is missing — the seal has no home" >&2; exit 1; }

git rev-parse --verify --quiet "$BASE^{commit}" >/dev/null || {
  echo "FATAL base ref '$BASE' does not resolve here." >&2
  echo "      In a fresh clone that is 'git fetch origin main'; pass --base <ref> to name another." >&2
  exit 2
}

# HEAD-side value comes from the working tree, so the verdict is the same before and after
# you commit the pin — you find out you owe a paragraph while you can still write one.
now_payload="$(payload_of "$(cat "$PIN")")" || {
  echo "FATAL $PIN does not hold exactly one payload line — lock 7 will refuse it too" >&2; exit 1; }
now_sha="$(sha_of "$now_payload")"
printf '%s\n' "$now_sha" | grep -qxE '[0-9a-f]{64}' || {
  echo "FATAL $PIN does not open with a 64-hex sha: $now_sha" >&2; exit 1; }

if ! base_file="$(git show "$BASE:$PIN" 2>/dev/null)"; then
  echo "DIGEST MOVE VERDICT NEW seal=$now_sha base=$BASE"
  echo "note: $PIN does not exist at the base — the pin is being introduced, not moved"
  exit 0
fi

base_payload="$(payload_of "$base_file")" || {
  echo "FATAL $PIN is malformed at $BASE — cannot tell what the seal was" >&2; exit 1; }
base_sha="$(sha_of "$base_payload")"

if [ "$now_sha" = "$base_sha" ]; then
  echo "DIGEST MOVE VERDICT NONE seal=$now_sha base=$BASE"
  exit 0
fi

want="Declared digest move: $base_sha -> $now_sha"
fail=0
argued_by=""

# Any commit in the range may carry the paragraph: a mover who writes the pin in one
# commit and the argument in the next has still argued, and refusing that would only
# teach people to squash for the checker rather than for the reader.
range="$(git rev-list "$BASE..HEAD" || true)"
for c in $range; do
  msg="$(git log -1 --format=%B "$c")"
  printf '%s\n' "$msg" | grep -qxF "$want" || continue
  printf '%s\n' "$msg" | grep -qE '#[0-9]+' || {
    echo "FATAL commit $(git rev-parse --short "$c") declares the move and names no issue." >&2
    echo "      The paragraph must point at the thread where the reasoning lives." >&2
    fail=1; continue; }
  argued_by="$c"
  break
done

if [ -z "$argued_by" ] && [ "$fail" = 0 ]; then
  echo "FATAL the seal moved and no commit on this branch declares it." >&2
  echo "      Put this line in the commit message that moves $PIN, with an issue number:" >&2
  echo "" >&2
  echo "          $want" >&2
  echo "" >&2
  echo "      Both shas in full, on one line. Naming what you moved FROM is the point: a" >&2
  echo "      pin regenerated to clear a red build gets written without ever looking." >&2
  fail=1
fi

printf '%s\n' "$now_payload" | grep -qE '#[0-9]+' || {
  echo "FATAL the payload line in $PIN names no issue in its reason field." >&2
  echo "      The seal's home must point at the argument: '<sha>  <why> (#N)'." >&2
  fail=1; }

if [ "$fail" != 0 ]; then
  echo "DIGEST MOVE VERDICT UNARGUED from=$base_sha to=$now_sha base=$BASE"
  exit 1
fi

echo "DIGEST MOVE VERDICT ARGUED from=$base_sha to=$now_sha by=$(git rev-parse --short "$argued_by")"
exit 0
