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
   address or an unordered iteration fails the sweep on the line that moved.
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

Compile the whole directory, not one file: seven of the twelve probes call the
shared `Probes` reflection helper, and `javac … probes/<Probe>.java` alone fails on
them with `cannot find symbol: variable Probes`.

## The sweep

One command runs every probe and prints one verdict:

```sh
probes/bench.sh            # 6,000 ticks each, compile included
probes/bench.sh --list     # the contract table, run nothing
probes/bench.sh --twice    # the sweep, plus the determinism pass below
probes/bench.sh --without-probes   # clause 5: does src/ still stand alone?
```

The contract table lives in that script, one row per probe — `judge <Class>
'<exact line>'` for an instrument that verdicts, `run <Class>` for one that
only reports, either of them prefixed by `vary '<reason>'` when the row's
output may legitimately move. A judged probe is judged by exact-line grep
(`grep -qxF`), so `=0` can never match `=01` and a missing verdict fails the
sweep; a reporting probe fails only by crashing or exiting nonzero. Adding a
probe is a one-row change here, beside the probe.

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
| `ChainDump` | What is the DIGEST chain of a run, as plain lines? | out-of-band replay diffing between two boxes |
| `LedgerMirror` | Does every ledger delta equal the open-link residue mirror? | the ghost-HARDLINE class of bug, made permanently detectable (`LEDGER_ANOMALIES=0`, seeds 42 & 7) |
| `SealHygiene` | Are the numbers the seal borrows from the JLS still the numbers it borrowed? | #837: `String.hashCode` sits inside `World.digestEntity`, so a species id is canonical text and not a caption — renaming `"black cat"` moved the chain from `421d7263…` to `ec0a1b61…` in total silence. Twelve ids and both door thresholds pinned; needs no ticks, no seed and no world, so it is the one probe that cannot be flaky |
| `HuntBound` | Does the running world obey the displacement law the gait table declares? | #825: `HUNT_DISP_BOUND_CM` had 74 cm of headroom and no reader — crossing it multiplies the linear far-mover term 428x while the digest, the selftest and the hunt referee all report that nothing happened. `Config.huntBoundLine()` is the tight check and runs in `--selftest`; this probe is the half a table cannot prove about itself, measuring what each gait actually spends (sparrow: 566 declared, 566 measured) and reporting the ledger occupancy nobody could see (peak 2, mean 0.147 at seed 42) |
| `OneTrace` | Does the One's death close his link the same tick? | the finale's contract after the v3 fix round: died=4284, closed=4284, `CONTRACT_HELD` |
| `CapSentinel` | Do awakened minds (present + wrapped) ever exceed the cap? | the treaty-restore cap breach, made permanently detectable (`CAP_BREACHES=0`, seeds 42 & 7) |
| `ArcBeats` | Does the film play, in order? | the D-036 DoD as a machine verdict (`BEATS_IN_ORDER` at 42 & 7) — and its own first run caught a wrong needle and a wrong beat order, which is what instruments are for |
| `AllocMeter` | What does the hot path allocate, really? | retired D-027's never-measured "allocation-free" row, then guarded the row that replaced it: steady bytes/tick and GC collections judged against the record's own 32 KB and 5, `VERDICT ALLOC_IN_BUDGET` (#906); `--selfcheck` runs the comparison's four cases with no universe, because at these figures the breach branch is otherwise unreachable. Then it caught itself: the 1,963-8,537 B/tick it used to report was one cold sample of a window C2 was still compiling into, and twenty-four repeats on fresh simulations converge to **365-367 B/tick at seed 42, 423-425 at seed 7, 0 GCs per arc** — the bound has ~90x of room, not 4x (#817). The old headline still prints, as `steady_max` |
| `SeedAtlas` | Across the multiverse, how common is the film? | 20-seed census: 16 FULL_ARC, 4 QUIET (Smith can lose), 0 emergency reloads in the wild |
| `CensusBlocks` | Is a contiguous block of seeds a random sample of the multiverse? | **no** — the same 400 universes cut contiguously disagree at φ=5.01 (`FULL_ARC` p=0.0018) and dealt interleaved do not (p=0.72): adjacency carries information, so contiguous blocks are retired as a sampling method (census entry 5). `--selfcheck` reproduces entry 3's published z=3.10/−3.54 with no universes at all |
| `DrawMeter` | What does the rng stream spend, and where? | boot 1,728 · steady ~374/tick · cascade ~505 · negotiation freeze **exactly 0** — the held breath, instrumented; windows derive from the run's own transitions (`BOUNDS`), so a QUIET universe reports no cascade |
| `PirateSever` | Does the wire's third ending hold — flatline, close, nothing to flush? | unit #110's DoD as a machine verdict: pirate sever + podless death with no NPE, hardline flush unchanged, a clean exit stays unkillable (`CONTRACT_HELD`) |
| `PodOptional` | Does crown #50's `pod` 0..1 ruling survive contact with all four endings? | the guard that had no keeper (#813/#849): a free-born mind and a racked one driven through the Kid's door, the treaty's door, a rig death and an unclean cut — the racked side must name its rack unit, the free-born side must say there is none, and the "untouched" cut must leave `Pod.occupied()` true. `podless=0` at every seed is REPORTED, not judged, so #121 does not break it. Re-introducing #813's bare deref turns it red on the line that lied (`POD_OPTIONAL_HELD`) |
| `FateAtlas` | Which names is the Kid band even willing to let out, and for how long is that answer true? | the monoculture, enumerated: of 400 growable names 11 need 6 spikes, 197 need 7, 192 need 8, none ever lands more than 7, and exactly one clears its own bar — `Otto Aydin` (threshold 161, window 474 = tick 4740), confirmed live at seeds 1/5/6/9. `--sweep` adds the second half (#843): that "one" is the reading at 600 windows and nowhere else — 1, 3, 59, 374, 400 admitted at 600/1,200/2,400/6,000/20,000 windows, `VERDICT ELIGIBILITY_DRIFTS 1..400 of 400`. The verdict is flat only when the admitted count is the same integer at every budget, which is the acceptance test #764 owes |
| `ConfirmationSweep` | Do D-001, D-011, D-021 and D-025's *Confirmation* clauses still describe this tree? | four scripted clauses that were prose since 2026-08-10, mechanized in one own-universe pass (`CONFIRMATIONS_HELD`): 413 originals restored, 6 minds out the door alive with null links, 60 frames at max_gap 100, 8 collections through notice→grace→ending. Its own first run printed `restored=409/413` — the delete broadcast and the treaty's door share tick 4329, and four restored originals walked out of the world again before the probe looked |
| `LineLint` | Do the instrument lines still speak the grammar D-020 fixed? | the eight families as a runtime registry (`LineGrammar`) plus their validator: 361 instrument lines at seed 42, `families=7`, `VERDICT GRAMMAR_HELD` — an appended column passes, a renamed or moved one names itself. The eighth is `BIRTH`, which prints only where a chronos recorder is attached: the same run under `--chronos` carries 363 lines and `families=8`, and the judged bench row (no recorder, no `PERF`) reaches six of the eight |
| `HullRoster` | Is the hull-naming rule total, and is it still the film? | #806's array crash, made permanently detectable without a universe: 3,000 ordinals, 3,000 distinct names, all thirteen generation marks reached, and the boot three pinned as literals — nothing else in this repository notices a renamed hull, because no `matrix.zion` state reaches the digest walk (`VERDICT ROSTER_TOTAL`) |

Add a probe when an investigation demands one; leave it here when the investigation
ends. The next skeptic starts from this bench, not from zero. (Tooling under D-030.)
