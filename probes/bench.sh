#!/usr/bin/env bash
# probes/bench.sh — the whole bench, one command, one verdict.
#
# Usage: probes/bench.sh [ticks]        sweep every probe (default 6000 ticks)
#        probes/bench.sh --list         print the contract table, run nothing
#        probes/bench.sh --no-build     sweep without recompiling
#        probes/bench.sh --twice        sweep, then run each probe a second
#                                       time and byte-compare the two outputs
#        probes/bench.sh --twice-changed <classes...>
#                                       the same second run, narrowed to the
#                                       probes a change touched (#1185). This is
#                                       the form the lane passes; a probe nobody
#                                       edited cannot have started drifting for a
#                                       reason this pull request is answerable for
#                                       Runs a selected probe THREE times, not two
#                                       (#1355): one comparison is not enough for a
#                                       field whose drift is probabilistic
#        probes/bench.sh --without-probes
#                                       build src/ and selftest with probes/
#                                       deleted, in a throwaway copy of HEAD
#        probes/bench.sh --help | -h   print this clause, and stop
#
# The bench was fifteen programs invoked by hand while CI judged four of them,
# with the expected verdict lines inlined in a workflow file most contributors
# never open. That is the wrong home for the bench's contract: the catalog is
# in probes/README.md, the verdicts are in the probes, and the list of what
# gets judged belongs next to them. This runner is that list.
#
# Since #880 the workflow runs this script instead of keeping a second list of
# its own — the two had drifted until two thirds of the judged rows here
# reached no runner but a human's hands, and a probe CI does not run is a probe
# that guards nothing between two people remembering it. Adding a row below is
# therefore adding a lock: no YAML edit, no second place to remember.
#
# The discipline the CI lane already got right, kept exactly: a judged probe is
# judged by EXACT-LINE grep (grep -qxF), so `=0` can never match `=01`, and a
# missing verdict line fails the sweep. A reporting probe is RUN — a crash or a
# nonzero exit is a failure, but no verdict is demanded. Adding a probe is a
# one-row change in the table below, beside the probe, not in a YAML.
#
# Zero dependency by rule (D-040): javac, java, sh. No build tool, no plugin.
# Rollback is deleting this file.

set -euo pipefail

cd "$(dirname "$0")/.."

TICKS=6000
BUILD=yes
LIST=no
TWICE=no
TWICE_ONLY=""
WITHOUT=no
for arg in "$@"; do
  case "$arg" in
    --list) LIST=yes ;;
    --no-build) BUILD=no ;;
    --twice) TWICE=yes ;;
    # The determinism pass, narrowed to the probes a change touches (#1185).
    #
    # WHY NARROWED: `--twice` runs every probe twice, so it costs about double the
    # sweep — and the sweep's own trailer already measures itself against the lane's
    # ceiling on every run:
    #
    #   bash probes/bench.sh | tail -1        # secs= against budget=, judged WITHIN
    #
    # The argument is the RELATIONSHIP and not a figure: a doubled sweep exceeds the
    # budget, so the full pass has never been in a lane. This comment used to say "a
    # 208 s sweep", which was true once — the sweep moved 104 → 117 → 166 in a single
    # day as rows landed, one of which runs a 4,000-tick simulation (#660). The
    # argument survived that, by thirty-two seconds rather than the hundred the figure
    # implied, and nobody reading the sentence could have known (#1332).
    #
    # `INSTRUMENTS_DRIFTED` was a verdict nothing read until the pass was run by hand
    # and immediately found one (#1184). This is the shape that matches how the defect
    # arrives: a probe added or edited without its author running the pass. The rest of
    # the bench is not re-run, because a probe nobody touched cannot have started drifting
    # for a reason this pull request is responsible for.
    --twice-changed) TWICE=yes; TWICE_ONLY="__next__" ;;
    --without-probes) WITHOUT=yes ;;
    # READ TO THE END OF THE CLAUSE, not to a line number (#1382, #1520): a door
    # added below it is in `--help` the moment it is in the header.
    -h|--help) awk 'NR==1 {next} !/^#/ {exit} /^#$/ {if (++blank == 2) exit} {print}' "$0"; exit 0 ;;
    *)
      # A positional argument is the tick budget, unless --twice-changed is waiting for
      # its class list, in which case it is that.
      if [ "$TWICE_ONLY" = "__next__" ]; then
        TWICE_ONLY="$arg"
      else
        case "$arg" in
          ''|*[!0-9]*) echo "FATAL unknown argument: $arg" >&2; exit 2 ;;
          *) TICKS="$arg" ;;
        esac
      fi ;;
  esac
done
[ "$TWICE_ONLY" = "__next__" ] && { echo "FATAL --twice-changed wants a comma-separated class list" >&2; exit 2; }

