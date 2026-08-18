#!/usr/bin/env bash
# tools/crowns.sh — every class has a crown, says the door. How many do? (#1459)
#
# Usage: tools/crowns.sh              count the classes against their crowns
#        tools/crowns.sh --list       print one row per uncrowned class
#        tools/crowns.sh --selftest   run the matcher's cases; no token, no network
#        tools/crowns.sh --help | -h  print this clause, and stop
#
# THE FINDING THIS EXISTS FOR. `CLAUDE.md` states the rule without qualification:
#
#   Every class has a crown (label `class-design`) — touch a class, update its
#   crown.
#
# Eighteen of eighty-nine did not have one, and seven of those appeared in no
# issue title in the repository's whole history — including `Streams`, whose
# `utf8()` is the first statement of every probe in the tree, and `SheetDoor`,
# the one door `SheetFence` exists to prove there is exactly one of.
#
# The gap had never had a number, and could not: a crown is an ISSUE, so no probe
# can see it — probes read the tree (contract clause 2). A tool can read both.
#
# REPORTED, NEVER JUDGED, and that is #1459's own ranking rather than timidity.
# Whether all eighty-nine warrant a crown is an open question — `Geo`, `Family`
# and `Origin` may be enums where a record is ceremony — and a gate installed
# before that is answered would be a gate whose first act is to demand eighteen
# documents nobody has argued for. The number goes on the record first; the rule
# gets its exemption clause, or does not, with the figure in front of it.
#
# PAGING IS STATED, because #1246's whole finding is that it usually is not:
# `gh issue list` returns 30 rows by default and says nothing about it, and an
# issue about unmeasured claims was itself off by seventeen times for exactly
# that reason. `limit=` is on the line and `TRUNCATED` is a refusal.
#
# Zero dependencies beyond gh, grep and coreutils (Dev7 / D-009). No bash 4.

set -uo pipefail

cd "$(dirname "$0")/.."

REPO="${MATRIX_REPO:-gokselozgur5/matrix-sim}"
LIMIT=2000
MODE=count

case "${1:-}" in
  '')          MODE=count ;;
  --list)      MODE=list ;;
  --selftest)  MODE=selftest ;;
  # READ TO THE END OF THE CLAUSE, not to a line number (#1382, #1520) — a door
  # added below the clause is in `--help` the moment it is in the header.
  -h|--help)   awk 'NR==1 {next} !/^#/ {exit} /^#$/ {if (++blank == 2) exit} {print}' "$0"; exit 0 ;;
  *) echo "FATAL unknown argument '$1' — see the Usage clause at the top of this file" >&2; exit 2 ;;
esac

# ---------------------------------------------------------------- the matcher
#
# Two pure functions over text, so the suite drives the shipped code rather than
# a copy of its regex — which is the defect this shop hunts (#789, #880, #1162).

# Every type this tree declares, one per line. Nested and local types are missed
# and that is correct: a crown is a design record for a top-level class, and an
# inner helper has no separate life to document.
classes_in() {                  # classes_in <dir>
  grep -rhoE '^(public )?(final |abstract |sealed )?(class|record|enum|interface) [A-Z][A-Za-z0-9]*' \
    "$1" --include='*.java' 2>/dev/null \
    | awk '{print $NF}' | sort -u
}

# A crown is an issue whose title opens `Class: <Name>`. The label is
# `class-design` and is NOT the discriminator: that label also rides unit issues
# about a class, so keying on it would count a unit as a crown and report the gap
# as smaller than it is — the direction that hides a finding.
crowns_in() {                   # crowns_in <titles-file>
  grep -oE '^Class: [A-Z][A-Za-z0-9]*' "$1" 2>/dev/null | awk '{print $2}' | sort -u
}

# ---------------------------------------------------------------- the reading

