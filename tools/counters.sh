#!/usr/bin/env bash
# tools/counters.sh — does a probe's catalog row still name the counters its
# bench row pins? (#1356, narrowed by #1369)
#
# Usage: tools/counters.sh              count the pinned counters and the named ones
#        tools/counters.sh --list       one row per pinned counter, named or not
#        tools/counters.sh --selftest   run the reader's cases; no probe is executed
#        tools/counters.sh --help | -h  print this clause, and stop
#
# THE FINDING. `roster_check` asks whether a probe HAS a row (#1177). Nothing
# asked whether the row still DESCRIBES the probe, so an instrument can grow a
# counter, a mode or a whole second question while its row keeps describing the
# instrument it was two units ago. #1319 repaired two such rows by hand; three
# counters have arrived since with their rows written in the same PR. The habit
# took. The mechanism did not, and nothing would catch the fourth.
#
# WHY THE BENCH AND NOT THE PROBE. A probe's counters live in two places: the
# format string in its source, and the EXACT LINE its bench row pins. #1369
# measured both. The wide reading — every `name=` token in any string literal in
# a probe's source — gives 621 tokens and 556 missing, a 90% rate that is almost
# entirely `seed=`, `ticks=` and progress-row noise. A check with that ratio is
# not a check. The bench row is the narrow reading and the right one: it is the
# CONTRACT, it is one line, and a counter that is pinned is a counter somebody
# decided the lane would judge.
#
# No probe is executed. This reads two files.
#
# REPORTED, NEVER JUDGED — deliberately, and this is the whole reason the unit is
# split. #1311's rule is that a gate installs at zero and not at one: at one it
# demands a unit from whoever trips it. The first run finds a backlog, so the
# order #1369 states is edits first and gate second. This tool is the number that
# makes the edits finishable and the gate installable at zero.

set -uo pipefail

cd "$(dirname "$0")/.."

BENCH=probes/bench.sh
CATALOG=probes/README.md
MODE=count
# `--bench` and `--catalog` point the READING at a scratch pair (#1511). `report` already
# took both as parameters and nothing could pass them, so the suite drove the three
# predicates and the reading that joins them was exercised only against the live tree —
# where every counter is named and `no_row` is zero, so no finding path had ever run.
#
# The same shape `flag_audit "$tmp/shop" "$tmp/shop/README.md"` has one directory over, and
# the reason it matters there is the reason it matters here: a checker whose findings are
# unreachable from a fixture is a checker whose findings appear for the first time on
# somebody's pull request.
while [ $# -gt 0 ]; do
  case "$1" in
    --list) MODE=list ;;
    --selftest) MODE=selftest ;;
    --bench) shift; [ $# -gt 0 ] || { echo "FATAL --bench wants a file" >&2; exit 2; }; BENCH="$1" ;;
    --catalog) shift; [ $# -gt 0 ] || { echo "FATAL --catalog wants a file" >&2; exit 2; }; CATALOG="$1" ;;
    # READ TO THE END OF THE CLAUSE, not to a line number (#1382, #1520): a door
    # added below it is in `--help` the moment it is in the header.
    -h|--help) awk 'NR==1 {next} !/^#/ {exit} /^#$/ {if (++blank == 2) exit} {print}' "$0"; exit 0 ;;
    *) echo "FATAL unknown argument: $1 (this tool takes --list, --bench FILE, --catalog FILE, --selftest, or nothing)" >&2; exit 2 ;;
  esac
  shift
done

# THE GUARD FAMILY, EXEMPT BY NAME AND NOT BY PATTERN (#1369, and #1207 is the
# precedent). These mean the same thing on every probe that carries one: the scan
# opened nothing, so an empty reading cannot print a clean line. Demanding that
# each row explain its own `checked_none=` asks for boilerplate rather than
# information — and a row that says "…_none= guards the empty reading" fifty times
# is a row nobody reads.
#
# A LIST, because a pattern (`*_none$`) exempts every counter a future probe
# happens to name that way, including one that means something specific. An
# exemption that grows on its own is a hole. Adding a name here costs a line and
# a reason, which is the price of the exemption being visible.
GUARDS='
births_none        the birth scan opened no birth
beats_none         no beat arose in the window
checks_none        the check ran over nothing
checked_none       the reading opened no file
compared_none      no pair was compared
samples_none       the sampler took no sample
scanned_none       the sweep scanned nothing
stale_none         no figure was compared
swept_none         the walk opened no file
read_none          neither side was read
sources_none       no source file was found
rows_none          the table had no rows
'

