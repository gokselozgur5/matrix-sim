#!/usr/bin/env bash
# tools/backedge.sh — the inbound half of the decision web, read rather than remembered (#1394)
#
# Usage: tools/backedge.sh [--check]    report every missing back edge; write nothing
#        tools/backedge.sh --census     the three counts, no rows
#        tools/backedge.sh --write      add a `Referenced by:` line to a record that
#                                       has none; NEVER touch one that exists
#        tools/backedge.sh --selftest   run every case against built fixtures
#        tools/backedge.sh --help | -h  print this clause, and stop
#
# THE FINDING THIS EXISTS FOR. `docs/adr/README.md` has described this program
# since D-029:
#
#   Every record may end with one `Referenced by:` line — the inbound half of
#   the decision web, generated from actual link targets (never from memory) by
#   an idempotent pass that refuses to touch an existing line.
#
# There was no pass. `grep -rln 'Referenced by' tools/ probes/ .github/` printed
# nothing, so every back edge in the tree was typed by hand — which is the one
# thing that sentence says they are not. Half of them were never typed at all:
# 230 forward links between records, 120 with no back edge, 20 of 59 records
# with no `Referenced by:` line.
#
# WHY THAT IS A SAFETY PROBLEM AND NOT A TIDINESS ONE. The same paragraph says
# what the line is for — *read it before you flip, supersede, or park a record*.
# A record whose line names three citers when six records cite it does not read
# as incomplete; it reads as answered. The empty case is visibly unanswered and
# is the safer of the two.
#
# WHAT IT READS. Only a MARKDOWN LINK to another record — `](D-057-slug.md)` —
# counts as a citation. A D-number written in prose does not: every record's own
# body cites neighbours rhetorically ("D-010 is why this is deterministic"), and
# counting those would make the web a list of everything anyone has mentioned,
# which is not what *what leans on this* means. Forward links are the edges the
# document itself drew.
#
# WHAT IT WILL NOT DO. Touch a line that exists. `--write` adds a `Referenced
# by:` line to a record that has none and declines every record that already
# carries one — the README's own refusal, kept verbatim, because several of the
# existing lines carry an ordering a generator would not reproduce.
#
# THE PRICE OF THAT REFUSAL, MEASURED. Of the seventy edges missing when this
# was written, twenty-eight sat on the twelve records with no line and
# forty-two on twenty-five records whose line existed and named only some of
# their citers. So `--write` closes the first group and cannot touch the second
# — and the second is the more dangerous half, because a partial line reads as
# ANSWERED rather than as absent. Relaxing the refusal is a decision about the
# records rather than a flag, and it is filed as one.
#
# Exit 0 when every forward link has its back edge, 1 when one does not, 2 when
# the invocation was refused (D-1241's universal code).
#
# Zero dependencies beyond grep, sed and coreutils, like every other tool here
# (Dev7 / D-009). No bash 4: the operator's box ships 3.2, so no `declare -A`.

set -uo pipefail

ADR_DIR="docs/adr"
MODE=check

# One arm per door, and `''|--check)` deliberately NOT folded into one. The flag
# audit reads a tool's parser by looking for `--x)` as a case pattern, so the
# folded spelling advertises a flag the tool appears to refuse — `advice.sh`
# reported `PHANTOM tools/backedge.sh advertises '--check' … and parses no such
# flag` on this file's first run, which is the audit working on the day the tool
# arrived.
case "${1:-}" in
  '')         MODE=check ;;
  --check)    MODE=check ;;
  --census)   MODE=census ;;
  --write)    MODE=write ;;
  --selftest) MODE=selftest ;;
  # READ TO THE END OF THE CLAUSE, not to a line number (#1382, #1520): a door
  # added below it is in `--help` the moment it is in the header.
  -h|--help) awk 'NR==1 {next} !/^#/ {exit} /^#$/ {if (++blank == 2) exit} {print}' "$0"; exit 0 ;;
  *) echo "FATAL unknown argument '$1' — see the Usage clause at the top of this file" >&2; exit 2 ;;
esac