report() {
  local titles rc=0
  titles="$(mktemp "${TMPDIR:-/tmp}/crowns.XXXXXX")"
  trap 'rm -f "${titles:-}"' EXIT

  if ! gh issue list --repo "$REPO" --state all --limit "$LIMIT" \
        --json title -q '.[].title' > "$titles" 2>/dev/null; then
    echo "CROWNS VERDICT UNREADABLE limit=$LIMIT  (the issue list could not be read)"
    return 3
  fi

  local read_rows
  read_rows="$(wc -l < "$titles" | tr -d ' ')"
  # A page that came back exactly full is a page, not an answer (#1273). The
  # tool cannot tell one from the other, and guessing is the defect it exists to
  # avoid reporting.
  if [ "$read_rows" -ge "$LIMIT" ]; then
    echo "CROWNS VERDICT TRUNCATED read=$read_rows limit=$LIMIT  (the answer filled the page)"
    return 5
  fi

  # THE IDENTITY THIS CENSUS OWES ITS READER, and it did not close (#1459 follow-up):
  #
  #     classes = crowned + uncrowned
  #
  # `crowned` counted the CROWNS and not the crowned CLASSES, so the day the last
  # class got its record the line read `classes=89 crowned=91 uncrowned=0` — a
  # count larger than the population it is a count of, with 89 + 0 != 91 and
  # nothing saying why. A reader cannot tell that from a duplicate crown, a crown
  # for a deleted class, or a broken matcher, and one of those three is a defect.
  #
  # Both extras are legitimate and neither is visible without a number of its own:
  # #887 `Class: Chronicle` is a crown cut AHEAD of its class (the v6.0 parser, born
  # with #542's PR, which has not landed), and #89 `Class: OpsConsole` is CLOSED for
  # a class that has since gone. So the fourth term is not an error count — it is
  # the other direction of the same question, and it earns its own name.
  local classes crowned crownedHere crownless uncrowned total have missing extra
  classes="$(classes_in src)"
  crowned="$(crowns_in "$titles")"
  crownedHere="$(comm -12 <(printf '%s\n' "$classes") <(printf '%s\n' "$crowned"))"
  uncrowned="$(comm -23 <(printf '%s\n' "$classes") <(printf '%s\n' "$crowned"))"
  crownless="$(comm -13 <(printf '%s\n' "$classes") <(printf '%s\n' "$crowned"))"

  total="$(printf '%s\n' "$classes" | grep -c '[^[:space:]]' || true)"
  have="$(printf '%s\n' "$crownedHere" | grep -c '[^[:space:]]' || true)"
  missing="$(printf '%s\n' "$uncrowned" | grep -c '[^[:space:]]' || true)"
  extra="$(printf '%s\n' "$crownless" | grep -c '[^[:space:]]' || true)"

  if [ "$MODE" = list ]; then
    # Beside each name, how many issue titles mention it at all: a class with a
    # unit but no crown is a different situation from one nothing has ever
    # named, and the second is where the seven are.
    while IFS= read -r c; do
      [ -n "$c" ] || continue
      printf 'UNCROWNED %-18s mentions=%s\n' "$c" "$(grep -c "\b$c\b" "$titles" || true)"
    done <<< "$uncrowned"
    # The other direction, under --list only: a crown naming no class in src/. Both
    # of today's are legitimate — one cut ahead of its class, one closed for a class
    # that has gone — and a reader who sees `crownless=2` needs to know WHICH before
    # they can tell that from a matcher fault.
    while IFS= read -r c; do
      [ -n "$c" ] || continue
      printf 'CROWNLESS %-18s no class of that name in src/\n' "$c"
    done <<< "$crownless"
  fi

  echo "CROWNS_CENSUS issues_read=$read_rows limit=$LIMIT crownless=$extra repo=$REPO"
  if [ "$total" -eq 0 ]; then
    # A sweep that found no classes is red: an empty reading must not print the
    # line a complete one prints (#1207, #970).
    echo "CROWNS VERDICT NOTHING_READ classes=0 crowned=0 uncrowned=0 classes_none=1"
    return 4
  fi
  echo "CROWNS VERDICT COUNTED classes=$total crowned=$have uncrowned=$missing classes_none=0"
  return $rc
}

# ---------------------------------------------------------------- the suite

selftest() {
  local tmp pass=0 fail=0
  tmp="$(mktemp -d "${TMPDIR:-/tmp}/crowns-selftest.XXXXXX")"
  trap 'rm -rf "${tmp:-}"' EXIT

  check() {                     # check <name> <want> <got>
    if [ "$2" = "$3" ]; then
      pass=$((pass + 1)); printf 'CROWNS case=%-28s want=%-12s got=%-12s OK\n' "$1" "$2" "$3"
    else
      fail=$((fail + 1)); printf 'CROWNS case=%-28s want=%-12s got=%-12s BROKEN\n' "$1" "$2" "$3"
    fi
  }

  mkdir -p "$tmp/src"
  printf 'package x;\npublic final class Alpha {}\n'      > "$tmp/src/Alpha.java"
  printf 'package x;\npublic record Beta(int n) {}\n'     > "$tmp/src/Beta.java"
  printf 'package x;\nenum Gamma { A }\n'                 > "$tmp/src/Gamma.java"
  printf 'package x;\npublic sealed interface Delta {}\n' > "$tmp/src/Delta.java"
  # An INNER type must not be counted: a crown is a record for a top-level class,
  # and the leading-anchor is what keeps a nested helper out of the census.
  printf 'package x;\npublic class Epsilon {\n    private static class Inner {}\n}\n' > "$tmp/src/Epsilon.java"

  check every-declaration-form "Alpha Beta Delta Epsilon Gamma" \
        "$(classes_in "$tmp/src" | tr '\n' ' ' | sed 's/ $//')"

  printf 'Class: Alpha\nClass: Beta\nunit: #1 — Gamma gains a flag\nCrown: Delta\n' > "$tmp/titles"
  # `unit: … Gamma …` must NOT count — the label rides unit issues too, and
  # counting one as a crown reports the gap as smaller than it is.
  # `Crown: Delta` must not count either: the convention is `Class:`.
  check only-the-class-prefix "Alpha Beta" \
        "$(crowns_in "$tmp/titles" | tr '\n' ' ' | sed 's/ $//')"

  : > "$tmp/empty"
  check no-titles-no-crowns "" "$(crowns_in "$tmp/empty" | tr '\n' ' ' | sed 's/ $//')"

  mkdir -p "$tmp/none"
  check no-sources-no-classes "" "$(classes_in "$tmp/none" | tr '\n' ' ' | sed 's/ $//')"

  printf 'CROWNS SELFTEST VERDICT %s cases=%d failed=%d\n' \
    "$([ "$fail" -eq 0 ] && printf PASS || printf FAIL)" "$((pass + fail))" "$fail"
  [ "$fail" -eq 0 ]
}

case "$MODE" in
  selftest) selftest; exit $? ;;
  *)        report;   exit $? ;;
esac
