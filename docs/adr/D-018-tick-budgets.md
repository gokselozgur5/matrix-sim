---
title: "D-018 — Tick-rate scheduling and per-species population caps"
status: accepted
date: 2026-08-10
decision-makers: gokselozgur5 (owner), the resident machine (pair)
consulted: thread #16
informed: phase tracker #22
---

# D-018 — Tick-rate scheduling and per-species population caps

*In the context of a growing universe on a fixed tick budget, facing the waste of every entity thinking every tick, we lean toward a scheduling wheel keyed by species tick-rate plus population caps and against uniform ticking, to achieve scale by scheduling, accepting due-time bookkeeping.*

## Context and Problem Statement

A rose does not need 2,000 decisions per second. Cadence is a property of kind: flowers barely think, birds think often. Budgets per species keep the universe big while the tick stays flat — and this is the embryo of attention-graded fidelity (D-024).

## Decision Drivers

* The tick must stay flat as the Bestiary grows (D-027)
* Cadence is species data (D-015), not code
* Determinism: the wheel must be seed-independent and order-canonical
* Caps prevent any species from eating the world by accident

## Considered Options

* Scheduling wheel by species tickRate + population caps
* Uniform tick for everyone
* Random skip probability per species

## Decision Outcome

Chosen option: "the wheel with caps", because scheduled laziness is how big systems stay fast — and how this one will one day stop simulating what nobody watches. Accepted by the owner's verdict, 2026-08-10 (thread #16).

### Consequences

* Good, because The 5,000-entity budget becomes reachable with headroom
* Bad, because The wheel is one more core structure with an order rule to document

### Confirmation

METRIC shows per-kingdom populations at or under caps; PERF meets budget; digest chains stable across runs (wheel order canonical by entity id).

## Pros and Cons of the Options

### Scheduling wheel by species tickRate + population caps

* Good, because cost scales with active entities, not existing ones
* Good, because cadence tuning is catalog tuning
* Good, because caps make ecosystem experiments safe
* Neutral, because due-time bookkeeping per entity (one long)
* Bad, because two entities due the same tick need a canonical order (id order, documented)
### Uniform tick for everyone

* Good, because simplest loop
* Bad, because ROOTED flowers burn cycles doing nothing, at scale, forever
### Random skip probability per species

* Good, because cheap approximation of cadence
* Neutral, because consumes Rng stream per entity per tick — noisy digests on every tuning change
* Bad, because cadence becomes stochastic; metrics get fuzzier for no gain

## More Information

Related: [D-015](D-015-species-as-data.md), [D-024](D-024-attention-lod.md), [D-027](D-027-performance-budgets.md). Crown: #78.

**Errata (2026-08-10, skeptic):** The per-kingdom census lives in the additive ECO instrument line (insects/flora/mammals/weather columns), not in METRIC whose grammar is frozen. PERF scope is the D-036-sealed 500+ ecosystem; the 5,000-entity budget line moves to the D-024 era together with D-017's hash-backed hunts.
