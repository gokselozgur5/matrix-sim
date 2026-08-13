#!/usr/bin/env bash
# tools/baseline.sh — does this PR's evidence know which world it was measured in? (#822)
#
# Usage: tools/baseline.sh <pr-body-file> <base-sha>
#        tools/baseline.sh --selftest        run every case against this history
#
# THE FINDING THIS EXISTS FOR. PR #207 shipped a full evidence table — a 196-tick
# park at seed 5, a refusal at 4265, an ECO dip at 5300 — and none of it
# reproduced at HEAD. The numbers were true when measured. Between measurement
# and merge, #205 landed and moved the world underneath them. Every lock we own
# passed, because our locks check the CODE against itself and never the EVIDENCE
# against the tree it is about to land on.
#
# So this is the one lock that judges a claim rather than a compilation. It asks
# a PR a single question — *which main were you measuring?* — and then checks
# whether that main is still the one underneath it.
#
# WHY IT WARNS AND DOES NOT FAIL, MOSTLY. On a day when a dozen crews merge into
# one main, drift is the normal condition, not the exception; a check that fails
# on drift would be red all day and would teach the crew to ignore it, which is
# strictly worse than no check. So drift is reported, named, and left to the
# author's judgement: most drift does not touch most evidence, and only the
# author knows whether these particular commits touch these particular numbers.
#
# THE ONE PLACE IT FAILS. A PR that moves the DIGEST seal quotes a 'before' as
# the whole basis of its claim. If that 'before' is not the tree's actual
# before, the claim is not weakened, it is unverifiable by construction: nobody
# can ever check that the move was the move that was declared. That is not
# drift. That is a lock with nothing behind it, and it fails. Whether the seal
# moved is read from .github/canonical-digest here versus at the base — the same
# place lock 9 reads it — and never from a field the author fills in, because a
# self-report that ships defaulted to 'no' disarms precisely the PR that needs
# this most (#884 is the ruling; the note above the code is the measurement).
#
# Zero dependencies beyond git and coreutils, like every other lock (Dev7).

set -euo pipefail

# The seal's home, read the same way its own header defines it and lock 9 reads
# it: the first non-comment, non-blank line, and the sha is its first field. Two
# spellings because one side is a file in the tree and the other is a path
# inside a commit.
PIN_PATH=.github/canonical-digest
PIN_FILE="$(git rev-parse --show-toplevel 2>/dev/null || echo .)/$PIN_PATH"
pin_sha() {                     # pin_sha <file text> — first payload line's sha, or empty
  printf '%s\n' "$1" | grep -vE '^[[:space:]]*(#|$)' | awk 'NR==1{print $1}' || true
}