# ---------------------------------------------------------------- the reading
#
# One function, pointed at a directory, so the suite can point it at a fixture
# tree instead of at `docs/adr`. Everything below is a pure read: no file in the
# directory is opened for writing anywhere in this script.

record_id() {                   # record_id <path> -> D-0NN
  basename "$1" | cut -d- -f1-2
}

# The forward links one record draws to another record, deduplicated.
#
# THE `Referenced by:` LINE IS NOT READ, and that is the whole correctness of
# this function. A back edge is spelled as a markdown link, so a naive extractor
# reads every inbound edge as a fresh outbound one: D-002 says *referenced by
# D-001*, the sweep calls that D-002 -> D-001, and then demands a back edge from
# D-001 to D-002 that nothing owes. Every completed pair becomes a new debt and
# the count can never reach zero. The first draft did exactly this and reported
# a two-record fixture with one correct back edge as INCOMPLETE.
#
# A link to the record's own file is dropped for the same family of reason: a
# record citing itself is a table of contents, not an edge, and counting it
# would make every record its own citer.
forward_links() {               # forward_links <path> -> one D-number per line
  local f="$1" self
  self="$(record_id "$f")"
  grep -vE '^Referenced by:' "$f" 2>/dev/null \
    | grep -oE '\]\(D-[0-9]{3}-[a-z0-9-]+\.md\)' \
    | grep -oE 'D-[0-9]{3}' \
    | sort -u \
    | grep -v "^${self}$" || true
}

# Does <target>'s `Referenced by:` line name <citer>? The line is matched as a
# whole rather than the file being searched, because a record's body mentions
# D-numbers constantly and a bare grep would report every edge as present — the
# failure mode that makes a checker green by construction.
has_back_edge() {               # has_back_edge <target-path> <citer-id>
  grep -E '^Referenced by:' "$1" 2>/dev/null | grep -q "$2"
}

sweep() {                       # sweep <dir> — rows on stdout, counts on globals
  local dir="$1" f self other tgt
  links=0; missing=0; records=0; with_line=0
  for f in "$dir"/D-*.md; do
    [ -f "$f" ] || continue
    records=$((records + 1))
    grep -qE '^Referenced by:' "$f" && with_line=$((with_line + 1))
  done
  for f in "$dir"/D-*.md; do
    [ -f "$f" ] || continue
    self="$(record_id "$f")"
    while IFS= read -r other; do
      [ -n "$other" ] || continue
      # A link to a record that is not in this directory is a dangling link and
      # a different defect; DocLint owns the front matter and the index owns the
      # roster, so it is counted as no edge rather than reported here.
      tgt=""
      for cand in "$dir/$other"-*.md; do [ -f "$cand" ] && { tgt="$cand"; break; }; done
      [ -n "$tgt" ] || continue
      links=$((links + 1))
      has_back_edge "$tgt" "$self" \
        || { missing=$((missing + 1)); echo "BACKEDGE MISSING $other <- $self  ($(basename "$tgt") does not name it)"; }
    done <<< "$(forward_links "$f")"
  done
}

report() {                      # report <dir> <rows: yes|no>
  local dir="$1" rows="$2" rowfile
  # Redirected to a file rather than captured. `out="$(sweep …)"` puts the sweep
  # in a subshell, and a subshell's counters die with it — the first draft of
  # this file read `records=0` on a directory full of records and printed
  # NOTHING_READ for every fixture. The rows are data and the counts are state,
  # and only one of the two survives a command substitution.
  rowfile="$(mktemp "${TMPDIR:-/tmp}/backedge-rows.XXXXXX")"
  sweep "$dir" > "$rowfile"
  [ "$rows" = yes ] && [ -s "$rowfile" ] && cat "$rowfile"
  rm -f "$rowfile"
  # The census carries the population and the verdict carries the judgement
  # (#1221): `records=` and `links=` move whenever a decision is written, and a
  # count on an exact-line row is a number people edit until the lane is quiet.
  printf 'BACKEDGE_CENSUS records=%d links=%d with_line=%d\n' "$records" "$links" "$with_line"
  if [ "$records" -eq 0 ]; then
    # An empty sweep must be red. A checker that reads nothing and reports
    # nothing prints the same verdict as a tree with no defects.
    echo "BACKEDGE VERDICT NOTHING_READ missing=0"
    return 1
  fi
  if [ "$missing" -eq 0 ]; then
    echo "BACKEDGE VERDICT WEB_COMPLETE missing=0"
    return 0
  fi
  echo "BACKEDGE VERDICT WEB_INCOMPLETE missing=$missing"
  return 1
}

