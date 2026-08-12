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
   `src/` must build and `--selftest` must pass with this directory deleted.
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
```

The contract table lives in that script, one row per probe — `judge <Class>
'<exact line>'` for an instrument that verdicts, `run <Class>` for one that
only reports, `vary <Class> '<reason>'` for one whose output may legitimately
move. A judged probe is judged by exact-line grep (`grep -qxF`), so
`=0` can never match `=01` and a missing verdict fails the sweep; a reporting
probe fails only by crashing or exiting nonzero. Adding a probe is a one-row
change here, beside the probe.

## The bench refereeing itself

The probes referee the daemon. `--twice` is what referees the probes: every row
runs a second time at the same seed and budget, the two outputs are byte-compared,
and the first line that moved is printed with its number.

```
STABLE LineLint
DRIFT NameCensus line=37 a="NANO 3276938399359740" b="NANO 3276941531734784"
EXEMPT AllocMeter reason="reads the JDK thread-allocation counter: …"
BENCH determinism probes=15 stable=13 drift=1 exempt=1 VERDICT INSTRUMENTS_DRIFTED
```

The digest leash proves the *world* is deterministic and says nothing about the
instruments pointed at it — and a drifting instrument is worse than none, because
it manufactures mysteries the world never had and the next skeptic spends a round
chasing a phantom that lives in the coroner, not the corpse. The sweep prints two
verdicts because they are two facts: `BENCH_GREEN` can sit directly above
`INSTRUMENTS_DRIFTED`, and the run still exits nonzero.

A row that legitimately moves declares itself with `vary` and states why, rather
than being skipped in silence. Exactly one does today: `AllocMeter` reads the JDK's
thread-allocation counter, which tracks the JIT's progress as much as the daemon's
(2,098 to 5,346 bytes/tick at seed 42 — #817).

## Catalog

| Probe | Question it answers | Case it solved |
|---|---|---|
| `NameCensus` | Are grown pilot names unique at a seed? | seed 42: 196 humans, 154 distinct — namesakes are real |
| `LinkTrace` | How did one pilot's link state evolve, tick by tick? | the Nadia Petrov double-dark mystery **as of the `v3.0.0` tag** (worn 1717, freed by The One 1846, worn 2477) — on current `main` that trace is gone: her link never changes in 6,000 ticks, so run this one pinned (`git archive v3.0.0`) if you want the case the field manual narrates. A bare run now closes with `VERDICT STILL_LINK` and the count of links that *did* move in the same window, so an era mismatch reads as a verdict instead of a malfunction |
| `LinkAudit` | What is the end-state of every NeuralLink after N ticks? | ghost-link triage: open/closed × alive/dead × present/absent |
| `ChainDump` | What is the DIGEST chain of a run, as plain lines? | out-of-band replay diffing between two boxes |
| `LedgerMirror` | Does every ledger delta equal the open-link residue mirror? | the ghost-HARDLINE class of bug, made permanently detectable (`LEDGER_ANOMALIES=0`, seeds 42 & 7) |
| `OneTrace` | Does the One's death close his link the same tick? | the finale's contract after the v3 fix round: died=4284, closed=4284, `CONTRACT_HELD` |
| `CapSentinel` | Do awakened minds (present + wrapped) ever exceed the cap? | the treaty-restore cap breach, made permanently detectable (`CAP_BREACHES=0`, seeds 42 & 7) |
| `ArcBeats` | Does the film play, in order? | the D-036 DoD as a machine verdict (`BEATS_IN_ORDER` at 42 & 7) — and its own first run caught a wrong needle and a wrong beat order, which is what instruments are for |
| `AllocMeter` | What does the hot path allocate, really? | retired D-027's never-measured "allocation-free" row: ~30 KB/tick steady, 3 GCs per arc — now a bounded, guarded budget |
| `SeedAtlas` | Across the multiverse, how common is the film? | 20-seed census: 16 FULL_ARC, 4 QUIET (Smith can lose), 0 emergency reloads in the wild |
| `DrawMeter` | What does the rng stream spend, and where? | boot 1,728 · steady ~374/tick · cascade ~505 · negotiation freeze **exactly 0** — the held breath, instrumented; windows derive from the run's own transitions (`BOUNDS`), so a QUIET universe reports no cascade |
| `PirateSever` | Does the wire's third ending hold — flatline, close, nothing to flush? | unit #110's DoD as a machine verdict: pirate sever + podless death with no NPE, hardline flush unchanged, a clean exit stays unkillable (`CONTRACT_HELD`) |
| `FateAtlas` | Which names is the Kid band even willing to let out? | the monoculture, enumerated: of 400 growable names 11 need 6 spikes, 197 need 7, 192 need 8, none ever lands more than 7, and exactly one clears its own bar — `Otto Aydin` (threshold 161, window 474 = tick 4740), confirmed live at seeds 1/5/6/9 |
| `LineLint` | Do the instrument lines still speak the grammar D-020 fixed? | the seven families as a runtime registry (`LineGrammar`) plus their validator: 361 instrument lines at seed 42, `families=7`, `VERDICT GRAMMAR_HELD` — an appended column passes, a renamed or moved one names itself |

Add a probe when an investigation demands one; leave it here when the investigation
ends. The next skeptic starts from this bench, not from zero. (Tooling under D-030.)