# ---- the suite ---------------------------------------------------------------
#
# The first draft of this file was reviewed by hand, and the review said the nine
# cases "now run as a suite" — a suite that was never committed. Nothing in the
# tree could reproduce that sentence, which is the shelf-life defect this whole
# unit is about, one level up: a verdict that was true when it was typed and that
# no one can run again. So the cases live here, they run in CI beside the check
# they judge, and the line they print is the claim.
#
# Every fixture is derived from this repository's own history rather than
# hardcoded, so the suite cannot rot as main moves: the stale bases are the Nth
# non-merge ancestor of HEAD, the off-line base is a commit built on an ancestor
# and never merged, and the seal-moved base is the last commit at which
# .github/canonical-digest held a different sha — the real #852 move, used as the
# fixture for the strict path instead of a mutated working tree.
selftest() {
  local root tmp pass=0 fail=0
  root="$(git rev-parse --show-toplevel)"
  tmp="$(mktemp -d)"
  trap 'rm -rf "$tmp"' RETURN

  local HEAD_SHA STALE_NEAR STALE_FAR OFFLINE MOVED_BASE
  HEAD_SHA="$(git rev-parse HEAD)"
  # The 4th and 45th non-merge ancestors: one drift small enough that every
  # commit is listed, one past the 40-line cut so the truncation tail is run.
  STALE_NEAR="$(git rev-list --no-merges HEAD | sed -n '4p')"
  STALE_FAR="$(git rev-list --no-merges HEAD | sed -n '45p')"
  # A branch tip main never passed through. commit-tree writes an object and no
  # ref, needs no working tree, and carries its identity in the environment
  # because a CI runner has no git user configured.
  OFFLINE="$(GIT_AUTHOR_NAME=selftest GIT_AUTHOR_EMAIL=selftest@invalid \
             GIT_COMMITTER_NAME=selftest GIT_COMMITTER_EMAIL=selftest@invalid \
             git commit-tree "$(git rev-parse 'HEAD^{tree}')" -p "$(git rev-parse HEAD~5)" \
               -m 'baseline --selftest fixture: a tip main never passed through')"
  MOVED_BASE=""
  local now_sha c s
  now_sha="$(pin_sha "$(cat "$PIN_FILE")")"
  for c in $(git log --format=%H -- "$PIN_PATH"); do
    s="$(pin_sha "$(git show "$c:$PIN_PATH" 2>/dev/null || true)")"
    if [ -n "$s" ] && [ "$s" != "$now_sha" ]; then MOVED_BASE="$c"; break; fi
  done
  if [ -z "$STALE_FAR" ] || [ -z "$MOVED_BASE" ]; then
    echo "FATAL the suite needs 45 non-merge ancestors and one real seal move in" >&2
    echo "      history to build its fixtures from; this clone has neither." >&2
    echo "BASELINE SELFTEST VERDICT FAIL cases=0 failed=0  (no fixtures; a suite of nothing is not a pass)"
    return 1
  fi
  # The two drifts are asked of git rather than assumed from the fixture's depth:
  # rev-list orders by date within the ancestry, so the Nth ancestor is not
  # always N-1 commits of drift. The near one must fit under the 40-line cut and
  # the far one must not, or the two cases below are testing the same path.
  local NEAR_N FAR_N
  NEAR_N="$(git rev-list --no-merges --count "${STALE_NEAR}..${HEAD_SHA}")"
  FAR_N="$(git rev-list --no-merges --count "${STALE_FAR}..${HEAD_SHA}")"
  if [ "$NEAR_N" -lt 1 ] || [ "$NEAR_N" -gt 40 ] || [ "$FAR_N" -le 40 ]; then
    echo "FATAL the drift fixtures landed on the same side of the 40-line cut" >&2
    echo "      (near=$NEAR_N far=$FAR_N); deepen them and rerun." >&2
    echo "BASELINE SELFTEST VERDICT FAIL cases=0 failed=0  (fixtures do not separate the paths)"
    return 1
  fi

  # case <name> <body text> <base> <verdict substring> <wanted exit>
  case_() {
    local name="$1" body="$2" base="$3" want="$4" want_exit="$5" got rc
    printf '%s' "$body" > "$tmp/body.md"   # no trailing newline: the runner writes the body the same way
    set +e
    got="$(bash "$root/tools/baseline.sh" "$tmp/body.md" "$base" 2>&1)"
    rc=$?
    set -e
    if printf '%s\n' "$got" | grep -qF "$want" && [ "$rc" = "$want_exit" ]; then
      pass=$((pass + 1))
      printf 'CASE %-22s OK    exit=%s  %s\n' "$name" "$rc" "$want"
    else
      fail=$((fail + 1))
      printf 'CASE %-22s FAIL  exit=%s (wanted %s)  wanted: %s\n' "$name" "$rc" "$want_exit" "$want"
      printf '%s\n' "$got" | sed 's/^/       | /' | head -6
    fi
  }

  # The seal is held at HEAD (the working tree's pin is the pin at HEAD unless
  # this very PR moves it), so every case based on HEAD reads seal_moved=no.
  case_ missing            'Closes #822 — a body with prose and no field at all.' \
                                                                  "$HEAD_SHA"    'BASELINE MISSING seal_moved=no'   0
  case_ empty-body         ''                                     "$HEAD_SHA"    'BASELINE MISSING seal_moved=no'   0
  case_ unknown-sha        'Baseline: deadbee'                    "$HEAD_SHA"    'BASELINE UNKNOWN stated=deadbee'  1
  case_ fresh              "**Baseline:** $HEAD_SHA"              "$HEAD_SHA"    'BASELINE FRESH'                   0
  case_ stale-near         "**Baseline:** $STALE_NEAR"            "$HEAD_SHA"    "intervening=$NEAR_N seal_moved=no"  0
  case_ stale-far          "**Baseline:** $STALE_FAR"             "$HEAD_SHA"    "intervening=$FAR_N seal_moved=no"   0
  case_ off-line           "**Baseline:** $OFFLINE"               "$HEAD_SHA"    'BASELINE OFF_LINE'                0
  case_ prose-on-the-line  "Baseline: measured on deadbeef before ${STALE_NEAR} landed" \
                                                                  "$HEAD_SHA"    "intervening=$NEAR_N"                0
  # A base from before the pin existed — this branch was measured on one. The
  # run cannot tell whether the seal moved, says so in the verdict rather than
  # guessing a side, and judges strictly.
  case_ no-pin-at-base     ''                                     "$(git rev-parse "$(git log --format=%H -- "$PIN_PATH" | tail -1)~1")" \
                                                                                 'BASELINE MISSING seal_moved=unknown' 1
  # Against a base whose pin held a different sha, the same bodies take the
  # strict path — armed by the tree, with nothing in the body changed.
  case_ moved+fresh        "**Baseline:** $MOVED_BASE"            "$MOVED_BASE"  'BASELINE FRESH'                   0
  case_ moved+missing      'no field here'                        "$MOVED_BASE"  'BASELINE MISSING seal_moved=yes'  1
  case_ moved+stale        "**Baseline:** $(git rev-parse "${MOVED_BASE}~1")" \
                                                                  "$MOVED_BASE"  'STALE'                            1
  case_ moved+off-line     "**Baseline:** $OFFLINE"               "$MOVED_BASE"  'BASELINE OFF_LINE'                1

  # The count and the list are one claim, and the first draft got it wrong: it
  # counted every commit and listed only the non-merges, so a 12-commit drift
  # printed seven lines. A meter that disagrees with its own evidence is the
  # defect this file exists to catch, so the suite reads both numbers back.
  printf '**Baseline:** %s\n' "$STALE_NEAR" > "$tmp/body.md"
  local out said shown
  out="$(bash "$root/tools/baseline.sh" "$tmp/body.md" "$HEAD_SHA")"
  said="$(printf '%s\n' "$out" | sed -n 's/.*intervening=\([0-9]*\).*/\1/p')"
  shown="$(printf '%s\n' "$out" | grep -c '^  [0-9a-f]\{7,\}  ' || true)"
  if [ "$said" = "$shown" ] && [ "${said:-0}" -gt 0 ]; then
    pass=$((pass + 1)); printf 'CASE %-22s OK    said=%s shown=%s\n' 'count-agrees-with-list' "$said" "$shown"
  else
    fail=$((fail + 1)); printf 'CASE %-22s FAIL  said=%s shown=%s\n' 'count-agrees-with-list' "$said" "$shown"
  fi

  # And the same question at the far end, where the list is cut at 40 and the
  # remainder is a sentence rather than lines.
  printf '**Baseline:** %s\n' "$STALE_FAR" > "$tmp/body.md"
  out="$(bash "$root/tools/baseline.sh" "$tmp/body.md" "$HEAD_SHA")"
  said="$(printf '%s\n' "$out" | sed -n 's/.*intervening=\([0-9]*\).*/\1/p')"
  shown="$(printf '%s\n' "$out" | grep -c '^  [0-9a-f]\{7,\}  ' || true)"
  if [ "$shown" = 40 ] && printf '%s\n' "$out" | grep -qF "and $((said - 40)) more"; then
    pass=$((pass + 1)); printf 'CASE %-22s OK    said=%s shown=40 + tail\n' 'truncation-accounts' "$said"
  else
    fail=$((fail + 1)); printf 'CASE %-22s FAIL  said=%s shown=%s and no tail line\n' 'truncation-accounts' "$said" "$shown"
  fi

  printf 'BASELINE SELFTEST VERDICT %s cases=%d failed=%d\n' \
    "$([ "$fail" = 0 ] && printf PASS || printf FAIL)" "$((pass + fail))" "$fail"
  [ "$fail" = 0 ]
}