# ---------------------------------------------------------------- the writing
#
# THE REFUSAL IS THE CONTRACT, and it is `docs/adr/README.md`'s own words: the
# pass "refuses to touch an existing line". So this writes a `Referenced by:`
# line only where there is none, and a record that already has one is left
# exactly as it stands even when the line is incomplete.
#
# WHAT THAT COSTS, STATED RATHER THAN DISCOVERED. Of the seventy edges missing
# today, twenty-eight are on the twelve records with no line at all and
# forty-two are on twenty-five records whose line exists and names only some of
# its citers. This mode can close the first group and none of the second — and
# the second is the more dangerous half, because a partial line reads as
# ANSWERED rather than as absent. Relaxing the refusal is a decision about the
# records rather than a flag, and it is filed as one.
#
# IDEMPOTENT by the same rule: a second run finds a line and declines, so
# running it twice is running it once.

citers_of() {                   # citers_of <dir> <target-id> — one D-number per line
  local dir="$1" want="$2" f self
  for f in "$dir"/D-*.md; do
    [ -f "$f" ] || continue
    self="$(record_id "$f")"
    [ "$self" = "$want" ] && continue
    forward_links "$f" | grep -qx "$want" && printf '%s\n' "$self"
  done
}

write_edges() {                 # write_edges <dir>
  local dir="$1" f self line citer slug wrote=0 declined=0 nothing=0
  for f in "$dir"/D-*.md; do
    [ -f "$f" ] || continue
    self="$(record_id "$f")"
    if grep -qE '^Referenced by:' "$f"; then
      declined=$((declined + 1))
      continue
    fi
    line=""
    while IFS= read -r citer; do
      [ -n "$citer" ] || continue
      # The link text is the citer's own filename, read off disk rather than
      # reconstructed, so a renamed record cannot produce a link to a file that
      # does not exist.
      slug=""
      for cand in "$dir/$citer"-*.md; do [ -f "$cand" ] && { slug="$(basename "$cand")"; break; }; done
      [ -n "$slug" ] || continue
      [ -n "$line" ] && line="$line, "
      line="$line[$citer]($slug)"
    done <<< "$(citers_of "$dir" "$self" | sort -u)"
    if [ -z "$line" ]; then
      nothing=$((nothing + 1))
      continue
    fi
    # A blank line before it, because every record in this tree ends its prose
    # and then its back edge, and a line glued to the last paragraph renders as
    # part of that paragraph.
    printf '\nReferenced by: %s.\n' "$line" >> "$f"
    wrote=$((wrote + 1))
    echo "BACKEDGE WROTE $self  $line"
  done
  printf 'BACKEDGE_WRITE_CENSUS wrote=%d declined=%d no_citers=%d\n' "$wrote" "$declined" "$nothing"
  echo "BACKEDGE WRITE VERDICT DONE wrote=$wrote"
}

# ---------------------------------------------------------------- the suite
#
# Fixtures, not the tree. The tree is `WEB_INCOMPLETE` today, so a suite that
# asserted anything about `docs/adr` would be asserting today's debt and would
# have to be edited by whoever pays it — which is how a suite becomes a thing
# people edit until it is quiet.

