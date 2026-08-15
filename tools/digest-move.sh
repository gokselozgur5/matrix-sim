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
AGE=no
SELFTEST=no

while [ $# -gt 0 ]; do
  case "$1" in
    --base) BASE="${2:-}"; [ -n "$BASE" ] || { echo "FATAL --base wants a ref" >&2; exit 2; }; shift 2 ;;
    --age) AGE=yes; shift ;;
    --selftest) SELFTEST=yes; shift ;;
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

# The stamp line: `# sealed: <ISO date> <commit> <where argued>`. A comment rather than a
# second payload line, because the payload rule is exactly one line and two readers
# enforce it — see the pin's own paragraph. Empty when the file carries no stamp, which
# is a state this script names rather than guesses at.
stamp_of() { grep -m1 -E '^#[[:space:]]*sealed:' "$PIN" 2>/dev/null | sed -E 's/^#[[:space:]]*sealed:[[:space:]]*//' || true; }

# --age answers "how long has this world stood" as a READ. Forty units landed against one
# sha in a single night and the aggregate was a fact nobody could state without walking
# the log (#1099) — so it is stated here, in the same grammar as every other verdict.
# Days are computed in whichever `date` dialect is present, the way balance.sh does it:
# GNU takes -d, BSD takes -j -f. A box with neither still gets the stamp and the count of
# commits, because those are the facts that do not need arithmetic.
if [ "$AGE" = yes ]; then
  [ -f "$PIN" ] || { echo "FATAL $PIN is missing — the seal has no home" >&2; exit 1; }
  stamp="$(stamp_of)"
  payload="$(payload_of "$(cat "$PIN")")" || {
    echo "FATAL $PIN does not hold exactly one payload line" >&2; exit 1; }
  sha="$(sha_of "$payload")"
  if [ -z "$stamp" ]; then
    echo "SEAL AGE sha=$sha sealed=unstamped (the pin carries no '# sealed:' line)"
    exit 1
  fi
  when="$(printf '%s\n' "$stamp" | awk '{print $1}')"
  at="$(printf '%s\n' "$stamp" | awk '{print $2}')"
  today="$(date -u +%Y-%m-%d)"
  days=unknown
  if s=$(date -u -d "$when" +%s 2>/dev/null); then
    days=$(( ( $(date -u -d "$today" +%s) - s ) / 86400 ))
  elif s=$(date -u -j -f %Y-%m-%d "$when" +%s 2>/dev/null); then
    days=$(( ( $(date -u -j -f %Y-%m-%d "$today" +%s) - s ) / 86400 ))
  fi
  # Commits since the stamped commit is a second, independent measure of the same
  # standing — a quiet week and a busy night are both "one day", and only one of them
  # is evidence. Silent when the stamped commit is not in this checkout: a shallow
  # clone knows the date and not the distance, and reporting 0 there would be a lie.
  since=unknown
  if git rev-parse --verify --quiet "$at^{commit}" >/dev/null 2>&1; then
    since="$(git rev-list --count "$at..HEAD" 2>/dev/null || echo unknown)"
  fi
  echo "SEAL AGE sha=$sha sealed=$when at=$at days=$days commits_since=$since"
  exit 0
fi