BODY="${1:-}"
BASE="${2:-}"

if [ "$BODY" = "--selftest" ]; then
  if [ "$(git rev-parse --is-shallow-repository 2>/dev/null || echo true)" = true ]; then
    echo "FATAL this clone is shallow; the suite builds its fixtures out of history." >&2
    exit 2
  fi
  selftest
  exit $?
fi

if [ -z "$BODY" ] || [ -z "$BASE" ]; then
  echo "usage: tools/baseline.sh <pr-body-file> <base-sha>" >&2
  echo "       tools/baseline.sh --selftest" >&2
  exit 2
fi
[ -r "$BODY" ] || { echo "FATAL cannot read PR body file: $BODY" >&2; exit 2; }

# A provenance check run against a shallow clone would call every honest sha
# unknown and blame the author for the runner's configuration. Refuse instead:
# a checker that cannot see history has no standing to judge history.
if [ "$(git rev-parse --is-shallow-repository 2>/dev/null || echo true)" = true ]; then
  echo "FATAL this clone is shallow, so it cannot verify any baseline sha." >&2
  echo "      the provenance lock needs history: checkout with fetch-depth: 0." >&2
  exit 2
fi

git cat-file -e "${BASE}^{commit}" 2>/dev/null || {
  echo "FATAL the base sha ${BASE} is not a commit in this clone." >&2
  exit 2
}