guard() {                         # guard <name> — 0 it is an exempt guard
  grep -qE "^[[:space:]]*$1[[:space:]]" <<< "$GUARDS"
}

# One bench row's class and the counters its pinned line carries.
#
# Continuation lines are joined FIRST, because the fattest rows wrap — the
# ledger's sweep row carries its arguments two lines down — and a reader that
# stops at the backslash reads half a contract. #1369's own figure of 44 was
# approximate for exactly this reason and is superseded by this reading.
bench_rows() {                    # bench_rows <bench file> — "<Class> <tok> <tok> …" per line
  awk '{ while (/\\$/) { sub(/\\$/,""); if ((getline nxt) > 0) $0 = $0 " " nxt; else break } print }' "$1" \
  | grep -E "^[[:space:]]*(judge|known|vary)[[:space:]]" \
  | while IFS= read -r row; do
      # `vary '<why>' judge <Class> '<line>'` — the why is a quoted string in
      # front of the verb, so it is removed before the verb is read. Without this
      # the class reads as `judge` and the why's own words read as counters.
      case "$row" in
        *vary*) row="$(sed -E "s/^[[:space:]]*vary[[:space:]]+'[^']*'[[:space:]]*//" <<< "$row")" ;;
      esac
      local cls line
      cls="$(sed -nE "s/^[[:space:]]*(judge|known|run)[[:space:]]+([A-Za-z][A-Za-z0-9]*).*/\2/p" <<< "$row")"
      [ -n "$cls" ] || continue
      line="$(sed -nE "s/^[^']*'([^']*)'.*/\1/p" <<< "$row")"
      # A row with no quoted line pins nothing — `run` rows are that shape, and a
      # row whose contract is empty is not a row with zero counters.
      [ -n "$line" ] || continue
      printf '%s %s\n' "$cls" \
          "$(grep -oE '[A-Za-z][A-Za-z0-9_]*=' <<< "$line" | tr -d '=' | sort -u | tr '\n' ' ')"
    done
}

# Does this row NAME the counter? The token followed by `=`, and that is a
# correction rather than a choice (#1369). A substring read reported `ticks` as
# named because the row said "6,000 ticks", and the published figure was wrong in
# the direction that hides the finding. The `=` is what makes the mention a
# mention of a COUNTER and not of the word.
names() {                         # names <row> <token>
  case "$2" in "$1") return 1 ;; esac   # unreachable guard: keeps shellcheck honest
  case "$1" in *"$2="*) return 0 ;; *) return 1 ;; esac
}