selftest() {
  local tmp pass=0 fail=0
  tmp="$(mktemp -d "${TMPDIR:-/tmp}/backedge.XXXXXX")"
  trap 'rm -rf "${tmp:-}"' EXIT

  verdict_case() {              # verdict_case <name> <want-verdict> <dir>
    local got
    got="$(report "$3" no | grep -oE 'BACKEDGE VERDICT [A-Z_]+' | awk '{print $3}')"
    if [ "$got" = "$2" ]; then
      pass=$((pass + 1)); printf 'BACKEDGE case=%-24s want=%-16s got=%-16s OK\n' "$1" "$2" "$got"
    else
      fail=$((fail + 1)); printf 'BACKEDGE case=%-24s want=%-16s got=%-16s BROKEN\n' "$1" "$2" "$got"
    fi
  }

  count_case() {                # count_case <name> <want-missing> <dir>
    local got
    got="$(report "$3" no | grep -oE 'missing=[0-9]+$' | cut -d= -f2)"
    if [ "$got" = "$2" ]; then
      pass=$((pass + 1)); printf 'BACKEDGE case=%-24s want=missing=%-9s got=missing=%-9s OK\n' "$1" "$2" "$got"
    else
      fail=$((fail + 1)); printf 'BACKEDGE case=%-24s want=missing=%-9s got=missing=%-9s BROKEN\n' "$1" "$2" "$got"
    fi
  }

  # An empty directory must be RED, not vacuously green.
  mkdir -p "$tmp/empty"
  verdict_case empty-directory NOTHING_READ "$tmp/empty"

  # One record linking another, and the target says so.
  mkdir -p "$tmp/complete"
  printf '# D-001\n\nSee [D-002](D-002-b.md).\n' > "$tmp/complete/D-001-a.md"
  printf '# D-002\n\nReferenced by: [D-001](D-001-a.md).\n' > "$tmp/complete/D-002-b.md"
  verdict_case link-with-back-edge WEB_COMPLETE "$tmp/complete"

  # The same pair with the back edge missing.
  mkdir -p "$tmp/incomplete"
  printf '# D-001\n\nSee [D-002](D-002-b.md).\n' > "$tmp/incomplete/D-001-a.md"
  printf '# D-002\n\nNo back edge here.\n' > "$tmp/incomplete/D-002-b.md"
  count_case link-without-back-edge 1 "$tmp/incomplete"

  # THE FAILURE MODE THIS CHECK IS MOST LIKELY TO HAVE. A record's body mentions
  # D-numbers constantly. If the back edge were looked for anywhere in the file
  # rather than on the `Referenced by:` line, this fixture would read as
  # complete and the checker would be green by construction.
  mkdir -p "$tmp/prose"
  printf '# D-001\n\nSee [D-002](D-002-b.md).\n' > "$tmp/prose/D-001-a.md"
  printf '# D-002\n\nD-001 is discussed at length here, in prose, with no back edge line.\n' \
    > "$tmp/prose/D-002-b.md"
  count_case prose-mention-is-not-an-edge 1 "$tmp/prose"

  # A D-number in prose is not a forward link either — the other direction of
  # the same rule. Nothing is owed here, so the web is complete.
  mkdir -p "$tmp/prose-forward"
  printf '# D-001\n\nD-002 is mentioned but never linked.\n' > "$tmp/prose-forward/D-001-a.md"
  printf '# D-002\n\nNothing.\n' > "$tmp/prose-forward/D-002-b.md"
  verdict_case prose-link-is-not-an-edge WEB_COMPLETE "$tmp/prose-forward"

  # A record linking ITSELF is a table of contents, not an edge. Without the
  # self-drop every record becomes its own citer and every record owes itself a
  # back edge — the count would never reach zero.
  mkdir -p "$tmp/self"
  printf '# D-001\n\nThis record, [D-001](D-001-a.md), links itself.\n' > "$tmp/self/D-001-a.md"
  verdict_case self-link-is-not-an-edge WEB_COMPLETE "$tmp/self"

  # A link to a record that is not in the directory is dangling, and belongs to
  # whoever owns the roster. It must not be counted as an edge owed.
  mkdir -p "$tmp/dangling"
  printf '# D-001\n\nSee [D-099](D-099-gone.md).\n' > "$tmp/dangling/D-001-a.md"
  verdict_case dangling-link-is-not-owed WEB_COMPLETE "$tmp/dangling"

  # Two citers, one named. The count must be 1 rather than 0 or 2 — a partial
  # line is the shape #1394 exists for, and it is the one that reads as answered.
  mkdir -p "$tmp/partial"
  printf '# D-001\n\nSee [D-003](D-003-c.md).\n' > "$tmp/partial/D-001-a.md"
  printf '# D-002\n\nSee [D-003](D-003-c.md).\n' > "$tmp/partial/D-002-b.md"
  printf '# D-003\n\nReferenced by: [D-001](D-001-a.md).\n' > "$tmp/partial/D-003-c.md"
  count_case partially-named-citers 1 "$tmp/partial"

  # ---- the writing half -----------------------------------------------------

  wrote_case() {                # wrote_case <name> <want-wrote> <dir>
    local got
    got="$(write_edges "$3" | grep -oE 'wrote=[0-9]+$' | cut -d= -f2)"
    if [ "$got" = "$2" ]; then
      pass=$((pass + 1)); printf 'BACKEDGE case=%-24s want=wrote=%-11s got=wrote=%-11s OK\n' "$1" "$2" "$got"
    else
      fail=$((fail + 1)); printf 'BACKEDGE case=%-24s want=wrote=%-11s got=wrote=%-11s BROKEN\n' "$1" "$2" "$got"
    fi
  }

  # A record with a citer and no line gains one, and the line is the citer.
  mkdir -p "$tmp/w1"
  printf '# D-001\n\nSee [D-002](D-002-b.md).\n' > "$tmp/w1/D-001-a.md"
  printf '# D-002\n\nNo line.\n' > "$tmp/w1/D-002-b.md"
  wrote_case write-adds-a-missing-line 1 "$tmp/w1"
  if grep -q '^Referenced by: \[D-001\](D-001-a\.md)\.$' "$tmp/w1/D-002-b.md"; then
    pass=$((pass + 1)); printf 'BACKEDGE case=%-24s want=%-16s got=%-16s OK\n' write-line-names-the-citer present present
  else
    fail=$((fail + 1)); printf 'BACKEDGE case=%-24s want=%-16s got=%-16s BROKEN\n' write-line-names-the-citer present absent
  fi
  # And the sweep now agrees, which is the only proof that matters: the writer
  # and the reader must not disagree about what a back edge is.
  verdict_case write-closes-the-edge WEB_COMPLETE "$tmp/w1"
  # Idempotent — the second run finds a line and declines.
  wrote_case write-is-idempotent 0 "$tmp/w1"

  # THE REFUSAL. An INCOMPLETE existing line is left exactly as it stands. This
  # is `docs/adr/README.md`'s contract and it is also this mode's largest
  # limitation, so it is driven rather than assumed.
  mkdir -p "$tmp/w2"
  printf '# D-001\n\nSee [D-003](D-003-c.md).\n' > "$tmp/w2/D-001-a.md"
  printf '# D-002\n\nSee [D-003](D-003-c.md).\n' > "$tmp/w2/D-002-b.md"
  printf '# D-003\n\nReferenced by: [D-001](D-001-a.md).\n' > "$tmp/w2/D-003-c.md"
  wrote_case write-declines-an-existing-line 0 "$tmp/w2"
  count_case refusal-leaves-the-debt 1 "$tmp/w2"

  # A record nobody cites gains nothing — an empty `Referenced by:` line would
  # be a claim that the record was checked, which is exactly what it is not.
  mkdir -p "$tmp/w3"
  printf '# D-001\n\nAlone.\n' > "$tmp/w3/D-001-a.md"
  wrote_case write-adds-nothing-uncited 0 "$tmp/w3"

  printf 'BACKEDGE SELFTEST VERDICT %s cases=%d failed=%d\n' \
    "$([ "$fail" -eq 0 ] && printf PASS || printf FAIL)" "$((pass + fail))" "$fail"
  [ "$fail" -eq 0 ]
}

# ---------------------------------------------------------------- the doors

links=0; missing=0; records=0; with_line=0

case "$MODE" in
  selftest) selftest; exit $? ;;
  census)   report "$ADR_DIR" no; exit $? ;;
  check)    report "$ADR_DIR" yes; exit $? ;;
  write)    write_edges "$ADR_DIR"; exit $? ;;
esac