# ---------------------------------------------------------------------------
# The contract table. One row per probe:
#
#   judge <Class> '<the exact line the run must print>' [args...]
#   run   <Class> [args...]
#
# prefixed, on either of them, by:
#
#   vary  '<why its output may legitimately move>' judge|run <Class> ...
#
# The args are the probe's own, in its own order — the bench does not pretend
# every instrument takes the same ones. $TICKS is spent where a probe's cost
# scales with the arc; the census sweeps carry their own smaller ranges so the
# lane's wall clock stays a lane's, not a laboratory's.
#
# `vary` is a MODIFIER, not a third verb: it declares an exemption from the
# --twice determinism pass with the reason stated in the row rather than
# skipped in silence, and then runs whichever verb follows it. It was a verb
# while it was a synonym for "run and forgive the drift", and that spelling
# quietly made the two questions one — what the sweep DEMANDS of a row, and
# whether that row's bytes may differ between two identical runs. They are
# separate, and AllocMeter is the row that separates them: its verdict line is
# fixed while the byte counts printed beside it move with the JIT (#906). It
# stays the rarest thing in the table, because an instrument whose output moves
# between two identical runs cannot be diffed across a change, which is most of
# what a bench is for.
#
# HOW MANY ROWS WEAR IT is a question with a command rather than an answer with
# a date (#1330):
#
#   grep -c '^  vary ' probes/bench.sh
#
# This comment said "exactly one row wears it today" and was wrong before
# #1329 made it wronger: UnparkStorm had taken a second one and the sentence
# never followed. The count carried the rule's whole weight — a table with one
# exemption is a table where the exemption is remarkable, and a table with five
# is one where `vary` is how a flaky probe gets past the sweep — so a number
# nobody re-ran was the worst possible place to keep it.
#
# WHICH rows, on the other hand, is short enough to be prose that ages
# honestly, and it is the part a reader needs. Every one earns it the same way:
# by printing its own noise BESIDE the world's numbers rather than in place of
# them, with the verdict line fixed and the noise on a neighbouring line.
#
#   AllocMeter    the JIT's cold sample beside a median of twenty-four
#                 repeats (#817, #906)
#   UnparkStorm   nanosecond percentiles beside a bounded worst case
#   DocFigures    wall-clock beside checked= and docs=, because two of its
#                 markers run the daemon (#1302, #1328)
# ---------------------------------------------------------------------------
table() {
  judge LedgerMirror 'LEDGER_ANOMALIES=0 compared_none=0'      "$TICKS"
  # The sweep the javadoc used to assert in prose (#1130). The row names the
  # broken seeds rather than demanding zero, because #1090 is open and a lane
  # that goes red for a known defect gets switched off — but it is regenerated
  # on every push, so a NEW break, a FIXED seed, and a range that silently
  # shrinks are all three the same red row. When #1090 lands, this line becomes
  # `clean=60 broken=0 at -` in the PR that fixes it.
  #
  # The range is 0..59 rather than 0..19 since #1150: the serial sweep cost 43 s over
  # sixty seeds and the row was narrowed to twenty to afford it, which left three known
  # breaks (34, 49, 52) outside the only thing watching. #1147 removed the constraint that
  # forced serial, and sixty seeds in parallel now cost 16 s — three times the coverage
  # for less than the old twenty took.
  # The budget is pinned at the full arc rather than inherited from $TICKS: the
  # row names the seeds that break AT 6,000 ticks, and a shorter run would name
  # a different set — a row whose claim changes with the caller's argument is
  # not a lock. `bench.sh 2000` still runs it, and still runs it over the arc.
  judge LedgerMirror 'LEDGER_SWEEP seeds=0..59 ticks=6000 clean=53 broken=7 at 4,7,8,13,34,49,52' \
                                               --sweep 0..59 6000
  judge OneTrace     'VERDICT CONTRACT_HELD births_none=0'   "$TICKS"
  judge ArcBeats     'VERDICT BEATS_IN_ORDER beats_none=0'  "$TICKS"
  judge CapSentinel  'CAP_BREACHES=0 samples_none=0'          "$TICKS"
  judge LinkAudit    'VERDICT CLEAN'           "$TICKS"
  judge PirateSever  'VERDICT CONTRACT_HELD checks_none=0'
  judge BoardScope   'VERDICT BOARD_SCOPE_HONEST lost_present=0 stale_none=0'
  judge PodOptional  'VERDICT POD_OPTIONAL_HELD' "$TICKS"
  judge DoorPressure 'VERDICT DOOR_PRESSURE_HELD' "$TICKS"

  # The second universe (#1094). A PROPERTY row claims something true of every world —
  # a contract held, a breach count of zero — and one seed cannot support that claim: three
  # units in one night found defects seed 42 cannot see. Seed 7 is already this tree's
  # second canonical universe and every probe here already takes one. ARC rows are NOT
  # doubled: a beat tick, a mover count, a named subject belong to the canonical film, and
  # asserting them at another seed would be asserting a different measurement.
  # Measured: six rows, 6.4 s. The lane's budget is 300 s (#1115).
  judge OneTrace     'VERDICT CONTRACT_HELD births_none=0'      "$TICKS" 7
  judge CapSentinel  'CAP_BREACHES=0 samples_none=0'             "$TICKS" 7
  judge LinkAudit    'VERDICT CLEAN'              "$TICKS" 7
  judge PirateSever  'VERDICT CONTRACT_HELD checks_none=0'      "$TICKS" 7
  judge PodOptional  'VERDICT POD_OPTIONAL_HELD'  "$TICKS" 7
  judge DoorPressure 'VERDICT DOOR_PRESSURE_HELD' "$TICKS" 7
  judge ArcBeats     'VERDICT BEATS_IN_ORDER beats_none=0'     "$TICKS" 7
  judge BondBook     'VERDICT BOOK_TURNS_OVER'    "$TICKS" 7
  judge SameTick     'VERDICT SAME_TICK_ABSORB'   "$TICKS" 7
  # Three probes that declared themselves ONE-OFF and were not (#1175). #1163's exemption
  # let a probe excuse itself with four words in a javadoc, and all six sentences were
  # written in one sitting by the person who wanted the check to pass. Measured instead:
  # 0.8 s, 4.9 s and 16.4 s, stable across two runs, against a lane budget of 300 s and a
  # measured 108 s. OrderTable was the tell — its own javadoc calls it "the keeper the root
  # door's draw-order table never had", and a KEEPER is the shape of a thing judged on
  # every push. The exemption survives for the two that genuinely cannot be judged: one
  # prints no verdict without arguments, the other REFUSES for want of a fixture.
  judge CensusSampleSize 'VERDICT SAMPLE_LAWS_PRICED'
  judge OrderTable   'VERDICT ORDER_TABLE_HELD orders=6 classes=4 silent=1'
  # `vary`, and it took --twice to find out (#1184). #1175 measured this probe's cost and
  # its verdict's stability across two runs and made it a judge row — but a judge row's
  # verdict is only half of what the sweep asks of it. The OTHER half is --twice, which
  # byte-compares the whole output, and this probe prints a QUIET line of nanosecond
  # percentiles beside its verdict:
  #
  #   a="QUIET ticks=4998 median_ns=2769875 … max_ns=15504958"
  #   b="QUIET ticks=4998 median_ns=2549125 … max_ns=12081916"
  #
  # Same world, same seed, different JIT. That is AllocMeter's situation exactly (#906,
  # #817) and it earns the same modifier for the same reason: the verdict is fixed, the
  # instrument noise printed beside it is not, and the exemption is DECLARED here rather
  # than discovered by whoever next runs the determinism pass.
  vary  'prints its own timing noise: the QUIET line carries nanosecond percentiles of the tick it measures, which move with the JIT while the bound they are judged against does not' \
        --lines '^(QUIET|STORM|TICKCOST) ' --cut 4 \
        judge UnparkStorm  'VERDICT UNPARK_STORM_BOUNDED worst=811 bound=1000'
  judge LineLint     'VERDICT GRAMMAR_HELD'    "$TICKS"
  judge BirthInputs  'VERDICT BIRTH_INPUTS_COMPLETE' "$TICKS"
  # Re-aimed by #764, not deleted. The row exists to pin the current KID_*
  # tuning as a contract so that widening the band goes red, and the count in
  # the judged line is the pin: the band is now open, and the number of births
  # it admits over seeds 1..20 at 600 windows is the thing a tuning moves.
  judge FateAtlas    'VERDICT BAND_OPEN admitted=5'
  judge HullRoster   'VERDICT ROSTER_TOTAL'      3000
  # The sentence beside the name. HullRoster pins what a hull is CALLED and
  # nothing pinned what the line announcing it CLAIMS, which is how one defect
  # reached main three times (#806, #948, #1056). Same shape as its neighbour:
  # a pure function walked over the ordinals, no universe, both values of the
  # loss flag at every ordinal that can carry one.
  judge FleetLines   'VERDICT FLEET_LINES_TRUE'  3000
  judge DistrictNeutral 'VERDICT DISTRICTS_DRAW_NOTHING'
  # Seed 42 explicitly rather than $TICKS: this probe's only argument is a
  # seed, and its cost is one boot, not an arc. Judged rather than run — the
  # off-pool and duplicate-quarter legs have verdicts, and a row that only
  # demanded survival would have accepted the NoSuchFieldException this probe
  # threw the moment #842 moved the pools out from under it (#892).
  judge DistrictCensus 'VERDICT CITY_CENSUSED' 42
  judge CensusBlocks 'SELFCHECK VERDICT MATH_OK'  --selfcheck
  # Two worlds alive in one JVM, judged the only way the SILENT form of #1135 shows:
  # each seed run alone, then all of them concurrently, chains compared link for link.
  # "It did not throw" is not the contract — before the fix, seed 42 diverged at link 1
  # with no exception at all while three others threw.
  # The world's own answer to which Bestiary list means "what exists" (#1142). SealHygiene
  # pins the hash of every id in EVERY and deliberately boots no world; this is the half
  # that needs one. reached=13 is not decoration: it moves the day a species is spawned
  # from an inline row again, which is the defect #974 split the list to end.
  judge SpeciesReach 'VERDICT EVERY_CONTAINS_THE_WORLD unlisted=0' "$TICKS"
  # What one universe shares with the next in the same JVM (#1148). TwoWorlds guards the
  # BEHAVIOUR at four seeds; this reads the tree, so a share that seed 42 never exercises is
  # still named. Twelve sites, all safe, all now SAYING they are safe — the sentence that was
  # missing from two scratch buffers for as long as they were wrong. Costs no universe.
  judge SharedState  'VERDICT EVERY_SHARE_IS_DECLARED undeclared=0'
  # The shelf's first inhabitant, and the reader it promised (#255/#260/#603). A spec
  # nobody can falsify is a wish with a filename, so docs/spec/README.md's rule is that a
  # spec lands with its probe in one pull request. This is that probe: the roster in
  # docs/spec/instrument-lines.md against the families a private universe prints.
  judge SpecDrift    'VERDICT SPEC_HOLDS unpredicted=0 unseen=0 field_drift=0 read_none=0' "$TICKS"
  # The splitter's own cases (#1508). `fieldsIn` became a pure function in #1442 and was
  # still driven only through a 6,000-tick arc — the one function here that parses, and one
  # whose comment says its correctness worked BY LUCK. Costs no universe: six string cases.
  judge SpecDrift    'SPEC SELFTEST VERDICT PASS cases=7 failed=0' --selftest
  # The three sentences docs/ARCHITECTURE.md calls "verified by grep", with the grep
  # (#1417). Nothing read any of them: `entities` importing nothing from `realworld`,
  # `World` holding no real-world object, nothing depending on `Main`. All three were
  # true, which is the reason — SheetFence's shape, a law that is true by luck reading
  # exactly like a law that is enforced. The widest instance of it in the tree: the
  # entities/realworld split IS this repository's thesis. Reads src/, builds no universe.
  judge LatticeFence 'VERDICT LATTICE_HELD entities_reach=0 world_holds=0 main_depended=0 swept_none=0'
  # The row #1153 wrote in its PR body and never committed (#1162). Its `perl` substitution
  # was anchored on `judge SpeciesReach`, which was on a different branch at the time; the
  # pattern matched nothing, perl exited 0, and the tree gained an instrument that ran on
  # nobody's push. Costs no universe — it reads Config.
  judge BoundsCensus 'VERDICT EVERY_KNOB_IS_WIRED_OR_SAYS_SO silent=0'
  judge TwoWorlds    'VERDICT WORLDS_INDEPENDENT ticks=2000 worlds=4 diverged=0' 2000
  # The two copies of the chronos grammar, compared (#1468). ChronosLog spells each
  # record's field order in a print statement; ChronosLine.grammarOf spells the same
  # six lists again, one file over. "Copied deliberately" was the argument and nothing
  # was checking it — #1053's finding surviving one layer down, since that unit removed
  # the second READER and not the second COPY. Reads two source files, builds no
  # universe, and order counts: the reader searches by needle and would not notice.
  # Every door that parses a long option refuses one it does not know (#1479). The
  # convention was real and unwritten — fifteen of twenty-six refused and nothing said
  # which group a new probe was in — and it is judged by BEHAVIOUR rather than by grep,
  # because a refusal is spelled at least three ways in this tree. Runs its siblings, so
  # it costs twenty-six JVM starts and no universe: a refusal returns before the JVM has
  # warmed. `crashed=` rides the census and is reported, not judged (#1481).
  judge DoorRefusal  'VERDICT EVERY_DOOR_REFUSES swallowed=0 crashed=0 swept_none=0'
  judge GrammarTwins 'VERDICT GRAMMAR_TWINS_AGREE writer_only=0 reader_only=0 diverged=0 read_none=0'
  judge SealHygiene  'VERDICT SEAL_HYGIENE_HELD breaks=0 checked_none=0'
  # The contract on the rows above, read off this table. `by_hand=` is the honest half of
  # the count: those probes exit with their own code, which is not a lie, only a second
  # place the contract lives (#1214).
  #
  # It read 11, then 10, and it reads 5 now — and the drop is not five probes converting
  # to the helper. It is #1502: the count included five probes whose ONLY exit was the
  # argument-refusal door, so their failing verdict fell off the end of main at 0 while
  # this row called them stylistically different. Those five are fixed and the check no
  # longer reads a refusal as a verdict code. The comment carries the history because a
  # number that halved for two different reasons is a number a reader will otherwise
  # reconstruct wrongly.
  judge LeaveContract 'VERDICT EVERY_JUDGED_PROBE_HAS_A_CODE no_code=0 by_hand=3 undeclared=0'
  # THE READER'S OWN CASES (#1531). This check spent its whole life reading probe
  # sources with no way to be watched misreading one, and the misreading it was
  # capable of is the one that matters: it read comments. A javadoc sentence with
  # the words `System.exit` in it made CensusBeatDrift — a probe with no verdict
  # code at all — read as a probe that spends its own. Two of these five cases go
  # red against the pre-#1531 raw read; three hold either way and say what the
  # matcher is FOR, which is the pair a fixture suite needs to be worth running.
  judge LeaveContract 'LEAVE SELFCHECK VERDICT READER_HOLDS cases=41 failed=0' --selfcheck
  # A verdict with no denominator cannot tell the contract holding over everything
  # from the contract holding over nothing (#1373, re-measured by #1540). Two guards
  # are already in use here — a `_none=` field on the pinned line, and a NEVER_AROSE
  # that makes an empty population print a different word — and nothing counted how
  # many rows have neither. The verdict pins the READ and carries `unguarded=` as a
  # number beside it: converting twenty-seven probes is twenty-seven judgements about
  # what each denominator IS, and a gate demanding them in the unit that lands the
  # reader is a gate that gets exempted in the unit after it (#1207, #1095 -> #1311).
  judge VacuousGuard 'VERDICT VACUOUS_GUARD_COUNTED unguarded=27 judged_none=0'
  # probes/README.md is read for EXISTENCE in two directions — roster_check asks
  # whether every probe has a row (#1177), counters.sh whether a row names the
  # counters its bench row pins (#1356) — and in no direction for ACCURACY. One
  # directory over, advice.sh asks that six ways. Twenty flags are parsed by a probe
  # and named by no row; the verdict pins the READ and carries the count beside it,
  # because twenty row edits is twenty judgements about what each row should say
  # (#1207, #1095 -> #1311).
  judge CatalogFlags 'VERDICT CATALOG_FLAGS_COUNTED undocumented=20 checked_none=0'
  # The reading's own cases (#1576). This probe reads fifty-five sources with a rule
  # that has a KNOWN false positive — a greedy read collects the flags DocLint hands
  # to git — and that correction was asserted in a javadoc and demonstrated nowhere.
  # Writing the cases found a defect in the OTHER half: the row join was a plain
  # contains, so a row saying --prefix read as naming --pr.
  judge CatalogFlags 'CATALOG SELFCHECK VERDICT READER_HOLDS cases=21 failed=0' --selfcheck
  # The third verb, kept proven. `known` had zero rows and zero tests (#1231) —
  # and it is the verb the tree reaches for when a defect is real and the fix is
  # not ready, which is the worst moment to discover it stopped working. This
  # break will never be fixed: the probe exists to be broken, so the row runs on
  # every sweep and the pass-is-a-failure inversion is exercised rather than
  # believed. Its issue is itself, which is stated in the probe rather than hidden.
  known KnownFixture 'VERDICT KNOWN_FIXTURE_BROKEN by_design=yes issue=1231' '#1231'
  judge ConfirmationSweep 'VERDICT CONFIRMATIONS_HELD' "$TICKS"
  # The second seed found a defect (#1155) and this row was a `known` break for four hours.
  # Both halves turned out to be the PROBE describing truthfully-measured things wrongly:
  # `malformed` counted frames that correctly report a world with no agent in it (#1170),
  # and `max_gap` counted the window the tap had ANNOUNCED it could not see — the signal
  # line "lost — the dream is no longer theirs" sits inside every gap that missed the bound.
  # Both are now their own numbers, `no_agent=` and `dark_gap=`, and the clause holds at
  # seeds 42, 7, 4, 9 and 13. The row is a judge again, which is what a known break is for.
  judge ConfirmationSweep 'VERDICT CONFIRMATIONS_HELD' "$TICKS" 7
  judge HuntBound    'VERDICT HUNT_BOUND_HELD movers=19 breaks=0'  "$TICKS"
  # SheetBench holds two rows because it is two instruments behind one class,
  # and the table is keyed by (class, args) rather than by class. --discipline
  # prints a standalone verdict line and is judged like every other row.
  # --avalanche prints its measurements and its verdict on ONE line, so an
  # exact-line judge would pin mean_bitflip and max_axis_corr into this table
  # beside the bound the probe already prints and already checks — and the row
  # would then go red on a lawful mixer change the probe itself passed, which
  # is a false alarm the bench would have manufactured. It is `run` instead,
  # and it is judged all the same: --avalanche is the one mode in the bench
  # whose exit code is its verdict (`System.exit(avalanche())`, 1 when the
  # bound is missed), and `run` fails a row on a nonzero exit.
  judge SheetBench   'DISCIPLINE VERDICT PASS'   --discipline
  # BITFLIP_TOLERANCE had one reader and nothing that would notice a wrong value
  # (#1092). Set it to 0.5 and the mixer must be perfect; set it to 0.4 and
  # everything passes forever — and in neither case does a line explain why. The
  # bounds cannot be derived, so the cheaper half of that issue is taken: synthetic
  # figures each bound MUST refuse, so both have been watched saying no.
  judge SheetBench   'AVALANCHE SELFCHECK VERDICT BOUNDS_REFUSE cases=12 failed=0 bitflip_tolerance=0.01 corr_bound=0.15' --avalanche-selfcheck
  judge SheetBench   'VERDICT CAST_BOOT_AGREES bench=6 world=6' --boot-version
  run   SheetBench   --avalanche
  judge DocLint      'VERDICT DOCS_TRUE'         "$TICKS"
  judge DocLint      'SELFCHECK VERDICT DOCLINT_FALSIFIABLE' --selfcheck
  judge DocsRoster   'VERDICT EVERY_DOCUMENT_IS_NAMED orphans=0 scanned_none=0'
  # Three populations exit with codes a script branches on, and until #1219 two
  # documents each said they agreed. They do not: 2 is NEVER_AROSE here and a
  # refusal in tools/, 3 is a refusal here and an unreadable answer there. The
  # split is declared in a marker on the grammar page and this row is what keeps
  # the declaration true — a new collision that nobody writes down is red, and so
  # is a declared code that has stopped colliding. `undeclared=` is judged in
  # both directions for that reason; a one-way check would let the list outlive
  # the thing it describes, which is the failure #1337 spent a day on.
  judge ExitGrammar  'VERDICT THE_GRAMMAR_HAS_ONE_HOME doc_drift=0 undeclared=0 literals=0 checked_none=0'
  # The second row ever to wear `vary`, and it is a decision rather than a
  # formality (#1328). `FIGURE_CENSUS … secs=` is wall-clock, so it moves
  # between two identical runs and clause 4 says a probe that does that fails
  # the sweep on the line that moved. The number exists because two of this
  # probe's markers run the daemon — 4,500 and 20,000 ticks — and #1302 put it
  # there so an expensive marker is visible where it is incurred rather than
  # only in the sweep's total.
  #
  # The alternative is deleting a measurement this tree decided yesterday it
  # wanted. Same shape as AllocMeter's exemption: the noise is printed BESIDE
  # the world's numbers rather than in place of them, and the verdict line is
  # fixed while the census moves.
  vary  'prints its own wall-clock: two of its markers run the daemon (4,500 and 20,000 ticks) and the second run of a pair is warmer, so secs= lands anywhere in 4-6 while checked= and docs= hold (#1302, #1328)' \
        --lines '^FIGURE_CENSUS ' --cut 1 \
        judge DocFigures   'VERDICT FIGURES_AGREE stale=0 refused=0 checked_none=0'
  judge BondBook     'VERDICT BOOK_TURNS_OVER'  "$TICKS"
  judge SameTick     'VERDICT SAME_TICK_ABSORB' "$TICKS"
  # The Room 303 clause's own row, and the one place its REFUSAL is reachable.
  # Its budget is written here rather than taken from $TICKS for the reason
  # #377 measured: at 6,000 ticks the clause fires and is never asked twice, so
  # a $TICKS row would judge half the ruling and report the other half as held.
  # 40,000 ticks under 60 deployed daemons is the scale at which a resurrected
  # mind dies a second time — 24 firings, 11 spent-edge refusals — and the row
  # costs the sweep about eight seconds.
  judge BondScenario 'VERDICT ONCE_PER_EDGE_HELD' 40000 42 60
  # The same clause's other half: not whether the edge pays twice, but what the
  # payment BUYS. Same budget as the row above and for the same reason — the
  # aftermath of two firings is an anecdote and the aftermath of eighteen is a
  # measurement. No scripted pressure, because the question is what the
  # canonical universe does on its own; costs the sweep about the same as the
  # row above, with no daemons to deploy. What is judged is that every firing
  # has a fate, not that the fates are kind: seed 42 reads `recaptured=16
  # rekilled=0 resaved=2 uncaught=0 median_delay=1`, and the verdict would read
  # the same if all eighteen had been rekilled.
  judge ClauseAftermath 'VERDICT AFTERMATH_ACCOUNTED' 40000 42
  # The one row that reads a committed file. Its budget is written here instead of
  # taken from $TICKS because probes/beatdrift.baseline is a measurement AT a
  # budget: a sweep at 2,000 ticks reaches two of the eight beats and reads -1 for
  # the other six, and comparing that against a 6,000-tick row would report the
  # argument as drift. The probe refuses the mismatch rather than judging it —
  # `CensusBeatDrift 42,7 2000 --baseline-file probes/beatdrift.baseline` leaves with
  # `Probes.Outcome.REFUSED` and a FATAL naming both budgets. The CONSTANT is quoted
  # here and the number is not, deliberately (#1533): this sentence said "exits 2"
  # and the probe has exited 3 since #1219 moved the refusal off NEVER_AROSE, and
  # nothing in the tree could have noticed — `ExitGrammar` pins the codes the probes
  # SPEND, `counters.sh` reads catalog rows against bench rows, and neither reads
  # shell prose for a bare digit. A name moves when the enum moves; a digit does not.
  # Band and denominator ride in the judged line, so widening the tolerance — or
  # reading a pin that names none of the beats — is an edit to this row and not a
  # quiet pass.
  judge CensusBeatDrift 'VERDICT DRIFT_WITHIN_BAND compared=16/16 band=200' \
        42,7 6000 --band 200 --baseline-file probes/beatdrift.baseline
  run   DrawMeter    "$TICKS"
  run   ChainDump    "$TICKS"
  run   LinkTrace    "Nadia Petrov" "$TICKS"
  run   NameCensus   42
  run   SheetDump    --all
  # The census against the prose that reports it. Its four numbers are the
  # only counts left in `probes/README.md` with a producer to run; the three
  # that had none were deleted rather than re-derived (#1192).
  judge SheetDump    'VERDICT SHEETDUMP_CATALOG_MATCHES checked=4 of=4' --catalog
  judge SheetDump    'VERDICT STOLEN_THROUGH_ONE_FENCE branches=1 unresolved=0' --stolen 42 4000
  judge SheetFence   'VERDICT ONE_DOOR_NO_CACHE stored=0 foreign_imports=0 cached=0 door_missing=0 swept_none=0 impure_adapters=0'
  judge SheetFence   'VERDICT CROSSINGS_STABLE checked=4 drifted=0' --crossings
  judge SystemFatigue 'VERDICT FATIGUE_READS_THE_COUNTER boot=6 reboot=7 v99=10 v0=1 derived_intact=true bypass_refused=true'
  # The reason text said the median "holds at 367", and 367 was wrong in two ways at once
  # (#1477). It is 351 on Temurin 17 today — sixteen bytes per tick stale in the figure the
  # exemption uses to argue everything else on the line is trustworthy — and `holds` was
  # doing work it had not earned: the median holds against repetition, warmup and the JIT,
  # and not against a JVM. Measured, three runs each, same class files, same seed:
  #
  #   Temurin 17.0.20  steady_bytes_per_tick=351 351 351   full_run_mb=5
  #   OpenJDK 25.0.1   steady_bytes_per_tick=290 290 290   full_run_mb=4
  #
  # So the reason now says which JVM it is a figure OF, the way SealHygiene's pinned hashes
  # name theirs. The exemption itself is unchanged and was never the problem: `steady_max`
  # genuinely is a cold uncompiled sample. What it must not do is vouch for a neighbour.
  vary  'prints its own instrument noise: steady_max is a cold uncompiled sample by construction and lands anywhere in 2.0-7.9 KB/tick (#817), and the steady median it sits beside is a per-JVM constant rather than a property of this code — 351 on Temurin 17.0.20, 290 on OpenJDK 25.0.1, three of three runs each, so it is stable to repeat and not to upgrade (#1477); the budget comparison in ALLOC_BUDGET is the honest reading of it' \
        --lines '^ALLOC(_NOTE|_BUDGET)? ' --cut 3 \
        judge AllocMeter 'VERDICT ALLOC_IN_BUDGET' 42
  judge AllocMeter   'SELFCHECK VERDICT GUARD_FIRES' --selfcheck
  # The referee's own referee, not the referee. `NeutralDiff <ticks>` needs
  # #528's sealed fixture, which is not in this tree, and a row that reads a
  # file the repository does not contain is a red sweep on every box. What is
  # judged here is the comparison itself, driven over hand-written chains with
  # no universe: the PASS branch is the only one a green tree ever reaches, so
  # the four failure verdicts are exercised here or nowhere. When the fixture
  # lands, this row gains a sibling that runs the lane for real.
  judge NeutralDiff  'SELFCHECK VERDICT REFEREE_HOLDS' --selfcheck
  run   SeedAtlas    1 5 "$TICKS"
}

