---
title: "D-027 — Performance budgets, --bench mode, and the digest-invariant optimization rule"
status: accepted
date: 2026-08-10
decision-makers: gokselozgur5 (owner), the resident machine (pair)
consulted: thread #93
informed: phase tracker #20
---

# D-027 — Performance budgets, --bench mode, and the digest-invariant optimization rule

*In the context of promising a fast Matrix, facing the difference between marketing and engineering, we lean toward contractual budgets verified by a built-in bench mode plus a rule that optimizations must not change digests, and against 'optimize later' vagueness, to achieve falsifiable speed, accepting that the daemon carries benchmark machinery.*

## Context and Problem Statement

The owner asked for a promise of speed. A promise you cannot falsify is marketing. The budgets: v1 >= 2,000 ticks/s at ~200 entities (single core, headless); v2.5 >= 100 ticks/s at 5,000 entities; v3 full arc < 5 s; allocation-free hot path at steady state.

## Decision Drivers

* Speed claims must be commands, not adjectives
* Optimization must never change behavior (the digest is the referee)
* Premature optimization is banned; measured optimization is contractual
* The reference box matters: budgets are pinned to a stated machine class

## Considered Options

* Budgets + PERF line + --bench + digest-invariance rule
* Optimize later, no budgets
* Continuous profiling harness from day one

## Decision Outcome

Chosen option: "contractual budgets with the digest referee", because bullet time is headroom, and headroom is measured. Accepted by the owner's verdict, 2026-08-10 (thread #93).

### Consequences

* Good, because Fast stays fast: every merge defends the budget
* Bad, because Numbers may need revision when real hardware differs (a revision is a thread comment + row update, honestly logged)

### Confirmation

--bench prints PERF meeting or beating the budget table on the reference box; the optimization-PR template requires the digest-equality diff and the PERF delta side by side.

## Pros and Cons of the Options

### Budgets + PERF line + --bench + digest-invariance rule

* Good, because every optimization PR carries two proofs: identical digests, better PERF
* Good, because regressions are caught by numbers, not vibes
* Good, because the profiler decides where ugliness is permitted
* Neutral, because benchmark code ships inside the daemon (small, contained)
* Bad, because budgets need a stated reference machine to be honest (documented in the ADR thread)
### Optimize later, no budgets

* Good, because zero ceremony now
* Bad, because speed becomes a mood; regressions arrive silently
### Continuous profiling harness from day one

* Good, because maximum visibility
* Bad, because heavy tooling before there is anything to profile; contradicts D-009

## More Information

Related: [D-010](D-010-determinism.md), [D-017](D-017-spatial-hash.md), [D-018](D-018-tick-budgets.md), [D-020](D-020-observability-contract.md). Principle: A10.

Accepted with a spark: each phase closure stamps its PERF line into the phase tracker — speed gains a git history.

**Errata (2026-08-10, skeptic):** The v2.5 row's "5,000 entities" predates D-036, which sealed the ecosystem at 500+ entities / 12 species. Measured at the sealed scope: 263-346 t/s at 663 entities with the full infection cascade — the 100 t/s floor holds. The 5,000-entity >= 100 t/s figure is retargeted to the D-024 attention-LOD era (whose design includes hash-backed hunts); it is a ceiling we owe the future, not a claim we make today.

**Errata (2026-08-11, skeptic):** Two v3-phase corrections. (1) The "v3 full arc < 5 s" row was written before the ecosystem existed — it assumed ~200 entities, and the arc now carries 663+ through a full infection cascade. Measured on the reference box: 10-14 s for the 6,000-tick arc. The row is revised to **full arc < 30 s at ecosystem scale**; the < 5 s figure joins the 5,000-entity row as a D-024-era ceiling. (2) The Confirmation promised a `--bench` mode that never shipped — for three phases the budget table had no executable check. `--bench` now exists (steady-state row + full-arc row, PASS/FAIL per row, exit code as verdict) and runs quiet, so it cannot perturb digests. Reference box, stated at last per the decision drivers: a 2-core x86-64 cloud VM (Debian, OpenJDK 17, single-threaded run) — laptops of the 2020s beat it comfortably; under load the same box measures 947-1,049 t/s steady and 14.5-15.4 s full arc (verification round), still inside every row with ~2x margin.

Referenced by: [D-006](D-006-arc-tuning.md), [D-009](D-009-build-tooling.md), [D-010](D-010-determinism.md), [D-017](D-017-spatial-hash.md), [D-018](D-018-tick-budgets.md), [D-020](D-020-observability-contract.md), [D-036](D-036-finish-line.md).