# --selftest runs every path this gate has against fixtures built out of THIS history,
# in a scratch clone, the way tools/baseline.sh builds its own (#1164).
#
# Why it did not exist until now: the gate is armed by an act — a moved pin — so walking
# its paths meant moving a seal, and moving a seal is the one thing this repository does
# on purpose and never casually. A scratch clone removes that: the fixtures are commits
# nothing here will ever push, and the real pin is never touched.
#
# Five paths, and the two that MATTER are the refusals. A gate whose only exercised path
# is the green one is #898's argument about --selftest itself: two runs of a weakened
# check agree perfectly.
if [ "$SELFTEST" = yes ]; then
  work="$(mktemp -d "${TMPDIR:-/tmp}/digest-move-selftest.XXXXXX")"
  trap 'rm -rf "$work"' EXIT
  root="$(git rev-parse --show-toplevel)"
  git clone -q --no-hardlinks "$root" "$work/repo" 2>/dev/null || {
    echo "DIGEST MOVE SELFTEST VERDICT FAIL cases=0 failed=0  (no clone; a suite of nothing is not a pass)"
    exit 1; }
  cd "$work/repo" || exit 1
  git config user.email selftest@invalid
  git config user.name selftest
  base="$(git rev-parse HEAD)"
  pass=0; fail=0

  case_() {                     # case_ <name> <want-verdict-word> <setup>
    local name="$1" want="$2" setup="$3" got
    git checkout -q -B case "$base" >/dev/null 2>&1
    git checkout -q "$base" -- "$PIN"
    eval "$setup"
    got="$(bash "$root/tools/digest-move.sh" --base "$base" 2>/dev/null | grep -oE 'VERDICT [A-Z]+' | awk '{print $2}' || true)"
    if [ "$got" = "$want" ]; then
      pass=$((pass + 1)); printf 'DIGESTMOVE case=%-26s want=%-9s got=%-9s OK\n' "$name" "$want" "$got"
    else
      fail=$((fail + 1)); printf 'DIGESTMOVE case=%-26s want=%-9s got=%-9s BROKEN\n' "$name" "$want" "${got:-<none>}"
    fi
  }

  # Helpers the cases share. The sha is read INSIDE the case, after the pin is restored,
  # so a fixture cannot be built from a value the previous case left behind.
  #
  # `sed` rewrites the sha's first four characters and nothing else: the payload keeps its
  # shape — 64 hex then a reason naming an issue — so a case tests the ARGUMENT and not
  # the file's grammar, which lock 7 already refuses on its own.
  move_pin() {
    old="$(sed -nE 's/^([0-9a-f]{64}).*/\1/p' "$PIN" | head -1)"
    new="aaaa${old:4}"
    sed -i.bak "s/^$old/$new/" "$PIN" && rm -f "$PIN.bak"
  }
  # The issue goes on its OWN line, and finding that out is half of what this suite is
  # worth. The gate matches the declaration with `grep -qxF` — exact line — and asks for
  # an issue reference anywhere in the same MESSAGE. Writing `… -> <sha> (#1164)` reads
  # perfectly and matches nothing, because the line is no longer the line. Nobody had
  # walked this path before, so nobody had discovered that its most natural spelling
  # fails; the first fixture written for it failed for exactly that reason.
  declare_it() { git commit -q -a -m "selftest: move the pin (#1164)

Declared digest move: $old -> $new"; }
  restamp() { sed -i.bak "s/^# sealed: [0-9][0-9-]*/# sealed: 1999-01-01/" "$PIN" && rm -f "$PIN.bak"; }
  # The spelling the FATAL text used to invite: paste the line, add the issue to it. It is
  # refused, and #1166 is the unit that made the message say so. This case is the
  # regression test for the MESSAGE — the artifact that was wrong — so it asserts the
  # refusal rather than treating it as a bug to be fixed in the matcher. Loosening the
  # match would put prose into a ledger `git log | grep` is supposed to print clean.
  declare_on_one_line() { git commit -q -a -m "selftest: move the pin

Declared digest move: $old -> $new (#1166)"; }

  case_ no-move-is-silent       NONE     "true"
  case_ moved-and-unargued      UNARGUED "move_pin"
  case_ issue-on-the-same-line  UNARGUED "move_pin && restamp && declare_on_one_line"
  case_ moved-argued-restamped  ARGUED   "move_pin && restamp && declare_it"
  case_ moved-stamp-left-behind UNARGUED "move_pin && declare_it"

  cd "$root" || exit 1
  printf 'DIGEST MOVE SELFTEST VERDICT %s cases=%d failed=%d\n' \
    "$([ "$fail" = 0 ] && printf PASS || printf FAIL)" "$((pass + fail))" "$fail"
  [ "$fail" = 0 ]
  exit $?
fi

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
  echo "      Both shas in full, on one line, and NOTHING ELSE ON THAT LINE — the issue" >&2
  echo "      number goes in the subject or a later paragraph, anywhere in the same" >&2
  echo "      message. Appending it here (\"-> <sha> (#N)\") is the natural reading of" >&2
  echo "      this advice and it is refused: the declaration is matched as an exact" >&2
  echo "      line, so that" >&2
  echo "" >&2
  echo "          git log --format=%B | grep '^Declared digest move:'" >&2
  echo "" >&2
  echo "      prints every head this repository has ever had and nothing else (#1166)." >&2
  echo "      Naming what you moved FROM is the point: a pin regenerated to clear a red" >&2
  echo "      build gets written without ever looking." >&2
  fail=1
fi

printf '%s\n' "$now_payload" | grep -qE '#[0-9]+' || {
  echo "FATAL the payload line in $PIN names no issue in its reason field." >&2
  echo "      The seal's home must point at the argument: '<sha>  <why> (#N)'." >&2
  fail=1; }

# The stamp moves with the seal, or it does not mean anything (#1099). A date nobody has
# to update reads as "this world has stood since 2026-08-13" forever, and the first
# person it misleads is whoever trusted --age instead of walking the log — which is the
# whole reason the line exists. Checked only on a MOVE: a unit that leaves the bytes
# alone owes nothing here, exactly as it owes no paragraph.
now_stamp="$(stamp_of)"
base_stamp="$(printf '%s\n' "$base_file" | grep -m1 -E '^#[[:space:]]*sealed:' | sed -E 's/^#[[:space:]]*sealed:[[:space:]]*//' || true)"
if [ -z "$now_stamp" ]; then
  echo "FATAL the seal moved and $PIN carries no '# sealed:' line." >&2
  echo "      Stamp it: '# sealed: $(date -u +%Y-%m-%d) <this commit> <the PR>'." >&2
  fail=1
elif [ "$now_stamp" = "$base_stamp" ]; then
  echo "FATAL the seal moved and its '# sealed:' stamp did not." >&2
  echo "      base: $base_stamp" >&2
  echo "      head: $now_stamp" >&2
  echo "      A date that survives the move it dates is wrong from the moment it is read." >&2
  fail=1
fi

if [ "$fail" != 0 ]; then
  echo "DIGEST MOVE VERDICT UNARGUED from=$base_sha to=$now_sha base=$BASE"
  exit 1
fi

echo "DIGEST MOVE VERDICT ARGUED from=$base_sha to=$now_sha by=$(git rev-parse --short "$argued_by")"
exit 0