PROBES=0 JUDGED=0 PASS=0 FAIL=0 RAN=0 UNEXERCISED=0
STABLE=0 DRIFTED=0 EXEMPT=0 UNCHECKED=0
VARIES=''
VARY_LINES=''
VARY_CUT=''
# Set by `known` around its call to `settle`, which otherwise reads a declared
# break's nonzero second run as an instrument that drifted (#1231).
KNOWN_ROW=no

# One row's run, printed. The three verbs differ only in what they demand of
# the output afterwards, so the invocation itself is written once: the class,
# its own args, stderr folded in, and the exit code kept rather than trusted.
#
# Every row's wall clock is accumulated HERE rather than at the three verbs,
# because a row that fails never reaches its verb's `PASS … secs=` line and its
# cost would vanish from the attribution — the sweep would report where the time
# went on a green day and lose the answer on the day somebody needs it (#1216).
ROW_OUT='' ROW_RC=0
COSTS=''
execute() {
  local cls="$1" t0; shift
  t0=$(date +%s)
  set +e
  ROW_OUT="$(java -cp out:probes/out "$cls" "$@" 2>&1)"
  ROW_RC=$?
  set -e
  COSTS="${COSTS}$(($(date +%s) - t0)) ${cls}
"
  printf '%s\n' "$ROW_OUT"
}