# The one field read off the body, matched case-insensitively and with or
# without markdown bold, because the field is for humans to type and a lock that
# trips on `**Baseline:**` versus `Baseline:` is a lock about formatting.
#
# KNOWN, FILED, NOT FIXED HERE (#1014): the first matching line wins and no
# markdown is parsed, so an example of this check's own output quoted earlier in
# the body is read as the claim — a fenced `Baseline: deadbee` above the real
# field hard-fails an honest PR. Every body that documents this lock has that
# shape, including the one that shipped it, which put the field first by hand.
field() {                       # field <label-regex> — the first matching line, or empty
  grep -im1 -E "^[[:space:]]*[*_]{0,2}${1}[*_]{0,2}[[:space:]]*:" "$BODY" || true
}

BASELINE_LINE="$(field 'baseline')"

# The hex runs on the baseline line, in order — and then the FIRST ONE THAT IS
# ACTUALLY A COMMIT.
#
# Taking the first run outright looked right and failed its own review: the
# honest body `Baseline: measured on deadbeef before 451cdab landed` picked
# `deadbeef` and hard-failed a PR whose real sha was sitting two words away.
# A lock that rejects honest work over word order teaches people to route
# around it, so prose on the line is now the author's business, as intended.
# If none of the candidates resolve, the first is reported — a body naming no
# real commit is the UNKNOWN case, and it should read as the author wrote it.
STATED=""
FIRST_CANDIDATE=""
while read -r cand; do
  [ -z "$cand" ] && continue
  [ -z "$FIRST_CANDIDATE" ] && FIRST_CANDIDATE="$cand"
  if git cat-file -e "${cand}^{commit}" 2>/dev/null; then STATED="$cand"; break; fi
done <<< "$(printf '%s' "$BASELINE_LINE" | grep -oiE '\b[0-9a-f]{7,40}\b' || true)"
[ -z "$STATED" ] && STATED="$FIRST_CANDIDATE"

