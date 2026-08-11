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

## Building and running

```sh
# from the repo root, daemon already built to out/
javac -encoding UTF-8 --release 17 -cp out -d probes/out probes/<Probe>.java
java -cp out:probes/out <Probe> [args]
```

## Catalog

| Probe | Question it answers | Case it solved |
|---|---|---|
| `NameCensus` | Are grown pilot names unique at a seed? | seed 42: 196 humans, 154 distinct — namesakes are real |
| `LinkTrace` | How did one pilot's link state evolve, tick by tick? | the Nadia Petrov double-dark mystery (worn 1717, freed by The One 1846, worn 2477) |
| `LinkAudit` | What is the end-state of every NeuralLink after N ticks? | ghost-link triage: open/closed × alive/dead × present/absent |
| `ChainDump` | What is the DIGEST chain of a run, as plain lines? | out-of-band replay diffing between two boxes |
| `LedgerMirror` | Does every ledger delta equal the open-link residue mirror? | the ghost-HARDLINE class of bug, made permanently detectable (`LEDGER_ANOMALIES=0`, seeds 42 & 7) |
| `OneTrace` | Does the One's death close his link the same tick? | the finale's contract after the v3 fix round: died=4284, closed=4284, `CONTRACT_HELD` |
| `CapSentinel` | Do awakened minds (present + wrapped) ever exceed the cap? | the treaty-restore cap breach, made permanently detectable (`CAP_BREACHES=0`, seeds 42 & 7) |
| `ArcBeats` | Does the film play, in order? | the D-036 DoD as a machine verdict (`BEATS_IN_ORDER` at 42 & 7) — and its own first run caught a wrong needle and a wrong beat order, which is what instruments are for |
| `AllocMeter` | What does the hot path allocate, really? | retired D-027's never-measured "allocation-free" row: ~30 KB/tick steady, 3 GCs per arc — now a bounded, guarded budget |

Add a probe when an investigation demands one; leave it here when the investigation
ends. The next skeptic starts from this bench, not from zero. (Tooling under D-030.)