# The --list row, printed by both verbs from one place.
#
# varies= rides at the END, and only when set: every row that is not exempt
# prints the CONTRACT line it printed before this unit, byte for byte. A field
# inserted mid-line is a break; a field appended is evolution (D-020's law,
# applied to the bench's own output as well as the daemon's). The only value
# that moves is judged=, on the one row that gained a verdict.
contract() {
  local cls="$1" want="$2"; shift 2
  printf 'CONTRACT %s judged="%s" args="%s"' "$cls" "$want" "$*"
  if [ -n "$VARIES" ]; then
    printf ' varies="%s"' "$VARIES"
  fi
  printf '\n'
}

# A judged probe: run it, print it, and demand its exact line.
# A row whose probe is EXPECTED to fail, because the defect it names is open and filed.
#
# #1093 gave judged probes honest exit codes, and that made "expected broken" unsayable:
# `judge` fails a row whose probe exits nonzero, so pinning ConfirmationSweep's seed-7
# verdict — the D-021 clause that holds at seed 42 and nowhere else (#1155) — turned the
# whole sweep red for a break the tree already knows about. The two ways out of that are
# both worse than a third verb: delete the row and lose the only thing watching the
# defect, or drop the exit code and lose what #1093 bought.
#
# So a known break is DECLARED, with the issue that owns it in the row. The verdict is
# still matched exactly, and the nonzero exit is required rather than tolerated: a probe
# that starts PASSING here is as red as one that starts failing differently, which is what
# makes this a lock and not a mute button. When the issue lands, the row becomes a `judge`.
known() {                       # known <Class> '<verdict>' '<#issue>' [args...]
  local cls="$1" want="$2" issue="$3"; shift 3
  PROBES=$((PROBES + 1)); JUDGED=$((JUDGED + 1))
  if [ "$LIST" = yes ]; then
    contract "$cls" "$want (known break, $issue)" "$@"
    return 0
  fi
  local started; started=$(date +%s)
  printf 'PROBE %s args="%s" known_break="%s" issue=%s\n' "$cls" "$*" "$want" "$issue"
  execute "$cls" "$@"
  if [ "$ROW_RC" -eq 0 ]; then
    FAIL=$((FAIL + 1))
    echo "FAIL $cls exited 0 — the break $issue records is gone; make this a judge row"
    return 0
  fi
  if printf '%s\n' "$ROW_OUT" | grep -qxF "$want"; then
    PASS=$((PASS + 1))
    echo "PASS $cls known=$issue secs=$(($(date +%s) - started))"
  else
    FAIL=$((FAIL + 1))
    echo "FAIL $cls broke differently: wanted $want"
  fi
  # Set around the call rather than passed as an argument, because `settle` takes
  # the probe's own args as "$@" and cannot grow a parameter without every caller
  # changing. Same shape as `VARIES`, which the `vary` modifier sets the same way.
  KNOWN_ROW=yes
  settle "$cls" "$ROW_OUT" "$@"
  KNOWN_ROW=no
}

