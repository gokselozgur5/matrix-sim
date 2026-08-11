---
title: "D-023 — Chronos proper: event-sourced state, reload as replay"
status: proposed (parked)
date: 2026-08-10
decision-makers: gokselozgur5 (owner), the resident machine (pair)
consulted: thread #27
informed: phase tracker #24
---

# D-023 — Chronos proper: event-sourced state, reload as replay

*In the context of the Vision's L1 layer, facing the gap between logging-as-observability and log-as-truth, we park full event sourcing (state = fold of events, snapshots, reload = replay) until after v3.0 ships the simple reload, to achieve focus now and a proven arc first, accepting that today objects remain the truth.*

## Context and Problem Statement

The digest chain already fingerprints reality per tick. The full inversion — events as the only truth — would make the Architect's reload a literal replay from snapshot. Powerful, and premature while the engine does not exist.

## Decision Drivers

* Reload-as-replay is the most honest possible reload
* Event sourcing before an engine exists is architecture cosplay
* The digest chain (D-020) builds the muscle this needs

## Considered Options

* Event-sourced core (parked)
* Objects as state + instruments (status quo)

## Decision Outcome

Parked until after v3.0; the revisit trigger is the simple reload shipping and hurting. Thread #27 holds the idea.

### Consequences

* Good, because v1-v3 stay achievable
* Bad, because Some v3 reload code is known-disposable (acceptable, documented)

### Confirmation

Not applicable while parked; on unparking, the first milestone is replaying a recorded v3 arc to identical digests.

## Pros and Cons of the Options

### Event-sourced core (parked)

* Good, because time travel, perfect audits, reload as replay
* Good, because optimization equivalence proofs become replay proofs
* Neutral, because a serious rewrite of World internals
* Bad, because costs are real: event schema design, fold performance, snapshot format
### Objects as state + instruments (status quo)

* Good, because simple, fast, shipping
* Bad, because reload resets by hand; history is derived, not primary

## More Information

Related: [D-010](D-010-determinism.md), [D-020](D-020-observability-contract.md). Vision: L1 Chronos.

Referenced by: [D-010](D-010-determinism.md), [D-020](D-020-observability-contract.md), [D-024](D-024-attention-lod.md), [D-038](D-038-season-two.md).