# ---- does this PR move the seal? asked of the tree, not of the author --------
#
# The first draft asked the body: a `Declared move: yes|no` field whose template
# default was `no`. That draft was written before #884 landed lock 9, and it is
# the exact shape #884 exists to refuse — the strict path below, the only path
# that FAILS, was armed by a self-report that ships disarmed, so the PR that
# most needs it is the PR that never touches the field. Measured on this tree
# with the pin edited by hand and the body left at the template's default:
#
#     tools/digest-move.sh --base origin/main
#       → DIGEST MOVE VERDICT UNARGUED from=e9c833ae… to=aaaa33ae…   [exit 1]
#     tools/baseline.sh body 0e61346
#       → BASELINE STALE stated=451cdab base=0e61346 intervening=61
#         declared_move=no                                           [exit 0]
#
# One tree, one moment, two answers to one question — and the answer that
# decides whether this check has teeth was the one nobody had to look up.
#
# So the question goes to the same place lock 9 asks it: the pin, here versus at
# the base. The gate is armed by the act it governs, and a strict path nobody
# can turn off by not typing something. The template's field is gone with it:
# the tree already carries `## Declared digest move` for the human argument, and
# two records of one fact drift the first time somebody edits one of them (#789
# deleted a rival list in this repository for the same reason).
#
# Only the sha is read. Whether the pin is well-formed is locks 7 and 9's
# judgement and they both refuse a malformed one; a third opinion here would be
# a fourth thing to keep in agreement.
SEAL_NOW=""
SEAL_BASE=""
if [ -r "$PIN_FILE" ]; then SEAL_NOW="$(pin_sha "$(cat "$PIN_FILE")")"; fi
BASE_PIN="$(git show "${BASE}:${PIN_PATH}" 2>/dev/null || true)"
if [ -n "$BASE_PIN" ]; then SEAL_BASE="$(pin_sha "$BASE_PIN")"; fi

if [ -n "$SEAL_NOW" ] && [ -n "$SEAL_BASE" ]; then
  if [ "$SEAL_NOW" = "$SEAL_BASE" ]; then SEAL_MOVED=no; else SEAL_MOVED=yes; fi
else
  # One side has no readable pin: a base older than the pin itself (this very
  # branch was measured on one — the pin did not exist yet), or a tree that
  # deleted it. Cannot tell is not the same as no, and a checker that cannot
  # tell must not hand out the lenient path. It is also not the same as yes, so
  # the verdict says `unknown` and judges strictly: the strict path is armed by
  # anything that is not a confirmed no, and the instrument line never claims to
  # know something this run could not read.
  SEAL_MOVED=unknown
  echo "NOTE the seal pin is unreadable at $([ -z "$SEAL_BASE" ] && printf 'the base' || printf 'HEAD')" >&2
  echo "     ($PIN_PATH), so this run cannot tell whether the seal moved and judges strictly." >&2
fi

verdict() { printf 'BASELINE %s\n' "$1"; }

# ---- 1. no field at all -----------------------------------------------------
if [ -z "$STATED" ]; then
  if [ "$SEAL_MOVED" != no ]; then
    verdict "MISSING seal_moved=${SEAL_MOVED}"
    echo "FATAL the seal is not known to be held here, and this PR states no baseline." >&2
    echo "      a move is a claim about a before and an after; without the before," >&2
    echo "      there is nothing for anyone to check it against." >&2
    echo "      add to the body:  **Baseline:** \$(git rev-parse --short HEAD)" >&2
    exit 1
  fi
  verdict "MISSING seal_moved=${SEAL_MOVED}"
  echo "WARN  this PR states no baseline, so its evidence cannot be dated." >&2
  echo "      add one line to the body and this becomes checkable:" >&2
  echo "      **Baseline:** \$(git rev-parse --short HEAD)   # before you branched" >&2
  exit 0
fi

# ---- 2. a sha nobody can resolve -------------------------------------------
# Worse than no sha: it *reads* as provenance. A typo, a sha from a fork, a
# number that was never a commit — all pass a human's eye and none can be
# checked. This fails on every PR, declared move or not, because the failure is
# not "your evidence is stale", it is "your evidence cites a world that does not
# exist".
if ! git cat-file -e "${STATED}^{commit}" 2>/dev/null; then
  verdict "UNKNOWN stated=${STATED}"
  echo "FATAL the stated baseline ${STATED} is not a commit in this repository." >&2
  echo "      a sha that cannot be resolved is not provenance — it only looks like it." >&2
  exit 1
fi

STATED_FULL="$(git rev-parse "${STATED}^{commit}")"
BASE_FULL="$(git rev-parse "${BASE}^{commit}")"

# ---- 3. fresh ---------------------------------------------------------------
if [ "$STATED_FULL" = "$BASE_FULL" ]; then
  verdict "FRESH stated=${STATED} base=$(git rev-parse --short "$BASE_FULL")"
  echo "the evidence was measured against the tree it is landing on."
  exit 0