judge() {
  local cls="$1" want="$2"; shift 2
  PROBES=$((PROBES + 1)); JUDGED=$((JUDGED + 1))
  if [ "$LIST" = yes ]; then
    contract "$cls" "$want" "$@"
    return 0
  fi
  local started; started=$(date +%s)
  printf 'PROBE %s args="%s" judged="%s"\n' "$cls" "$*" "$want"
  execute "$cls" "$@"
  # Exit 2 is the third answer (#1138): the scenario never arose. Not a pass — a row that
  # reaches no miracle must not stand in for one that does — and not a failure, because
  # nothing broke. The row is UNEXERCISED and counted, which is #970's INSTRUMENTS_UNPROVEN
  # argument in the other axis: a green report about work that did not occur.
  if [ "$ROW_RC" -eq 2 ]; then
    UNEXERCISED=$((UNEXERCISED + 1))
    skipped "$cls"
    echo "UNEXERCISED $cls its scenario never arose in this run — nothing was judged"
    return 0
  fi
  if [ "$ROW_RC" -ne 0 ]; then
    FAIL=$((FAIL + 1))
    skipped "$cls"
    echo "FAIL $cls exited $ROW_RC"
    return 0
  fi
  if printf '%s\n' "$ROW_OUT" | grep -qxF "$want"; then
    PASS=$((PASS + 1))
    echo "PASS $cls secs=$(($(date +%s) - started))"
  else
    FAIL=$((FAIL + 1))
    echo "FAIL $cls missing verdict: $want"
  fi
  settle "$cls" "$ROW_OUT" "$@"
}

# A reporting probe: run it, print it, demand only that it survives.
# $VARIES, set by `vary` for the row it wraps, is the declared exemption from
# the determinism pass — empty for every other row.
run() {
  local cls="$1"; shift
  PROBES=$((PROBES + 1))
  if [ "$LIST" = yes ]; then
    contract "$cls" '-' "$@"
    return 0
  fi
  local started; started=$(date +%s)
  printf 'PROBE %s args="%s" judged="-"\n' "$cls" "$*"
  execute "$cls" "$@"
  if [ "$ROW_RC" -ne 0 ]; then
    FAIL=$((FAIL + 1))
    skipped "$cls"
    echo "FAIL $cls exited $ROW_RC"
    return 0
  fi
  RAN=$((RAN + 1))
  echo "RAN $cls secs=$(($(date +%s) - started))"
  settle "$cls" "$ROW_OUT" "$@"
}

# A row that is allowed to move: the reason, then the verb it applies to.
# Setting $VARIES around the call is the whole mechanism — `settle` reads it,
# `contract` prints it, and both verbs are unaware they were wrapped.
vary() {
  VARIES="$1"; shift
  # An optional `--lines <ERE>` narrows the exemption to the lines that actually move
  # (#1187). Optional rather than required, because a row that genuinely cannot say which
  # of its lines drift is still better off declaring the drift than hiding it — but a row
  # that CAN say should, and both of today's two can.
  VARY_LINES=''
  VARY_CUT=''
  if [ "${1:-}" = "--lines" ]; then
    VARY_LINES="$2"; shift 2
  fi
  # `--cut <N>`: how many lines this exemption expects to remove. A pattern is a
  # snapshot of what the probe printed the day the row was written, and when the
  # probe grows a line family the pattern stops covering it — silently, because
  # the pass compares what is LEFT and a family it never removed simply gets
  # judged. `AllocMeter` sat that way from #1187 until #1245: two families named,
  # three printed, and the third carrying the same JIT noise the row was exempted
  # for. The count was on the EXEMPT line the whole time and nothing compared it.
  #
  # A floor rather than an equality: a probe that grows a noisy line inside an
  # already-exempt family is not a defect, and demanding the exact number would
  # turn every such growth into a red row and an edit-to-green (#884's lesson).
  # Cutting FEWER than declared is the direction that means the pattern stopped
  # reaching, and that is the one this refuses.
  if [ "${1:-}" = "--cut" ]; then
    VARY_CUT="$2"; shift 2
  fi
  "$@"
  VARIES=''
  VARY_LINES=''
  VARY_CUT=''
}

# The determinism pass (#364). The probes referee the daemon; nothing referees
# the probes. The digest leash proves the WORLD is deterministic and says
# nothing about the instruments pointed at it — and an instrument that drifts
# is worse than no instrument, because it manufactures mysteries the world
# never had and the next skeptic spends a round chasing a phantom that lives in
# the coroner, not the corpse.
#
# So: the same class, the same args, the same budget, a second time, and the
# two outputs compared byte for byte. The second run's output is deliberately
# NOT reprinted — the diff is the fact, and doubling the log to say "identical"
# helps nobody. Cost is one extra run per row inside a sweep already paid for.
#
# One thing about the second run is deliberately NOT the same: it is taken
# under LC_ALL=C. Contract clause 4 has always said a probe that reaches for a
# default locale fails this sweep on the line that moved, and until #836 that
# sentence was false — both runs stood in the same shell, so the one property
# a locale can break was the one property this pass could not see. DrawMeter's
# freeze line and DistrictNeutral's catalog separators were arriving as '?' on
# any box with no locale exported, and every row here was STABLE. The hostile
# locale costs nothing (the probes are pinned to UTF-8 by matrix.Streams.utf8)
# and makes the byte compare mean what the clause says it means.
# A row whose probe died on its first run never reaches `settle`, so it was
# never asked the determinism question at all (#970). It used to be counted in
# `probes` and in nothing else, which broke the identity the line below rests on
# and let INSTRUMENTS_STABLE print for a sweep that skipped an instrument — the
# referee filing a clean sheet for a match it did not watch, which is the exact
# failure #364 exists to prevent, one level up. Counted here rather than
# inferred from the shortfall, because a number that says how many is worth more
# than a verdict that says something is missing.
skipped() {
  [ "$TWICE" = yes ] || return 0
  UNCHECKED=$((UNCHECKED + 1))
  echo "UNCHECKED $1 — died on its first run, so it was never run twice"
}