report() {                        # report <bench> <catalog>
  local bench="$1" catalog="$2"
  [ -r "$bench" ] || { echo "FATAL cannot read $bench" >&2; return 3; }
  [ -r "$catalog" ] || { echo "FATAL cannot read $catalog" >&2; return 3; }

  local rows=0 pinned=0 named=0 missing=0 exempt=0 norow=0 nocounter=0 printsnone=0
  local cls toks tok row
  while read -r cls toks; do
    rows=$((rows + 1))
    row="$(grep "^| \`$cls\` |" "$catalog" | head -1)"
    if [ -z "$row" ]; then
      # A class with no catalog row is `roster_check`'s finding and not this
      # one's (#1170): one absence reported by two checks is one defect counted
      # twice. Counted here so the identity closes.
      norow=$((norow + 1))
      continue
    fi
    # TWO POPULATIONS, NOT ONE (#1579). `row_no_counter=` asks whether the PINNED
    # verdict carries a field — a real property, because an exact-line row with
    # nothing in it that can move goes green on a probe whose numbers all changed.
    # It is NOT the property #1372 asked about, and the sentence beside the old
    # single count said the second thing: `SameTick` prints five counters and pins a
    # word, so it landed in the subclass while being one of the better-instrumented
    # probes in the directory.
    #
    # `prints_no_counter=` is the harder half: does the probe print a `name=value`
    # ANYWHERE. A probe in both is one nothing can watch. A probe in the first only
    # is a row that should probably pin one of the fields it already prints, which
    # is a repair with an obvious shape — and the reason separating them is worth a
    # second counter rather than a footnote.
    #
    # The read is textual and generous: any `name=` inside a string literal, with
    # javadoc and comment lines dropped first. It over-counts a probe that mentions
    # `x=` in prose it prints, which understates the finding and can never invent
    # one — the safe direction for a census (#1207).
    # It counts ROWS and not probes, beside
    # `rows=` and for the same reason: a probe with two judged rows is two pinned
    # lines and either can be the one nothing can move. `VERDICT X_HELD` and nothing after it
    # cannot be told apart from a row that always prints that line: there is no
    # number in it to move, so the only way to watch the probe fail is to break the
    # world it judges and look. #1372 asked for this subclass to be MEASURED, and it
    # is the decidable half of that issue — the other half, *has this probe ever been
    # seen red*, has three sources and none of them is a lane check.
    #
    # REPORTED, never judged, and the reason is in the issue: many of these judge a
    # SIMULATION, and their falsifier is the world itself — `BondScenario` reads a
    # 40,000-tick run and would report differently on a broken one with no counter to
    # show for it. A gate here would demand a number from a probe that has nothing to
    # count, which is how a rule gets exempted the first time it is inconvenient.
    if [ -z "$toks" ]; then
      nocounter=$((nocounter + 1))
      [ "$MODE" = list ] && printf 'NO_COUNTER %-20s the verdict on this row carries no field that can move\n' "$cls"
      if [ -f "probes/$cls.java" ] \
         && [ "$(grep -vE '^[[:space:]]*(\*|//|/\*)' "probes/$cls.java" | grep -coE '"[^"]*[a-z_]+=')" = 0 ]; then
        printsnone=$((printsnone + 1))
        [ "$MODE" = list ] && printf 'PRINTS_NO_COUNTER %-14s and its source prints no name=value anywhere\n' "$cls"
      fi
    fi
    for tok in $toks; do
      pinned=$((pinned + 1))
      if guard "$tok"; then
        exempt=$((exempt + 1))
        [ "$MODE" = list ] && printf 'COUNTER %-20s %-18s EXEMPT\n' "$cls" "$tok"
      elif names "$row" "$tok"; then
        named=$((named + 1))
        [ "$MODE" = list ] && printf 'COUNTER %-20s %-18s NAMED\n' "$cls" "$tok"
      else
        missing=$((missing + 1))
        printf 'UNNAMED %-20s %-18s its row does not say what this counts\n' "$cls" "$tok"
      fi
    done
  done <<< "$(bench_rows "$bench")"

  # The populations ride the census (#1221). `pinned = named + missing + exempt`
  # closes by construction and every path through the token loop increments
  # exactly one term, which the suite's closed-set case asserts rather than argues
  # (the #1443 lesson, one directory over).
  echo "COUNTERS_CENSUS rows=$rows pinned=$pinned named=$named exempt=$exempt no_row=$norow row_no_counter=$nocounter prints_no_counter=$printsnone bench=$bench catalog=$catalog"

  # `pinned_none=` rides the VERDICT, because a reading that found no bench row
  # must not print the line a fully-named catalog prints (#1207). Nothing read is
  # the finding, not a clean result over an empty set.
  if [ "$pinned" -eq 0 ]; then
    echo "COUNTERS VERDICT NOTHING_READ — an empty reading is not a named catalog" >&2
    return 4
  fi
  echo "COUNTERS VERDICT COUNTED pinned=$pinned named=$named missing=$missing exempt=$exempt pinned_none=0"
  return 0
}

selftest() {
  local pass=0 fail=0
  tmp="$(mktemp -d "${TMPDIR:-/tmp}/counters.XXXXXX")"
  trap 'rm -rf "${tmp:-}"' EXIT

  row_case() {                    # row_case <name> <want tokens> <bench body>
    local name="$1" want="$2" got
    printf '%s\n' "$3" > "$tmp/bench.sh"
    got="$(bench_rows "$tmp/bench.sh" | tr -s ' ' | sed 's/ $//' | tr '\n' '|')"
    if [ "$want" = "$got" ]; then
      pass=$((pass + 1)); printf 'COUNTERS case=%-24s want=[%s] got=[%s] OK\n' "$name" "$want" "$got"
    else
      fail=$((fail + 1)); printf 'COUNTERS case=%-24s want=[%s] got=[%s] BROKEN\n' "$name" "$want" "$got"
    fi
  }

  row_case judge-row       'Alpha a b|'  "  judge Alpha 'VERDICT X a=0 b=1'"
  # The arguments after the quoted line are not counters. `"\$TICKS"` and
  # `--sweep 0..59 6000` both sit there and neither is part of the contract.
  row_case args-after-line 'Beta c|'     "  judge Beta 'VERDICT Y c=0'   \"\$TICKS\" --sweep 0..59"
  # A wrapped row is one row. The ledger's fattest row wraps, and a reader that
  # stops at the backslash reads half a contract.
  row_case wrapped-row     'Gamma d e|'  "  judge Gamma 'VERDICT Z d=0 e=2' \\
        --sweep 0..59 6000"
  # `vary` puts a quoted REASON in front of the verb. Read naively the class is
  # `judge` and the reason's own words are counters.
  row_case vary-row        'Delta f|'    "  vary  'rates move with the draw' judge Delta 'VERDICT W f=0'"
  # `known` is the third verb and pins a line like the others (#1231).
  row_case known-row       'Eps g|'      "  known Eps 'VERDICT BROKEN g=yes' '#1231'"
  # A row with no quoted line pins nothing, and that is not a row with zero
  # counters — it is not a contract at all.
  row_case no-quoted-line  ''            "  run Zeta \"\$TICKS\""

  name_case() {                   # name_case <name> <want 0=named 1=not> <row> <token>
    local name="$1" want="$2" got
    if names "$3" "$4"; then got=0; else got=1; fi
    if [ "$want" = "$got" ]; then
      pass=$((pass + 1)); printf 'COUNTERS case=%-24s want=%s got=%s OK\n' "$name" "$want" "$got"
    else
      fail=$((fail + 1)); printf 'COUNTERS case=%-24s want=%s got=%s BROKEN\n' "$name" "$want" "$got"
    fi
  }

  name_case names-the-counter    0 'the row explains `swept=` at length' swept
  # THE CASE THAT IS THE WHOLE REASON THE MATCHER IS WHAT IT IS. A substring read
  # called this named, because the row says the word. The published figure was
  # wrong in the direction that hides the finding, and this is the correction.
  name_case names-only-the-word  1 'runs for 6,000 ticks before judging'  ticks
  name_case empty-row            1 ''                                    swept

  guard_case() {                  # guard_case <name> <want 0=exempt 1=not> <token>
    local name="$1" want="$2" got
    if guard "$3"; then got=0; else got=1; fi
    if [ "$want" = "$got" ]; then
      pass=$((pass + 1)); printf 'COUNTERS case=%-24s want=%s got=%s OK\n' "$name" "$want" "$got"
    else
      fail=$((fail + 1)); printf 'COUNTERS case=%-24s want=%s got=%s BROKEN\n' "$name" "$want" "$got"
    fi
  }

  guard_case guard-listed        0 swept_none
  # THE EXEMPTION IS A LIST AND NOT A PATTERN, and this is the case that says so:
  # a name the list does not carry is not exempt however much it looks like one.
  # A pattern would exempt every future `*_none` including one that means
  # something specific, and an exemption that grows on its own is a hole.
  guard_case guard-not-a-pattern 1 harvest_none
  guard_case guard-ordinary      1 broken

  # ---- the reading itself (#1511) ------------------------------------------
  #
  # The three predicates above are pure functions and were the whole suite, so the READING
  # that joins them ran only against the live tree — where `missing=0` and `no_row=0`, which
  # means no finding path had ever executed. A checker whose findings are unreachable from a
  # fixture is a checker whose findings appear for the first time on somebody's pull request.
  #
  # `report` already took both files as parameters; nothing could pass them until `--bench`
  # and `--catalog` landed. Now the join is driven over two-line fixtures, and each case
  # asserts the whole verdict line rather than one field — a census that agreed on `missing=`
  # and disagreed on `exempt=` would pass a narrower check.
  read_case() {                   # read_case <name> <want-tail> <bench-body> <catalog-body>
    local name="$1" want="$2" got
    printf '%s\n' "$3" > "$tmp/b.sh"
    printf '%s\n' "$4" > "$tmp/c.md"
    got="$(MODE=count report "$tmp/b.sh" "$tmp/c.md" 2>/dev/null | grep '^COUNTERS VERDICT' \
           | sed 's/^COUNTERS VERDICT //')"
    if [ "$want" = "$got" ]; then
      pass=$((pass + 1)); printf 'COUNTERS case=%-24s want=[%s] got=[%s] OK\n' "$name" "$want" "$got"
    else
      fail=$((fail + 1)); printf 'COUNTERS case=%-24s want=[%s] got=[%s] BROKEN\n' "$name" "$want" "$got"
    fi
  }

  read_case read:all-named 'COUNTED pinned=2 named=2 missing=0 exempt=0 pinned_none=0' \
      "  judge Alpha 'VERDICT X a=0 b=1'" \
      '| `Alpha` | does a thing. It carries `a=` and `b=`. |'
  # THE FINDING PATH, executed for the first time by this case: one counter pinned and not
  # named. Before #1511 nothing in the suite could reach this line.
  read_case read:one-unnamed 'COUNTED pinned=2 named=1 missing=1 exempt=0 pinned_none=0' \
      "  judge Alpha 'VERDICT X a=0 b=1'" \
      '| `Alpha` | does a thing. It carries `a=` and nothing else. |'
  # A guard is exempt and does not count as named — the third term, reachable only here.
  read_case read:guard-exempt 'COUNTED pinned=2 named=1 missing=0 exempt=1 pinned_none=0' \
      "  judge Alpha 'VERDICT X a=0 swept_none=0'" \
      '| `Alpha` | does a thing. It carries `a=`. |'

  # THE NO-COUNTER SUBCLASS (#1372), driven over the CENSUS line rather than the
  # verdict, because that is where it rides — the verdict pins the contract and the
  # census carries populations (#1221).
  census_case() {                 # census_case <name> <want-row_no_counter> <bench-body> <catalog-body>
    local name="$1" want="$2" got
    printf '%s\n' "$3" > "$tmp/b.sh"
    printf '%s\n' "$4" > "$tmp/c.md"
    got="$(MODE=count report "$tmp/b.sh" "$tmp/c.md" 2>/dev/null | grep '^COUNTERS_CENSUS' \
           | sed 's/.*row_no_counter=\([0-9]*\).*/\1/')"
    if [ "$want" = "$got" ]; then
      pass=$((pass + 1)); printf 'COUNTERS case=%-24s want=%s got=%s OK\n' "$name" "$want" "$got"
    else
      fail=$((fail + 1)); printf 'COUNTERS case=%-24s want=%s got=%s BROKEN\n' "$name" "$want" "$got"
    fi
  }
  # A verdict with a field is not in the subclass, however few fields it has.
  census_case census:has-a-counter 0 \
      "  judge Alpha 'VERDICT X a=0'" \
      '| `Alpha` | does a thing. It carries `a=`. |'
  # THE SUBCLASS: a word and nothing after it. Nothing in the line can move, so the
  # row cannot be told apart from a row that always prints it.
  census_case census:bare-verdict 1 \
      "  judge Alpha 'VERDICT X_HELD'" \
      '| `Alpha` | does a thing. |'
  # It counts ROWS, not probes: a probe with two judged rows and one bare verdict is
  # one finding, not zero and not two.
  census_case census:two-rows-one-bare 1 \
      "  judge Alpha 'VERDICT X_HELD'
  judge Alpha 'VERDICT Y b=1'" \
      '| `Alpha` | does a thing. It carries `b=`. |'
  # A row whose class has no catalog entry is roster_check's finding and is skipped
  # BEFORE this count, so it cannot inflate the subclass (#1170).
  census_case census:no-row-is-not-bare 0 \
      "  judge Ghost 'VERDICT X_HELD'" \
      '| `Alpha` | does a thing. |'

  # THE SECOND POPULATION (#1579), which needs a probe SOURCE and not only a bench
  # row — so the fixtures write one. `prints_no_counter` is a SUBSET of
  # `row_no_counter` by construction: it is only asked of a row already in the
  # first, which is the relationship the two cases below pin.
  prints_case() {                 # prints_case <name> <want> <bench-body> <catalog-body> <source>
    local name="$1" want="$2" got
    printf '%s\n' "$3" > "$tmp/b.sh"
    printf '%s\n' "$4" > "$tmp/c.md"
    mkdir -p "$tmp/probes"
    printf '%s\n' "$5" > "$tmp/probes/Alpha.java"
    got="$(cd "$tmp" && MODE=count report b.sh c.md 2>/dev/null | grep '^COUNTERS_CENSUS' \
           | sed 's/.*prints_no_counter=\([0-9]*\).*/\1/')"
    if [ "$want" = "$got" ]; then
      pass=$((pass + 1)); printf 'COUNTERS case=%-24s want=%s got=%s OK\n' "$name" "$want" "$got"
    else
      fail=$((fail + 1)); printf 'COUNTERS case=%-24s want=%s got=%s BROKEN\n' "$name" "$want" "$got"
    fi
  }
  # THE LIVE SHAPE, and the whole point of the split: a bare pinned verdict on a
  # probe that prints five counters. `SameTick` is this, and the single count read
  # it as a probe nobody can watch.
  prints_case prints:bare-row-rich-probe 0 \
      "  judge Alpha 'VERDICT X_HELD'" \
      '| `Alpha` | does a thing. |' \
      'class Alpha { void m() { System.out.println("ALPHA census=1 late=0"); } }'
  # And the population that is actually blind: nothing pinned and nothing printed.
  prints_case prints:bare-row-bare-probe 1 \
      "  judge Alpha 'VERDICT X_HELD'" \
      '| `Alpha` | does a thing. |' \
      'class Alpha { void m() { System.out.println("VERDICT X_HELD"); } }'
  # A counter named only in a COMMENT is not printed. Same self-matching shape the
  # Java-side readers have met five times (#1531).
  prints_case prints:counter-in-a-comment 1 \
      "  judge Alpha 'VERDICT X_HELD'" \
      '| `Alpha` | does a thing. |' \
      'class Alpha {
  /** prints census=N one day. */
  void m() { System.out.println("VERDICT X_HELD"); }
}'
  # `no_row` is the term `roster_check` cannot cover, and it rides the census — so the
  # VERDICT stays clean while a judged class has no row at all. The fixture carries one
  # class with a row and one without, because the verdict is what this case reads and a
  # rowless class alone leaves `pinned=0`, which is the NOTHING_READ refusal on stderr and a
  # different finding (#1207). That distinction is the reason the case is written this way
  # rather than with the obvious one-line fixture.
  read_case read:no-row 'COUNTED pinned=1 named=1 missing=0 exempt=0 pinned_none=0' \
      "  judge Alpha 'VERDICT X a=0'
  judge Ghost 'VERDICT Y z=0'" \
      '| `Alpha` | does a thing. It carries `a=`. |'
  # And the refusal itself, which is the other end of the same axis: nothing pinned is not a
  # fully-named catalog, and it leaves 4 rather than printing a clean line.
  printf '%s\n' "  run Alpha" > "$tmp/b.sh"
  printf '%s\n' '| `Alpha` | positional only. |' > "$tmp/c.md"
  rc=0
  MODE=count report "$tmp/b.sh" "$tmp/c.md" >/dev/null 2>&1 || rc=$?
  if [ "$rc" = 4 ]; then
    pass=$((pass + 1)); printf 'COUNTERS case=%-24s want=4 got=%s OK\n' read:nothing-pinned "$rc"
  else
    fail=$((fail + 1)); printf 'COUNTERS case=%-24s want=4 got=%s BROKEN\n' read:nothing-pinned "$rc"
  fi

  echo "COUNTERS SELFTEST VERDICT $([ "$fail" -eq 0 ] && echo PASS || echo FAIL) cases=$((pass + fail)) failed=$fail"
  [ "$fail" -eq 0 ]
}

if [ "$MODE" = selftest ]; then
  selftest
  exit $?
fi

report "$BENCH" "$CATALOG"
exit $?