fi

SHORT_S="$(git rev-parse --short "$STATED_FULL")"
SHORT_B="$(git rev-parse --short "$BASE_FULL")"

# ---- 4. stale, but on the line ---------------------------------------------
if git merge-base --is-ancestor "$STATED_FULL" "$BASE_FULL"; then
  # Counted and listed over the SAME set. The first draft counted every commit
  # and listed only the non-merges, so a 12-commit drift printed seven lines —
  # a meter disagreeing with its own evidence, which is the exact defect this
  # whole file exists to catch. Merge commits carry no content of their own
  # here; what moved the world underneath the author is the non-merge set, so
  # that is the set both numbers describe.
  N="$(git rev-list --no-merges --count "${STATED_FULL}..${BASE_FULL}")"
  verdict "STALE stated=${SHORT_S} base=${SHORT_B} intervening=${N} seal_moved=${SEAL_MOVED}"
  echo "${N} commit(s) landed on main between the measurement and this check:"
  # `git log … | head -40` is the obvious spelling and it kills this check on
  # exactly the drift it exists to report — INTERMITTENTLY, which is worse than
  # always. `head` exits at line 41 and closes the read end; if `git log` has
  # not finished writing by then it takes SIGPIPE and exits 141, and
  # `set -o pipefail` makes 141 the verdict, with the WARN paragraph never
  # printed. Whether git finishes first is a pipe-buffer race, so the same
  # command on the same tree does both.
  #
  # Measured on a 61-commit drift against this repository's own main, ten runs:
  #
  #   141 141 0 0 0 0 0 0 0 141        3 dead, 7 green, nothing else different
  #
  # The suite is not blind to this — `stale-far` asserts `want_exit 0` over a
  # drift chosen to exceed the cut. It went 5/5 green because its fixture's log
  # is short enough in BYTES that git wins the race nearly always; the case was
  # under-loaded rather than wrong. A flaky lock is the one failure mode a lock
  # cannot have, so the race is removed rather than made less likely.
  #
  # The whole log goes into a variable first, so nothing is ever handed a pipe
  # it can close early. `head -n 40` on a here-string cannot SIGPIPE a process
  # that has already finished.
  DRIFT_LOG="$(git log --no-merges --format='  %h  %s' "${STATED_FULL}..${BASE_FULL}")"
  head -n 40 <<<"$DRIFT_LOG"
  [ "$N" -gt 40 ] && echo "  … and $((N - 40)) more"
  if [ "$SEAL_MOVED" != no ]; then
    echo >&2
    echo "FATAL this PR moves the DIGEST seal, and its 'before' is not the tree's" >&2
    echo "      before. The move cannot be verified by anyone, including its author:" >&2
    echo "      re-measure against ${SHORT_B} and restate the baseline." >&2
    exit 1
  fi
  echo
  echo "WARN  this is not automatically wrong — most drift does not touch most"
  echo "      evidence. But only you know whether these commits touch these numbers."
  echo "      If any of them do, re-measure against ${SHORT_B} and restate the baseline."
  exit 0
fi

# ---- 5. stale, and off the line --------------------------------------------
# The stated sha resolves but is not an ancestor of the base: it was a branch
# tip, a commit that was squashed away, or another lineage entirely. The
# evidence describes a world that main never passed through, which is a
# different and louder problem than being behind.
verdict "OFF_LINE stated=${SHORT_S} base=${SHORT_B} seal_moved=${SEAL_MOVED}"
echo "the stated baseline is not an ancestor of main's tip — it is not a tree main ever was."
echo "commonly: a sha from your own branch rather than the main you branched off."
echo "the merge base of the two is $(git merge-base "$STATED_FULL" "$BASE_FULL" | cut -c1-7)."
if [ "$SEAL_MOVED" != no ]; then
  echo >&2
  echo "FATAL a seal move measured against a tree main never was is unverifiable." >&2
  exit 1
fi
echo
echo "WARN  re-measure against ${SHORT_B} — the tree this is landing on."
exit 0