settle() {
  [ "$TWICE" = yes ] || return 0
  local cls="$1" first="$2"; shift 2
  # Narrowed to a named set when --twice-changed supplied one. Skipped rows are counted
  # as UNCHECKED rather than STABLE — the identity `probes = stable + drift + exempt +
  # unchecked` is #970's, and it exists precisely so a pass over a subset cannot report
  # itself as a pass over the whole (#1185).
  if [ -n "$TWICE_ONLY" ] && ! printf ',%s,' "$TWICE_ONLY" | grep -qF ",$cls,"; then
    UNCHECKED=$((UNCHECKED + 1))
    return 0
  fi
  # An exemption is per-LINE when the row names a pattern, and per-ROW only when it does
  # not (#1187). `vary` was line-blind: it saw VARIES set, printed EXEMPT and returned, so
  # a row whose VERDICT started flickering would have been exempted along with the noise it
  # was exempted for. Both rows that wear the modifier name the line in their own reason
  # — "the QUIET line carries…", "steady_max is a cold uncompiled sample" — and the
  # mechanism ignored what the row said.
  #
  # With a pattern, the two runs are compared with the matching lines removed, and the
  # count of removed lines is on the row: `EXEMPT lines=1` and `EXEMPT lines=12` are the
  # difference between an instrument with a noisy field and an instrument that is mostly
  # noise. Without one, the old behaviour, so an unpatterned `vary` still works and still
  # says nothing.
  if [ -n "$VARIES" ]; then
    EXEMPT=$((EXEMPT + 1))
    if [ -z "$VARY_LINES" ]; then
      printf 'EXEMPT %s whole-row reason="%s"\n' "$cls" "$VARIES"
      return 0
    fi
    local again2 rc2 cut_a cut_b n_cut t0
    t0=$(date +%s)
    set +e
    again2="$(LC_ALL=C java -cp out:probes/out "$cls" "$@" 2>&1)"
    rc2=$?
    set -e
    COSTS="${COSTS}$(($(date +%s) - t0)) ${cls}
"
    if [ "$rc2" -ne 0 ] && [ "$rc2" -ne 1 ]; then
      DRIFTED=$((DRIFTED + 1)); EXEMPT=$((EXEMPT - 1))
      echo "DRIFT $cls second run exited $rc2"
      return 0
    fi
    n_cut="$(printf '%s\n' "$first" | grep -cE "$VARY_LINES" || true)"
    cut_a="$(printf '%s\n' "$first"  | grep -vE "$VARY_LINES" || true)"
    cut_b="$(printf '%s\n' "$again2" | grep -vE "$VARY_LINES" || true)"
    if [ -n "$VARY_CUT" ] && [ "$n_cut" -lt "$VARY_CUT" ]; then
      DRIFTED=$((DRIFTED + 1)); EXEMPT=$((EXEMPT - 1))
      echo "DRIFT $cls exemption cut $n_cut lines and declares $VARY_CUT — the pattern stopped reaching its subject"
      return 0
    fi
    if [ "$cut_a" = "$cut_b" ]; then
      # THE THIRD RUN REACHES THE EXEMPT BRANCH TOO (#1567). #1355 gave it to the
      # plain branch and stopped, and that left the narrowing's strongest
      # protection off the rows with the strongest prior: a probe wearing `vary`
      # is one KNOWN to have a field that moves on a clock, which is evidence it
      # is the population most likely to grow a second one. `DocFigures` is the
      # probe #1329 was opened for and it is exempt.
      #
      # The cut-and-compare is applied to the new pair, not to a merged three-way
      # read: `cut_a` is the reference and each later run is judged against it, so
      # a drift on run three reports the same way a drift on run two does.
      #
      # VARY_CUT'S RULE IS DECIDED HERE RATHER THAN COPIED. It asks whether the
      # exemption pattern still reaches its subject, and it asked that of the
      # FIRST run's line count. A third run that cuts fewer lines is the same
      # finding — the pattern stopped reaching — so it is judged the same way and
      # the message says which run.
      if [ -n "$TWICE_ONLY" ]; then
        local again3 rc3 cut_c n_cut3 t3
        t3=$(date +%s)
        set +e
        again3="$(LC_ALL=C java -cp out:probes/out "$cls" "$@" 2>&1)"
        rc3=$?
        set -e
        COSTS="${COSTS}$(($(date +%s) - t3)) ${cls}
"
        if [ "$rc3" -ne 0 ] && [ "$rc3" -ne 1 ]; then
          DRIFTED=$((DRIFTED + 1)); EXEMPT=$((EXEMPT - 1))
          echo "DRIFT $cls third run exited $rc3"
          return 0
        fi
        n_cut3="$(printf '%s\n' "$again3" | grep -cE "$VARY_LINES" || true)"
        cut_c="$(printf '%s\n' "$again3" | grep -vE "$VARY_LINES" || true)"
        if [ -n "$VARY_CUT" ] && [ "$n_cut3" -lt "$VARY_CUT" ]; then
          DRIFTED=$((DRIFTED + 1)); EXEMPT=$((EXEMPT - 1))
          echo "DRIFT $cls exemption cut $n_cut3 lines on its third run and declares $VARY_CUT — the pattern stopped reaching its subject"
          return 0
        fi
        if [ "$cut_a" != "$cut_c" ]; then
          DRIFTED=$((DRIFTED + 1)); EXEMPT=$((EXEMPT - 1))
          echo "DRIFT $cls outside its declared exemption on its third run — the verdict moved, not the noise"
          diff <(printf '%s\n' "$cut_a") <(printf '%s\n' "$cut_c") | head -4 | sed 's/^/  /'
          return 0
        fi
      fi
      printf 'EXEMPT %s lines=%s declared=%s runs=%s reason="%s"\n' \
        "$cls" "$n_cut" "${VARY_CUT:-none}" "$([ -n "$TWICE_ONLY" ] && echo 3 || echo 2)" "$VARIES"
    else
      DRIFTED=$((DRIFTED + 1)); EXEMPT=$((EXEMPT - 1))
      echo "DRIFT $cls outside its declared exemption — the verdict moved, not the noise"
      diff <(printf '%s\n' "$cut_a") <(printf '%s\n' "$cut_b") | head -4 | sed 's/^/  /'
    fi
    return 0
  fi
  local again rc t0
  t0=$(date +%s)
  set +e
  again="$(LC_ALL=C java -cp out:probes/out "$cls" "$@" 2>&1)"
  rc=$?
  set -e
  # The second run is the sweep's time too, and under `--twice` it is half of
  # it. Attribution that counted only judged runs would name the wrong row on
  # the one invocation whose cost anybody is arguing about.
  COSTS="${COSTS}$(($(date +%s) - t0)) ${cls}
"
  # A `known` row's probe is SUPPOSED to exit nonzero, twice. The determinism
  # pass did not know that: it read the second run's code, saw 1, and reported
  # `FAIL KnownFixture second run exited 1` — a declared break counted as an
  # instrument that drifted (#1231's own CI run found this, which is the whole
  # argument for exercising the verb).
  #
  # The expectation inverts with the row rather than being waived: a known row
  # whose probe exits 0 on the SECOND run has healed between two runs of the
  # same tree, and that is a drift worth a red line — the same reasoning the
  # verb itself uses when it refuses a probe that starts passing.
  if [ "${KNOWN_ROW:-no}" = yes ]; then
    if [ "$rc" -eq 0 ]; then
      FAIL=$((FAIL + 1)); DRIFTED=$((DRIFTED + 1))
      echo "FAIL $cls second run exited 0 — a declared break healed between two runs"
      return 0
    fi
  elif [ "$rc" -ne 0 ]; then
    # Ran, then did not: a failure of the bench AND a difference between the
    # two runs. Both counters move, because both statements are true.
    FAIL=$((FAIL + 1)); DRIFTED=$((DRIFTED + 1))
    echo "FAIL $cls second run exited $rc"
    return 0
  fi
  if [ "$again" = "$first" ]; then
    # A THIRD RUN WHEN THE PASS IS NARROWED (#1355). #1302 put a wall-clock
    # `secs=` on DocFigures, the lane ran `--twice-changed DocFigures`, and it
    # PASSED — then the weekly full pass found `secs=5` against `secs=4` six days
    # later. The narrowing selected the right probe and the comparison was right;
    # one comparison is simply not enough for a field whose drift is
    # PROBABILISTIC. Wall clock at one-second resolution is bimodal: two runs
    # 400 ms apart print the same integer most of the time.
    #
    # A third run is what #1355 ranks first and calls cheap, and the cost is
    # bounded by the narrowing itself — the set is the probes one pull request
    # touched, usually one or two, never the whole bench. `--twice` over
    # everything is untouched, because there the extra run is fifty of them and
    # the weekly pass is where the whole-tree question belongs.
    #
    # It turns a coin flip into a better one and does NOT make the answer
    # certain. That is stated here rather than left to be inferred from a green
    # row: the deterministic guarantee this pass gives is about bytes, and a
    # field that moves on a clock has no guarantee to give.
    if [ -n "$TWICE_ONLY" ]; then
      local third rc3 t3
      t3=$(date +%s)
      set +e
      third="$(LC_ALL=C java -cp out:probes/out "$cls" "$@" 2>&1)"
      rc3=$?
      set -e
      COSTS="${COSTS}$(($(date +%s) - t3)) ${cls}
"
      if [ "${KNOWN_ROW:-no}" = yes ] && [ "$rc3" -eq 0 ]; then
        FAIL=$((FAIL + 1)); DRIFTED=$((DRIFTED + 1))
        echo "FAIL $cls third run exited 0 — a declared break healed on the third run"
        return 0
      fi
      if [ "${KNOWN_ROW:-no}" != yes ] && [ "$rc3" -ne 0 ]; then
        FAIL=$((FAIL + 1)); DRIFTED=$((DRIFTED + 1))
        echo "FAIL $cls third run exited $rc3"
        return 0
      fi
      if [ "$third" != "$first" ]; then
        DRIFTED=$((DRIFTED + 1))
        moved "$cls" "$first" "$third"
        return 0
      fi
    fi
    # `runs=` on the row, because a reader cannot otherwise tell a narrowed STABLE
    # (three runs) from a whole-sweep STABLE (two) — and the difference is exactly
    # how much the word is worth. Nothing greps this line; the lane reads the
    # determinism verdict, which is untouched.
    STABLE=$((STABLE + 1))
    echo "STABLE $cls runs=$([ -n "$TWICE_ONLY" ] && echo 3 || echo 2)"
    return 0
  fi
  DRIFTED=$((DRIFTED + 1))
  moved "$cls" "$first" "$again"
}

