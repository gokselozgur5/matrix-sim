---
title: "D-010 — Determinism: one seeded Rng, no wall clock, no unordered iteration"
status: accepted
date: 2026-08-10
decision-makers: gokselozgur5 (owner), the resident machine (pair)
consulted: thread —
informed: phase tracker —
---

# D-010 — Determinism: one seeded Rng, no wall clock, no unordered iteration

*In the context of a simulation whose tests, replays and reviews all diff two runs, facing the many ways the JVM can smuggle nondeterminism in, we decided for a single seeded Rng and a ban on wall-clock time and unordered iteration in domain logic, and against per-call convenience randomness, to achieve bit-identical runs from equal seeds, accepting ceremony around every random draw.*

## Context and Problem Statement

Replay is the project's oxygen: the DoDs diff digest chains, optimization PRs must prove behavioral equality, and the reload mechanic will eventually be a literal replay (D-023). One stray Math.random() poisons all of it.

## Decision Drivers

* Same seed, same film — bit for bit (the constitutional sentence)
* Reviewability: any divergence must be attributable to a code change
* Future event sourcing (D-023) requires it retroactively

## Considered Options

* Single seeded Rng owned by World; bans enforced by review
* Free use of java.util.Random / Math.random
* Seeded substreams per entity

## Decision Outcome

Chosen option: "single seeded Rng with bans", because everything this repo promises is downstream of replayability. Accepted 2026-08-09.

### Consequences

* Good, because The double-run diff is a one-line test of the entire engine
* Bad, because A future parallel tick would need a substream design (acceptable, deferred)

### Confirmation

grep finds no banned APIs in src; the v1.0 DoD runs the engine twice with seed 42 and diffs the digest chains to empty.

## Pros and Cons of the Options

### Single seeded Rng owned by World; bans enforced by review

* Good, because one stream, one seed, total reproducibility
* Good, because violations are trivially greppable (Random, System.currentTimeMillis, nanoTime)
* Neutral, because all randomness flows through one object (also an audit feature)
* Bad, because contributors must learn the discipline once
### Free use of java.util.Random / Math.random

* Good, because zero ceremony
* Bad, because reproducibility dies quietly, discovered at the worst moment
### Seeded substreams per entity

* Good, because parallel-friendly someday
* Neutral, because more machinery than v1 needs
* Bad, because premature; can be added under the same constitution later

## More Information

Related: [D-020](D-020-observability-contract.md), [D-023](D-023-chronos-event-sourcing.md), [D-027](D-027-performance-budgets.md). Principle: A3. Crown: #46.

Referenced by: [D-004](D-004-field-model.md), [D-005](D-005-world-mutation.md), [D-020](D-020-observability-contract.md), [D-023](D-023-chronos-event-sourcing.md), [D-026](D-026-language-java17.md), [D-027](D-027-performance-budgets.md).
