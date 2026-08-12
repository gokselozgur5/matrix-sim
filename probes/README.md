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
   in a PR without prose.
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

## Catalog

| Probe | Question it answers | Case it solved |
|---|---|---|
| `NameCensus` | Are grown pilot names unique at a seed? | seed 42: 196 humans, 154 distinct — namesakes are real |
| `LinkTrace` | How did one pilot's link state evolve, tick by tick? | the Nadia Petrov double-dark mystery **as of the `v3.0.0` tag** (worn 1717, freed by The One 1846, worn 2477) — on current `main` that trace is gone: her link never changes in 6,000 ticks, so run this one pinned (`git archive v3.0.0`) if you want the case the field manual narrates |
| `LinkAudit` | What is the end-state of every NeuralLink after N ticks? | ghost-link triage: open/closed × alive/dead × present/absent |
| `ChainDump` | What is the DIGEST chain of a run, as plain lines? | out-of-band replay diffing between two boxes |
| `LedgerMirror` | Does every ledger delta equal the open-link residue mirror? | the ghost-HARDLINE class of bug, made permanently detectable (`LEDGER_ANOMALIES=0`, seeds 42 & 7) |
| `OneTrace` | Does the One's death close his link the same tick? | the finale's contract after the v3 fix round: died=4284, closed=4284, `CONTRACT_HELD` |
| `CapSentinel` | Do awakened minds (present + wrapped) ever exceed the cap? | the treaty-restore cap breach, made permanently detectable (`CAP_BREACHES=0`, seeds 42 & 7) |
| `ArcBeats` | Does the film play, in order? | the D-036 DoD as a machine verdict (`BEATS_IN_ORDER` at 42 & 7) — and its own first run caught a wrong needle and a wrong beat order, which is what instruments are for |
| `AllocMeter` | What does the hot path allocate, really? | retired D-027's never-measured "allocation-free" row: ~30 KB/tick steady, 3 GCs per arc — now a bounded, guarded budget |
| `SeedAtlas` | Across the multiverse, how common is the film? | 20-seed census: 16 FULL_ARC, 4 QUIET (Smith can lose), 0 emergency reloads in the wild |
| `DrawMeter` | What does the rng stream spend, and where? | boot 1,728 · steady ~398/tick · cascade ~503 · negotiation freeze **exactly 0** — the held breath, instrumented |
| `PirateSever` | Does the wire's third ending hold — flatline, close, nothing to flush? | unit #110's DoD as a machine verdict: pirate sever + podless death with no NPE, hardline flush unchanged, a clean exit stays unkillable (`CONTRACT_HELD`) |

Add a probe when an investigation demands one; leave it here when the investigation
ends. The next skeptic starts from this bench, not from zero. (Tooling under D-030.)