# The first line that moved, with its number, both sides quoted so a trailing
# space or a vanished line is visible. Pure shell reads: the two outputs are
# already in memory, and spawning a process per line would cost more than the
# rerun that produced them. (A difference in trailing blank lines alone is
# invisible here — command substitution strips those before the compare.)
moved() {
  local cls="$1" n=0 a b ea eb
  exec 3<<<"$2"
  exec 4<<<"$3"
  while :; do
    a=''; b=''; ea=0; eb=0
    if ! IFS= read -r a <&3; then ea=1; fi
    if ! IFS= read -r b <&4; then eb=1; fi
    if [ "$ea" -eq 1 ] && [ "$eb" -eq 1 ]; then break; fi
    n=$((n + 1))
    if [ "$a" != "$b" ]; then
      printf 'DRIFT %s line=%d a="%s" b="%s"\n' "$cls" "$n" "$a" "$b"
      break
    fi
  done
  exec 3<&- 4<&-
}

# Clause 5 of the probe contract, instrumented (#818):
#
#   Outside the build. Nothing under probes/ is compiled into the daemon.
#   src/ must build and --selftest must pass with this directory deleted.
#
# Until now that clause was enforced by nobody having broken it. CI compiles
# src/, then compiles probes/ against it, and never once asks whether the first
# step still stands when the second directory is not there.
#
# The instrument is the clause read literally: take a copy of the tree, delete
# probes/ from the COPY, build src/ and selftest there. Two properties matter
# more than the three commands. First, the copy comes from `git archive HEAD`,
# so the check judges a pinned tree (clause 6) and never the working one —
# deleting probes/ in place to test whether probes/ is needed is how you lose
# an afternoon's uncommitted work to a lock. Second, everything happens under
# mktemp: clause 1 forbids the bench from mutating the tree it is pointed at,
# and a check that breaks the contract it is checking is not a check.
#
# A tree with no .git — the pinned form the README prescribes for evidence
# runs, which is an extracted tarball — cannot be archived. Rather than fail
# there, the copy falls back to the worktree and says so on its own line, so
# the reader always knows which tree reached the verdict.
clause5() {
  local work tmp from sha started rc log builds self verdict
  started=$(date +%s)
  tmp="${TMPDIR:-/tmp}"
  work="$(mktemp -d "${tmp%/}/bench-clause5.XXXXXX")"
  if git rev-parse --verify HEAD >/dev/null 2>&1; then
    from=archive
    sha="$(git rev-parse HEAD)"
    git archive HEAD | tar -x -C "$work"
  else
    from=worktree
    sha='-'
    tar -cf - --exclude './probes' --exclude './probes/*' \
              --exclude './out' --exclude './.git' --exclude './.git/*' . \
      | tar -xf - -C "$work"
  fi
  rm -rf "$work/probes"

  printf 'CLAUSE5 source=%s sha=%s probes_present=%s ticks=%s tree=%s\n' \
    "$from" "$sha" "$([ -e "$work/probes" ] && echo yes || echo no)" \
    "$TICKS" "$work"

  builds=no
  self='-'
  log="$work/clause5.log"
  set +e
  ( cd "$work" && javac -encoding UTF-8 --release 17 -d out $(find src -name '*.java') ) >"$log" 2>&1
  rc=$?
  set -e
  if [ "$rc" -eq 0 ]; then
    builds=yes
    set +e
    ( cd "$work" && java -cp out matrix.Main --selftest --ticks "$TICKS" ) >>"$log" 2>&1
    rc=$?
    set -e
    # Exit code AND the greppable line, the same pair CI's lock 2 trusts: a
    # selftest that exits 0 without printing its verdict has not passed.
    if [ "$rc" -eq 0 ] && grep -q '^SELFTEST OK ' "$log"; then self=OK; else self=FAIL; fi
  fi

  if [ "$builds" = yes ] && [ "$self" = OK ]; then
    verdict=BENCH_STANDS_ALONE
  else
    verdict=BENCH_ENTANGLED
    echo "--- the deleted-probes tree said: ---"
    tail -20 "$log"
    echo "--- tree kept for reading: $work ---"
  fi
  printf 'CLAUSE5 secs=%s\n' "$(($(date +%s) - started))"
  printf 'BENCH clause5 src_builds=%s selftest=%s VERDICT %s\n' "$builds" "$self" "$verdict"
  [ "$verdict" = BENCH_STANDS_ALONE ] || return 1
  rm -rf "$work"
}
# Every probe in the directory is in the table, or the table is not the list it claims
# to be (#1162). `BoundsCensus` landed on main with no row: its PR body quoted the row,
# its evidence block quoted the verdict, and the commit touched one file. Three ordinary
# things lined up — a substitution anchored on a line that did not exist yet, a tool that
# reports success when it changes nothing, and an author who verified the probe instead of
# the row — and the tree gained an instrument that guards nothing.
#
# This file's own header says a probe CI does not run is a probe that guards nothing
# between two people remembering it. That sentence needed a reader.
#
# `Probes` is the shared accessor class and has no main; the rest of the exemptions are
# named individually rather than pattern-matched, because a pattern is where the next
# unrun probe hides. Reported and judged on every run, --list included: the check costs
# one `ls` and it is the cheapest lock in the file.
roster_check() {
  local missing=0 cls
  for f in probes/*.java; do
    cls="$(basename "$f" .java)"
    # No main, no row: `Probes` holds the reflective accessors and `LineGrammar` holds
    # D-020's families as data, and neither is a thing you can run. Asked of the file
    # rather than listed by name, because a name list is where the next unrun probe hides.
    grep -q 'static void main(String\[\] args)' "$f" || continue
    grep -q "^\s*\(judge\|run\|known\|vary\).*\b$cls\b" probes/bench.sh && continue
    # A probe may legitimately have no row: six do, and they are one-off instruments
    # written to answer a question once — a census re-verdict, a sample-size argument,
    # a draw-order keeper. Forcing them into the lane would buy nothing and cost wall
    # clock on every push. What is refused is the SILENT case, which is what happened
    # to BoundsCensus: the probe says so in its own javadoc, or it is untabled.
    grep -qi 'one-off\|not in the bench\|run by hand\|investigation' "$f" && continue
    missing=$((missing + 1))
    echo "UNTABLED $cls has no row and does not say it is one-off — it runs on nobody's push"
  done
  # The second absence, and it is a different one (#1177). A probe with no BENCH row runs
  # on nobody's push, which a green sweep tolerates. A probe with no CATALOG row cannot be
  # found at all: probes/README.md is the only document that says what an instrument is
  # FOR, and this file's own header calls it the catalog. Five probes had a main, a bench
  # row and no catalog row — four of them landed the same day, and OrderTable had been
  # there since #1013.
  #
  # Counted separately rather than folded into `untabled`: one is a lock gap and one is a
  # documentation gap, and conflating them hides the cheaper one behind the louder.
  local uncatalogued=0
  for f in probes/*.java; do
    cls="$(basename "$f" .java)"
    grep -q 'static void main(String\[\] args)' "$f" || continue
    grep -q "\`$cls\`" probes/README.md && continue
    uncatalogued=$((uncatalogued + 1))
    echo "UNCATALOGUED $cls has no row in probes/README.md — nobody can find out what it is for"
  done
  echo "ROSTER probes_on_disk=$(ls probes/*.java | wc -l | tr -d ' ')" \
       "untabled=$missing uncatalogued=$uncatalogued"
  [ "$missing" -eq 0 ] && [ "$uncatalogued" -eq 0 ]
}


if [ "$LIST" = yes ]; then
  table
  echo "CONTRACT probes=$PROBES judged=$JUDGED ticks=$TICKS"
  # The roster runs here too, and this is the form an author actually types while
  # adding a row (#1162): --list is the cheap read, so the check that a probe HAS a
  # row belongs where the author will meet it, not only in the two-minute sweep.
  roster_check
  exit $?
fi

# The clause-5 check is its own errand: it builds a different tree from the one
# the sweep builds, and it runs no probe. It answers alone.
if [ "$WITHOUT" = yes ]; then
  if clause5; then exit 0; else exit 1; fi
fi

SWEEP_START=$(date +%s)
if [ "$BUILD" = yes ]; then
  # The bench compiles against the daemon it dissects, from nothing, with the
  # same two lines the workflow and the release script use. Probes stay outside
  # the build (contract clause 5): src/ never sees probes/.
  javac -encoding UTF-8 --release 17 -d out $(find src -name '*.java')
  javac -encoding UTF-8 --release 17 -cp out -d probes/out probes/*.java
  echo "BUILD daemon+bench ok secs=$(($(date +%s) - SWEEP_START))"
fi

table
roster_check || FAIL=$((FAIL + 1))

# The lane's own budget (#1115). `--bench` judges the DAEMON against D-027's
# table; the sweep that runs it had no ceiling of its own, so every unit adding
# an instrument made the lane longer for every unit after it — 35 probes in 86 s
# early one night and 39 in 64 s later, the count up and the wall time down,
# with nothing recording whether that was an improvement or a quieter laptop.
#
# Three things this number is deliberately NOT. It is not a per-probe budget:
# a probe that honestly takes twenty seconds is fine and the sweep's own
# LedgerMirror row costs fifteen. It is not tight: wall clock on shared hardware
# moves 20% between runs and a bound that reddens on load teaches people to
# ignore it. And it is not a gate on this script's exit code — OVER prints and
# does not fail, because the failure being prevented is a lane growing tenfold
# over a season, which is a thing a human reads, not a thing a runner refuses.
#
# 300 s against a measured 147 s on the box this was written on: roughly double
# today's cost, so the lane can take on another dozen probes before anyone has
# to think about it, and it cannot silently reach ten minutes.
BENCH_BUDGET_SECS=${BENCH_BUDGET_SECS:-300}
BENCH_SECS=$(($(date +%s) - SWEEP_START))

# WHERE THE TIME WENT (#1216). The trailer below carried one number for the
# whole sweep and a ceiling to compare it against, and when the two approached
# each other the only way to learn WHICH row had grown was to re-run and read
# 1,400 lines — the work the trailer exists to save. Worse: a row that quietly
# doubles is invisible until the total crosses the budget, and then the sweep
# goes red on the next unit, which had nothing to do with it.
#
# ON ITS OWN LINE AND NOT ON THE VERDICT (#1221). #1216 asked for this in the
# trailer, and the census rule says a number whose CHANGE IS NOT A FINDING may
# not ride a judged line. Wall clock is that number exactly: it moves between
# two runs of the same tree, and `PROBE SWEEP FLOOR` greps the trailer. One line
# above the trailer is adjacent enough to answer the question a reader is
# actually asking — it is not in the scrollback, which is what #1216 objected
# to — and it keeps a timing off a line CI judges.
#
# PER CLASS, summed, rather than per row: `SheetFence` runs twice with different
# flags and `probes=67 judged=60` says rows outnumber classes. The question
# being asked is "what is this probe costing the sweep", and two rows of one
# class are one answer to it. `--twice` folds the second run in here too, since
# on that invocation the second run IS half the cost and attribution that
# ignored it would name the wrong row on the one day anybody argues about the
# budget.
#
# Ties break by name so two runs of the same tree print the same line: awk's
# `for (k in t)` has no defined order, and a census that reshuffles itself is a
# diff nobody can read.
# ONE ORDERING, ONE CUT, READ THREE TIMES (#1506). `slowest=`, `top3=` and `tail=` used to
# come from two pipelines whose first two stages were identical — sum by class, sort
# descending, ties by name — and whose cut was the literal `3` in two places: `head -3`
# decided which names were printed and `NR <= 3` decided which rows were summed.
#
# Nothing coupled them, and nothing would have noticed. `top3 + tail` equals the row total
# whatever the split, so the identity the trailer advertises would have held while its two
# halves described different sets — which is worse than a broken identity, because the check
# that exists passes.
#
# Two copies of one list is #789's sweep, #880's workflow, #1162's bench table and #1053's
# two readers of one grammar. This is the same shape at its smallest: two copies of one
# ORDER.
#
# WHY THE SPLIT EXISTS AT ALL (#1353): `slowest=` names the three fattest rows and they are
# the same three every week while the total climbs — 123 -> 178 in two units, 104 -> 117 ->
# 166 in a single day — and none of those was one fat row. They were rows landing, each
# cheap, each justified alone. `top3 + tail` is the sweep's ROW time, so a climb lands in one
# of two places and the line says which.
#
# NEITHER IS JUDGED, and the reason is measured. One unchanged tree, three consecutive runs,
# 2026-08-17:
#
#   top3=46 tail=85 secs=137
#   top3=44 tail=85 secs=134
#   top3=41 tail=86 secs=132
#
# `tail` moves by 1 second and `top3` by 5, which is the argument FOR the split: the noise
# lives in the fat rows and the growth lands in the steadier number. The runner's own spread
# is far wider (296..710 s over 54 runs, in locks.yml's hang-guard comment), so a gate on
# either would be a red build caused by load. `top3 + tail` is the row time and not the
# sweep's — 131 of 137 above; the remainder is the build, the roster and this trailer.
#
# CHANGING TOP_N CHANGES A FIELD NAME. `top3=` carries the number in its spelling, so the
# cut is not free to move: an instrument line is a byte contract (D-020) and a `top3=` that
# summed four rows would be the exact lie this unit removed, wearing a different mask.
TOP_N=3

# The row time per class, heaviest first, ties by name. One producer; the ordering is a fact
# about this run and not three functions' private opinion of it.
costs_ranked() {
  printf '%s' "$COSTS" \
    | awk 'NF == 2 { t[$2] += $1 } END { for (k in t) printf "%d %s\n", t[k], k }' \
    | sort -k1,1rn -k2,2
}

# The $TOP_N heaviest, named with their seconds.
cost_line() {
  costs_ranked | head -"$TOP_N" \
    | awk '{ printf "%s%s:%s", (NR > 1 ? "," : ""), $2, $1 } END { if (NR == 0) printf "none" }'
}

# Seconds inside the $TOP_N, or outside them. Same ordering, same cut, one argument.
cost_split() {                    # cost_split top|tail
  costs_ranked \
    | awk -v want="$1" -v n="$TOP_N" \
        'NR <= n { top += $1; next } { tail += $1 } END { printf "%d", (want == "top" ? top : tail) }'
}
echo "BENCH_COST slowest=$(cost_line)" \
     "top3=$(cost_split top)" \
     "tail=$(cost_split tail)" \
     "rows_timed=$(printf '%s' "$COSTS" | grep -c . || true)" \
     "classes_timed=$(printf '%s' "$COSTS" | awk '{print $2}' | sort -u | grep -c . || true)" \
     " (wall clock, so it moves between two runs of one tree — a census and never a verdict, #1221;" \
     "top3 + tail is the whole sweep's row time, so a climb lands in one of two places and this line" \
     "says which — the three fattest names are stable and the tail is where rows land (#1353);" \
     "classes_timed counts CLASSES WITH A ROW and sits four lines under probes_on_disk, which counts" \
     ".java files — the difference is the helpers no row invokes, and #1368 is about a reader having" \
     "to know that to reconcile them)"

echo "BENCH probes=$PROBES judged=$JUDGED pass=$PASS fail=$FAIL unexercised=$UNEXERCISED ran=$RAN" \
     "ticks=$TICKS secs=$BENCH_SECS budget=$BENCH_BUDGET_SECS" \
     "$([ "$BENCH_SECS" -le "$BENCH_BUDGET_SECS" ] && echo WITHIN || echo OVER)" \
     "VERDICT $([ "$FAIL" -eq 0 ] && echo BENCH_GREEN || echo BENCH_RED)"

# Two verdicts, because they are two facts: the world can be perfectly
# deterministic while its instruments are not, and the sweep that noticed the
# second must not report it as the first.
# probes = stable + drift + exempt + unchecked, and the identity is the point:
# a row that died before the second run is in none of the first three, so
# without the fourth the counters silently failed to close and the verdict was
# computed from DRIFTED alone (#970). The field is appended rather than
# inserted, because appending is evolution and inserting is a break (D-020).
# Drift outranks unchecked: a moved instrument is a finding, an unrun one is
# only an absence of findings, and the verdict names the worse of the two.
#
# A FOURTH WORD, because two different situations were printing the third one
# (#1247). `--twice-changed` exists to narrow the pass to what a pull request
# touched (#1185), so a large `unchecked` is what a correct narrowed run looks
# like — and every correct narrowed run was printing INSTRUMENTS_UNPROVEN.
#
# That word is #970's, and its argument is that a green report about work that
# did not occur is worse than a red one. Spending it on the ordinary case wears
# it out: the same mechanism as a lane number people learn to edit (#884),
# applied to a verdict people learn to ignore. The lane greps `drift=0` rather
# than the verdict, which is why nothing broke — and a verdict nothing reads is
# the state this bench keeps finding in other people's scripts.
#
# The two states are genuinely distinct and the denominator #970 insisted on
# stays on both: a pass that was SUPPOSED to cover everything and did not is
# UNPROVEN; a pass narrowed on purpose is NARROWED, and says what it covered.
if [ "$TWICE" = yes ]; then
  echo "BENCH determinism probes=$PROBES stable=$STABLE drift=$DRIFTED exempt=$EXEMPT unchecked=$UNCHECKED" \
       "VERDICT $(if [ "$DRIFTED" -ne 0 ]; then echo INSTRUMENTS_DRIFTED
                  elif [ "$UNCHECKED" -eq 0 ]; then echo INSTRUMENTS_STABLE
                  elif [ -n "$TWICE_ONLY" ]; then echo INSTRUMENTS_NARROWED
                  else echo INSTRUMENTS_UNPROVEN; fi)"
fi


[ "$FAIL" -eq 0 ] && [ "$DRIFTED" -eq 0 ]
