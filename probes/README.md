# probes/ — the skeptic's bench, in permanent form

Four adversarial passes produced a drawer of throwaway diagnostic programs; each ran
once and died with its scratchpad. This directory is where they live instead. A probe
is not a test and not a feature: it is an instrument you point at a running universe
when one of the three D-020 instruments shows a symptom the others cannot explain.

## The probe contract

1. **Read-only.** A probe never mutates the repo tree, never writes files, and never
   calls anything on a shared `Simulation` that could queue a `WorldEvent`. It prints.
2. **Own universe.** A probe constructs and ticks its **own private** `Simulation`
   (quiet sink, explicit seed). Determinism does the rest: the universe it dissects is
   byte-identical to the one that showed the symptom.
3. **Reflection allowed.** Probes may open private fields. The daemon's encapsulation
   protects the domain from the domain — not the coroner from the corpse.
4. **Greppable verdicts.** Evidence lines are stable, prefixed, one-per-fact
   (`DUP …`, `t=… link#… …`), so a probe's output can be diffed across runs and quoted
   in a PR without prose. "Diffed across runs" is a promise the bench keeps rather
   than assumes: `probes/bench.sh --twice` runs every probe a second time and
   byte-compares, and a probe that reaches for wall-clock, a default locale, a heap
   address or an unordered iteration fails the sweep on the line that moved. The
   second run is taken under `LC_ALL=C`, because the locale clause was the one this
   pass could not see while both runs stood in the same shell (#836).
5. **Outside the build.** Nothing under `probes/` is compiled into the daemon.
   `src/` must build and `--selftest` must pass with this directory deleted. "With this
   directory deleted" is a promise the bench keeps rather than assumes:
   `probes/bench.sh --without-probes` extracts `git archive HEAD` into a throwaway tree,
   removes `probes/` from **that** copy, builds `src/` and selftests there, and prints
   `VERDICT BENCH_STANDS_ALONE` or `VERDICT BENCH_ENTANGLED`. It never deletes anything
   in the tree you are standing in — clause 1 binds the check as hard as it binds a probe.
6. **Pinned to a SHA.** A probe run that is *evidence* — anything quoted in a PR,
   a verdict, an ADR errata or a skeptic round — is taken from a `git archive <sha>`
   copy, never from a shared working tree that may move under it. This is Ag9's
   closing sentence and clause (3) of D-030's errata, which records why: it was
   adopted the day a tree moved mid-verification, and the round survived only
   because the skeptic had pinned. `tools/` rides the same rule.
7. **Owns its bytes.** A probe's first statement is `matrix.Streams.utf8()`. D-020's
   line grammar is a byte contract, and a JVM takes its charset from the environment:
   with no locale exported, JDK 17 resolves it to `ANSI_X3.4-1968` and every em dash
   the probe printed becomes `?`. The pin is scanned for in CI, so a probe that
   forgets it fails the lane rather than the next reader.

## Building and running

**Pinned — the form for anything you will quote.** The SHA goes in the PR next to
the output, so a reader can reproduce the exact universe you saw:

```sh
SHA=$(git rev-parse HEAD)                     # or a tag: $(git rev-parse v3.0.0^{commit})
WORK=$(mktemp -d)
git archive "$SHA" | tar -x -C "$WORK"
cd "$WORK"
javac -encoding UTF-8 --release 17 -d out $(find src -name '*.java')
javac -encoding UTF-8 --release 17 -cp out -d probes/out probes/*.java
java -cp out:probes/out <Probe> [args]
```

Building inside the pinned copy also keeps clause 1 honest: `probes/out` is written
in the throwaway tree, not in the tree you are working in.

**Unpinned — casual local poking only.** From the repo root, daemon already built
to `out/`, and with the understanding that the tree can move under you between the
compile and the run:

```sh
javac -encoding UTF-8 --release 17 -cp out -d probes/out probes/*.java
java -cp out:probes/out <Probe> [args]
```

Compile the whole directory, not one file: seventeen of the thirty-seven probes call
the shared `Probes` reflection helper, and `javac … probes/<Probe>.java` alone fails
on them with `cannot find symbol: variable Probes`. Both numbers are hand-counted,
and both had drifted by the time #995 read them — the sentence said seven of twelve
against a directory that already held seventeen of thirty-six — so they now carry
the commands that produce them rather than asking to be believed:

```sh
ls probes/*.java | wc -l                            # 39 files
grep -al 'static void main' probes/*.java | wc -l   # 37 probes; the other two are
                                                    # the Probes and LineGrammar helpers
grep -al 'Probes\.'         probes/*.java | wc -l   # 17 of them call the helper
```

`grep -a` is kept out of habit rather than necessity now. `SheetDump.java` used to
carry a raw NUL byte inside a string literal, which made a BSD grep call the whole
file binary and undercount both figures by one — and, more expensively, made lock
8's charset guard skip that file entirely (#1039). The byte is an escape now, so a
bare `grep` agrees; the flag stays because the next probe to need a control
character should not silently change what these commands count.

## The sweep

One command runs every probe and prints one verdict:

```sh
probes/bench.sh            # 6,000 ticks each, compile included
probes/bench.sh --list     # the contract table, run nothing
probes/bench.sh --twice    # the sweep, plus the determinism pass below
probes/bench.sh --without-probes   # clause 5: does src/ still stand alone?
```

The contract table lives in that script, one row per invocation — `judge
<Class> '<exact line>'` for an instrument that verdicts, `run <Class>` for one
that only reports, either of them prefixed by `vary '<reason>'` when the row's
output may legitimately move. A judged probe is judged by exact-line grep
(`grep -qxF`), so `=0` can never match `=01` and a missing verdict fails the
sweep; a reporting probe fails only by crashing or exiting nonzero.

**A judged probe also owes an honest exit code, and until #1093 only the grep
was ever enforced.** The bench reads the verdict line, so nineteen probes could
print a failing verdict and exit 0 and no lane cared — while every hand-run
invocation, every `$?` in a script, and `bench.sh --twice` (whose `settle` reads
the second run's code before comparing bytes) were told the contract held.
`Probes.leave(verdict, held)` is the one place that owes both: it prints the
line the bench greps and leaves with 0 or 1 to match. A **reporting** probe must
not call it — a `run` row fails on a nonzero exit, so adopting an exit code
there changes what the row means.

**A known break is declared, not deleted and not tolerated.** `known <Class>
'<verdict>' '<#issue>' [args]` is the third verb, and it exists because #1093's
honest exit codes made "expected broken" unsayable: `judge` fails a row whose
probe exits nonzero, so a defect the tree already knows about could only be
watched by deleting the row or by dropping the exit code. Both lose something.
A `known` row requires the nonzero exit **and** matches the verdict exactly, so
a probe that starts *passing* is as red as one that starts failing differently
— that is what makes it a lock rather than a mute button. The issue number is
in the row; when it lands, the row becomes a `judge`.

**A property row runs at two seeds; an arc row runs at one (#1094).** The
distinction is what the row CLAIMS. *"The contract held"*, *"breaches = 0"*,
*"the audit is clean"* are claims about every world, and one universe cannot
support them — three units in one night found defects the canonical seed cannot
see, and the second seed added here immediately found a fourth (#1155: D-021's
perception clause holds at seed 42 and at no other seed tried). *"The film's
beats are 1299, 1525, …"*, *"movers = 19"*, *"the subject is Nadia Petrov"* are
measurements of the canonical arc, and asserting them at another seed would be
asserting a different measurement, not a stronger one. Seed 7 is the tree's
second canonical universe; nine property rows carry it, measured at 6.4 s.

**The lane has a budget of its own (#1115).** The summary line carries
`secs=<measured> budget=<ceiling> WITHIN|OVER`, because `--bench` judges the
*daemon* against D-027's table and nothing judged the sweep that runs it — so
every unit adding an instrument made the lane longer for every unit after it,
unbounded by construction. The ceiling is `BENCH_BUDGET_SECS`, 300 s by default
against a measured 147 s, and it is deliberately loose: wall clock on shared
hardware moves 20% between runs, and `OVER` **prints without failing**. The
thing being prevented is a lane growing tenfold over a season, which a human
reads; a bound that reddens under load is a bound that gets switched off.

Adding a
probe is a one-row change here, beside the probe — one row per probe, and one
row per mode where a probe verdicts in more than one. `SheetBench` is the only
one that does today, and its `--avalanche` row is `run` rather than `judge` on
purpose: that mode prints its measurements and its verdict on the same line,
so judging it by exact line would pin `mean_bitflip` and `max_axis_corr` into
the runner beside the bound the probe already prints and already checks. Its
exit code is its verdict instead, which is what `run` reads.

One row reads a committed file. `CensusBeatDrift` judges today's beat ticks against
`probes/beatdrift.baseline` — two rows, one per standard seed, verbatim as the probe
printed them at the tree their own `sha=` names. Moving the pin is those rows
re-measured in the commit that moves the film, so `git log -p -- probes/beatdrift.baseline`
is the whole move history, the way `.github/canonical-digest` holds the seal's. Its
judged line carries the set it judged (`compared=16/16`) and the tolerance it judged
against (`band=200`), because a probe that read a baseline naming none of the beats
would otherwise print the same clean line as one that compared all sixteen pairs.

## The bench refereeing itself

The probes referee the daemon. `--twice` is what referees the probes: every row
runs a second time at the same seed and budget, the two outputs are byte-compared,
and the first line that moved is printed with its number.

```
STABLE LineLint
DRIFT NameCensus line=37 a="NANO 3276938399359740" b="NANO 3276941531734784"
EXEMPT AllocMeter reason="prints its own instrument noise: steady_max is a cold …"
BENCH determinism probes=17 stable=15 drift=1 exempt=1 VERDICT INSTRUMENTS_DRIFTED
```

The digest leash proves the *world* is deterministic and says nothing about the
instruments pointed at it — and a drifting instrument is worse than none, because
it manufactures mysteries the world never had and the next skeptic spends a round
chasing a phantom that lives in the coroner, not the corpse. The sweep prints two
verdicts because they are two facts: `BENCH_GREEN` can sit directly above
`INSTRUMENTS_DRIFTED`, and the run still exits nonzero.

A row that legitimately moves declares itself with `vary` and states why, rather
than being skipped in silence. Exactly one does today: `AllocMeter` reads the JDK's
thread-allocation counter, which counts the JIT compiling as well as the daemon
allocating. Its headline barely moves — the steady window is a median of 24
repeats and printed `367` on eleven of thirteen back-to-back runs at seed 42,
`407` and `415` on the other two (#817) — but the same line deliberately
carries `steady_max`, the cold first repeat, which is a different number every
time (1,952 to 7,899 across those thirteen). That field is the
evidence and it is why the row is still exempt: an instrument that publishes its
own noise cannot pass a byte-compare, and hiding the noise to pass one would be
the disease, not the cure.

`vary` prefixes a verb rather than replacing one, because moving and being judged
are different questions. `AllocMeter` is the row that asks them separately: its
byte counts move with the JIT, so it is exempt from the determinism pass, while
`VERDICT ALLOC_IN_BUDGET` is fixed for as long as the budget holds, so the sweep
judges it (#906).

## Catalog

| Probe | Question it answers | Case it solved |
|---|---|---|
| `NameCensus` | Are grown pilot names unique at a seed? | seed 42: 196 humans, 154 distinct — namesakes are real |
| `LinkTrace` | How did one pilot's link state evolve, tick by tick? | the Nadia Petrov double-dark mystery **as of the `v3.0.0` tag** (worn 1717, freed by The One 1846, worn 2477) — on current `main` that trace is gone: her link never changes in 6,000 ticks, so run this one pinned (`git archive v3.0.0`) if you want the case the field manual narrates. A bare run now closes with `VERDICT STILL_LINK` and the count of links that *did* move in the same window, so an era mismatch reads as a verdict instead of a malfunction |
| `LinkAudit` | What is the end-state of every NeuralLink after N ticks? | ghost-link triage: open/closed × alive/dead × present/absent |
| `ChainDump` | What is the DIGEST chain of a run, as plain lines? | out-of-band replay diffing between two boxes — and since #518 the same diff at scale, which is the only cross-PROCESS determinism proof the dial has: `ChainDump [ticks] [seed] [scale]`, two runs at `2000 42 11` byte-identical, trailer `CHAIN … scale=11 entities=5266`. `--selftest` double-runs inside one process, where anything derived from the dial once is shared by both runs and cancels; two processes do not share it. The scale rides the daemon's own gate (`Config.scaleRefusal`), so 0, 101 and 217 die with the daemon's sentence and its exit 2, and scale 1 appends nothing so every chain this probe has already published keeps its bytes |
| `LedgerMirror` | Does every ledger delta equal the open-link residue mirror? | the ghost-HARDLINE class of bug, made permanently detectable (`LEDGER_ANOMALIES=0`, seeds 42, 7 & 9 — seed 9 joined when #863 modelled the clean exit, which the mirror used to report as an anomaly and explain away in a comment) |
| `SealHygiene` | Are the numbers the seal borrows from the JLS still the numbers it borrowed? | #837: `String.hashCode` sits inside `World.digestEntity`, so a species id is canonical text and not a caption — renaming `"black cat"` moved the chain from `421d7263…` to `ec0a1b61…` in total silence. Twelve ids and both door thresholds pinned; needs no ticks, no seed and no world, so it is the one probe that cannot be flaky |
| `HuntBound` | Does the running world obey the displacement law the gait table declares? | #825: `HUNT_DISP_BOUND_CM` had 74 cm of headroom and no reader — crossing it multiplies the linear far-mover term 428x while the digest, the selftest and the hunt referee all report that nothing happened. `Config.huntBoundLine()` is the tight check and runs in `--selftest`; this probe is the half a table cannot prove about itself, measuring what each gait actually spends (sparrow: 566 declared, 566 measured) and reporting the ledger occupancy nobody could see (peak 2, mean 0.147 at seed 42) |
| `OneTrace` | Does the One's death close his link the same tick? | the finale's contract after the v3 fix round: died=4284, closed=4284, `CONTRACT_HELD` |
| `CapSentinel` | Do awakened minds (present + wrapped) ever exceed the cap? | the treaty-restore cap breach, made permanently detectable (`CAP_BREACHES=0`, seeds 42 & 7) |
| `ArcBeats` | Does the film play, in order? | the D-036 DoD as a machine verdict (`BEATS_IN_ORDER` at 42 & 7) — and its own first run caught a wrong needle and a wrong beat order, which is what instruments are for |
| `AllocMeter` | What does the hot path allocate, really? | retired D-027's never-measured "allocation-free" row, then guarded the row that replaced it: steady bytes/tick and GC collections judged against the record's own 32 KB and 5, `VERDICT ALLOC_IN_BUDGET` (#906); `--selfcheck` runs the comparison's four cases with no universe, because at these figures the breach branch is otherwise unreachable. Then it caught itself: the 1,963-8,537 B/tick it used to report was one cold sample of a window C2 was still compiling into, and twenty-four repeats on fresh simulations converge to **365-367 B/tick at seed 42, 423-425 at seed 7, 0 GCs per arc** — the bound has ~90x of room, not 4x (#817). The old headline still prints, as `steady_max` |
| `SeedAtlas` | Across the multiverse, how common is the film? | 20-seed census: 17 FULL_ARC, 2 TREATY, 0 WAR, 1 QUIET (Smith can lose), 0 emergency reloads in the wild. The verdict is a seed list and not only a count — TREATY is 4 and 12, QUIET is 1 — because the row this replaces read 16 and 4 with no TREATY column at all, which folded two universes that reached peace in with the one that never went to war and lost the distinction the five verdicts exist to draw. Read at `5afa0e1` with `java -cp out:probes/out SeedAtlas 1 20 6000`, whose `ATLAS` trailer carries all five totals |
| `CensusBlocks` | Is a contiguous block of seeds a random sample of the multiverse? | **no** — the same 400 universes cut contiguously disagree at φ=5.01 (`FULL_ARC` p=0.0018) and dealt interleaved do not (p=0.72): adjacency carries information, so contiguous blocks are retired as a sampling method (census entry 5). `--selfcheck` reproduces entry 3's published z=3.10/−3.54 with no universes at all |
| `DrawMeter` | What does the rng stream spend, and where? | boot 1,728 · steady 374/tick · cascade 491 · negotiation freeze **exactly 0** — the held breath, instrumented; windows derive from the run's own transitions (`BOUNDS`), so a QUIET universe reports no cascade. Every figure but the freeze is a reading at one seed and says nothing about the next: seed 7 spends 343 steady and 514 in cascade off the same derived windows, so the unqualified `~505` this row used to carry could not have been right for both canonical universes and is 491 at this one. Read at `5afa0e1` with `java -cp out:probes/out DrawMeter 6000 42` and `java -cp out:probes/out DrawMeter 6000 7` |
| `PirateSever` | Does the wire's third ending hold — flatline, close, nothing to flush? | unit #110's DoD as a machine verdict: pirate sever + podless death with no NPE, hardline flush unchanged, a clean exit stays unkillable (`CONTRACT_HELD`) |
| `PodOptional` | Does crown #50's `pod` 0..1 ruling survive contact with all four endings? | the guard that had no keeper (#813/#849): a free-born mind and a racked one driven through the Kid's door, the treaty's door, a rig death and an unclean cut — the racked side must name its rack unit, the free-born side must say there is none, and the "untouched" cut must leave `Pod.occupied()` true. `podless=0` at every seed is REPORTED, not judged, so #121 does not break it. Re-introducing #813's bare deref turns it red on the line that lied (`POD_OPTIONAL_HELD`) |
| `DoorPressure` | Can the inward door's two refusals be reached at all, by the door the root wired? | #886: `DoorPolicy` refuses on a full rack and on a starved budget, and the canonical arc walks neither — `DOORCLAIM` measures it (seed 42, 6,000 ticks: somebody is ashore on **2** ticks, the rack has 8 free units on both, and the budget never sells below 5 against a floor of 2). Three universes off one amnesty and two sink orders: `healthy` grants exactly `REINSERTION_QUOTA` and then says *quota spent*, `rack_full` and `starved` break one half of I-1 each and must say *no slot*. Every mutation of either comparison in `substrateSeats` passes all seven CI locks including the canonical digest and turns this one red (`DOOR_PRESSURE_HELD`) |
| `FateAtlas` | Which births is the Kid band even willing to let out, and for how long is that answer true? | first the monoculture, enumerated over the 400 growable names: 11 needed 6 spikes, 197 needed 7, 192 needed 8, none ever landed more than 7, and exactly one cleared its own bar — `Otto Aydin` (threshold 161, window 474 = tick 4740). Then #764 keyed fate to the birth event and the domain became the births a span of universes grows: `admitted=5 distinct=5 top="Hugo Novak"x1` of 3,920 across seeds 1..20 at 600 windows, `VERDICT BAND_OPEN admitted=5`, and the same bar at `KID_BASE=144` admits 0 of 3,920 — the retune was forced, and this table is what forced it. `--sweep` is the second half (#843): the admitted count is a reading at one budget and nowhere else — 5, 161, 1238, 3813, 3920 at 600/1,200/2,400/6,000/20,000 windows, `VERDICT ELIGIBILITY_DRIFTS 5..3920 of 3920`. #843 expected #764 to make that line read FLAT and it does not: re-keying the die moves who is admitted, never the accumulator's slope, so the flat verdict is still owed to whatever rules #373's axis |
| `ConfirmationSweep` | Do D-001, D-011, D-021 and D-025's *Confirmation* clauses still describe this tree? | four scripted clauses that were prose since 2026-08-10, mechanized in one own-universe pass (`CONFIRMATIONS_HELD`): 413 originals restored, 6 minds out the door alive with null links, 60 frames at max_gap 100, 8 collections through notice→grace→ending. Its own first run printed `restored=409/413` — the delete broadcast and the treaty's door share tick 4329, and four restored originals walked out of the world again before the probe looked |
| `DistrictNeutral` | Does naming the city cost the world a die roll? | the D-048 catalog is the same six quarters at seeds 42, 7, 1 and 55, and the same again built out of a stream burned 1,000 draws down (`DISTRICTS_DRAW_NOTHING`) — #536's claim, kept |
| `DistrictCensus` | Do the city's names and its people's names still obey one law? | the join `NameCensus` and `DistrictNeutral` each leave to the other — one counts people and never reads the map, the other reads the map and never counts people. At seed 42: six quarters, three of them namesakes of a living citizen, five family names shared with 196 humans, all REPORTED, because a namesake is the naming law working rather than failing. What is judged is the part that can be wrong — two quarters wearing one name, a pool entry with a space in it (which would make every `SURNAME` line quietly wrong instead of loudly absent), and a name on either bank not drawn from `NamePool`. `off_pool=0` on both banks is what #842's one-home refactor claims, checked from outside instead of read off the source. Deliberately blind to a RENAME: change `District`'s `SALT_FIRST` and four quarters become different people with the verdict still green, which is #944's pin and not this probe's (`VERDICT CITY_CENSUSED`) |
| `BondBook` | What holds the bond book's ceiling, and does the book ever let go? | #852's own diagnosis, refuted and replaced: the book fills at t=1419 and the slots are not squatters — `evictable=0/64`, every other slot WOVEN and exempt by law. Also the `RETURN`/`STRAND` band that set `BOND_FORGET_WINDOWS`: with the clock off, returns of 160 and 208 windows apart still weave while the one real desertion runs to 479, so the symmetric 12 would have eaten two real bonds |
| `SameTick` | Is a liberation queued in tick T in the census before tick T ends? | #830: the root door promised it for six hundred commits and nothing checked — `ZION` prints every hundred ticks, so a one-tick slip reads identically on the line #187 offered as proof, and the census is outside the digest chain. The falsifiable form is `RealWorld.pendingLiberations` empty at every tick boundary (`SAME_TICK_ABSORB`, seeds 42/7 at the treaty t=4329/3747, seed 1 at the Kid's door t=4739). Armed against the refactor the door used to invite: hoist the drain ahead of the treaty block and it reads `first_late=4329 max_stranded=6 VERDICT LATE_ABSORB` |
| `BondScenario` | Does D-013's one exception hold both halves of its ruling — the death unwritten, and the edge never paying twice? | #377: the unwriting is checked per `(tick, name)` pair, so a saved mind's own flatline line on the saving tick is a break and a LATER one is not (the clause is one payment, not immunity). The refusal is not reachable in a canonical arc — at 6,000 ticks the clause fires and is never asked twice — so the row scripts 40,000 ticks under 60 daemons deployed through the ops console's own `agent` command, and only after the first miracle: 24 firings, 11 `refused — the edge is spent`, 10 stand-downs, 0 written anyway (`ONCE_PER_EDGE_HELD`). Letting `observeDeath` fall through its own exception branch turns it red on the five lines that lied (`VERDICT UNWRITING_BROKEN`) |
| `ClauseAftermath` | What does the most expensive line on the retail list actually BUY the mind it saves? | #1018: one tick. `ROOM_303_DEPOSIT` is 4,000 and #377 unwrites the death without moving the body, so the saved mind stands up inside the contact radius of the daemon that just killed it, still red, and D-002's 90/10 is re-rolled next tick. Every firing is followed by OBJECT IDENTITY — 196 minds wear 154 names at seed 42, so a name match across 40,000 ticks proves nothing — and gets one of four fates: `recaptured` (the 90), `rekilled` (the 10, written), `resaved` (the 10, and a second woven edge paid), `uncaught` (still prey when the budget ended). Seed 42 reads `saved=18 recaptured=16 rekilled=0 resaved=2 uncaught=0 median_delay=1`; seed 7 reaches the other branch unscripted at `saved=17 recaptured=10 rekilled=7 median_delay=1`. Thirty-five firings across two universes, not one delay above 1. The residual has a POSITIVE definition — same brain, same wire, same body, alive, red, held by the world — so the verdict is not the tautology `saved == sum`: a firing that leaves the hunt by some other door (walked out, worn by a Smith copy, re-jacked) counts `unaccounted` and turns the row red until that door is named (`AFTERMATH_ACCOUNTED`) |
| `CensusBeatDrift` | Is the film's timing drifting, merge by merge? | the eight D-036 beats at seeds 42 and 7, pinned in `probes/beatdrift.baseline` and judged against a declared 200-tick band (`DRIFT_WITHIN_BAND compared=16/16 band=200`). `ArcBeats` gates the ORDER and throws the ticks away, so #222's cascade — seed 7's overflow, flatline, peace, reboot and door all sliding +492 together, second birth +420 — passed the lane green |
| `LineLint` | Do the instrument lines still speak the grammar D-020 fixed? | the eight families as a runtime registry (`LineGrammar`) plus their validator: 360 instrument lines at seed 42, `families=6`, `VERDICT GRAMMAR_HELD` — an appended column passes, a renamed or moved one names itself. Six of eight is what a bare run reaches, which is what this row's closing clause always said while its own headline said seven. The two it does not reach are `PERF`, which the quiet sink never asks for, and `BIRTH`, which prints only where a chronos recorder is attached; both arrive together under `--chronos`, one `PERF` line and two `BIRTH` lines for 363 and `families=8`. Read at `5afa0e1` with `java -cp out:probes/out LineLint 6000 42`, and the chronos half with `java -cp out matrix.Main --headless --ticks 6000 --seed 42 --chronos rec.jsonl > chronos.out` then `java -cp out:probes/out LineLint --stdin < chronos.out` |
| `BirthInputs` | Can a reader holding nothing but the recording state the birth event the die was keyed to? | #847: it could not. The record carried tick, name and family; the derivation reads five facts, and the rack unit and the growth ordinal were on no line of the file. Its own scanner — a hundred lines that have never seen a `Simulation` — extracts the five off a recorded universe (`births=2 complete=2 short=0`, `BIRTH_INPUTS_COMPLETE`), and `--file` points the same reader at a recording on disk: a pre-#847 one prints `SHORT line=5 missing=rack,id` and `BIRTH_INPUTS_SHORT` |
| `UnparkStorm` | How big is one déjà vu, and what does the tick it lands on cost? | #522: at x11 the worst single-tick re-materialisation is 834 minds (seed 42, tick 4103) and 758 (seed 7, tick 4889), against a stated S6 bound of 1,000 — one region's fold, set below two. The wall figure is the surprise: across eleven runs at two seeds no unpark tick reached the quiet p99, while every run's FOLD cleared it and five cleared the quiet maximum — tick 4102 folds those 834 minds at 15.3-42.8x the quiet median against a storm tick at 0.61-3.75x. Parking's bill arrives when the crowd leaves, not when it comes back (`VERDICT UNPARK_STORM_BOUNDED`). The judged number is the mind count, which is a function of the seed; the wall numbers are reported beside their own noise floor and never judged, per AllocMeter's #916 note. `--selfcheck` drives all four verdicts with no universe, because two of them need a city that has not been grown yet. **No row in `bench.sh`** — a 6,000-tick x11 run is minutes of walk over ~5,260 entities, which is a laboratory's wall clock and not a lane's |
| `HullRoster` | Is the hull-naming rule total, and is it still the film? | #806's array crash, made permanently detectable without a universe: 3,000 ordinals, 3,000 distinct names, all thirteen generation marks reached, and the boot three pinned as literals — nothing else in this repository notices a renamed hull, because no `matrix.zion` state reaches the digest walk (`VERDICT ROSTER_TOTAL`) |
| `FleetLines` | Does a laydown line tell the truth about the fleet it just joined? | the other half of `HullRoster`: what a hull is CALLED was pinned over 3,000 ordinals and what the line announcing it CLAIMS was pinned by nothing, so one defect reached `main` three times — #806 called the third keel "a second hull", #948 narrated a loss into a fleet that had lost none, #1056 left that same lie in the ordinal-1 arm at the SHIPPED `FLEET_MAX = 2`. Every round was found by a human reading a log. Five arms pinned as literals and 5,999 walked, no universe: the head must name the laydown ordinal, the clause must come from a declared vocabulary of four, it must say *replaces what it lost* if and only if a hull was lost, and a clause that names a board count may only appear where the census can man that count. Its domain is honest about reachability — `replacing` is `laydown > afloat()` and ordinal 0 is an empty fleet, so `(0, true)` is not walked. Restoring #1056's arm turns it red three ways on the one line that lied (`VERDICT FLEET_LINES_TRUE`) |
| `SheetBench` | What does the character kernel derive, and is its mixer worth believing? | the two questions the parked kernel's review left as homework, answered with numbers instead of assurance: strict avalanche on the finalizer reads `mean_bitflip=0.5001` over 400 names × 16 axes × 32 bits, and cross-axis Pearson tops out at `0.0969` on `HUMAN.disbelief~integrity` under a stated `0.15` bound, printed beside the measurement because a verdict without its threshold is a claim. `--discipline` is the other half: a HUMAN asked for `replication` throws and the message names the vocabulary it does have (D-042). Building it also caught a byte bug in the bench itself — this box's JVM reports `file.encoding=ANSI_X3.4-1968`, so an em dash in a verdict line was silently becoming `?` and a line quoted in a PR would not have been the line another box prints, which is why this probe sets its own UTF-8 stream and why #836 exists for the rest. The kernel is imported by nothing in the domain, so this bench is the only place its numbers exist |
| `NeutralDiff` | Did the control group move, and at which link? | the permanent-NEUTRAL ruling's measuring stick (#212/#537): the NEUTRAL lane's chain against the sealed baseline, byte-equal link by link, `NEUTRALDIFF 60/60 byte-equal VERDICT PASS`. Its first real case was the seal on the open lane branch — `--seal` at `unit/336-neutral-forever`'s committed fixture names tick 500 as the first unequal link and 4/60 links equal, so that branch's rebase has to reseal or land a lane that is red on arrival. #528's fixture is not in `main` yet, so the bare `NeutralDiff 6000` prints `seal_missing` and exits **2** rather than passing vacuously: a referee with no fixture is red, and a length mismatch is reported only after the links the two chains share, because the moved link is the one that says where the world changed |
| `DocLint` | Do the documents still say what the tree says? | the one probe pointed at the repository instead of at a universe. Six questions: the ADR front matter, the `DECISIONS.md` emoji and the `ROADMAP.md` gate cell agree per D-number; the same three name the same PHASE, the roadmap's section heading being the one D-039 schedules units against (#957: seven Season Three decisions read v6.0 in the index and v6.5/v7.0/v7.5 in the roadmap); no accepted record still claims, unlabelled, to be awaiting a verdict; every record carries a `### Confirmation`; a missing D-number is explained; and README's pinned `main` beat column equals a live `ArcBeats.measure` — one scan, two readers, no second copy of the needles. All three hand-repairs it replaces were invisible to every other lock: #907's five decisions carried two statuses for a day, #903's four beats were 420 ticks stale, #957's seven decisions each named two phases at once. `--selfcheck` breaks a canon of its own seventeen ways and demands that each break move exactly one counter, so `DOCS_TRUE` is a verdict that has been seen to say no (`VERDICT DOCS_TRUE`) |
| `SheetDump` | What did every soul in this universe derive, and does the same seed derive it twice? | the census, printed: 681 rows at seed 42 boot — 10 cast, 196 humans, 473 programs, the Matrix, the Machine City — with `--cast`, `--wing <family>`, `--system` and `--all` so a reader can ask for one wing instead of the city. Two laws ride the exit code rather than the prose: every mode renders the census TWICE from two universes and byte-compares before a line is printed (an identity hash smuggled into a derivation prints `DRIFT line=3` and exits 1), and `cached=` is a heap walk from the composition root through `matrix.*` fields, arrays, collections and maps, so the sheets-cached-nowhere law of #350 is a number and not a promise — 0 today because the domain imports nothing from `matrix.character`, and 1 the moment a `Sheet` is parked on `World`. Its own first run found the empty wing — `--wing MACHINE` printed `souls=0`, because the machine side is singletons and statics and a sheet derives from an identity string none of them carries — and #1010 closed it the way the SYSTEM row was always closed: the city answers for itself, `SHEET the Machine City [MACHINE] power=10 precision=10 relentlessness=1`, one row minted with the name spelled once and forever, since respelling a name re-rolls the sheet attached to it. Residents (sentinels, harvesters, as catalog data under D-015) append to that wing when a verdict grows one; they do not move this row |

Thirty-four rows for thirty-seven probes. `CensusCensor`, `CensusReverdict` and
`CensusSampleSize` have no row here and none in `bench.sh` either — #816 is the unit
that gives them both. `UnparkStorm` is the reverse and says so in its own row: a
catalog entry with no sweep row, on purpose. Nothing reads this table, so until
#995's `DocLint` comparison lands the gap is found by hand:

```sh
comm -23 <(grep -al 'static void main' probes/*.java | sed 's#probes/##;s#\.java##' | sort) \
         <(grep -oE '^\| `[A-Za-z]+` \|' probes/README.md | sed 's/^| `//;s/` |$//' | sort)
```

Add a probe when an investigation demands one; leave it here when the investigation
ends. The next skeptic starts from this bench, not from zero. (Tooling under D-030.)
