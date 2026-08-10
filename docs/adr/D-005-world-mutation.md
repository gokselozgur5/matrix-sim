---
title: "D-005 — World mutation: pending queues vs snapshot vs double buffer"
status: accepted
date: 2026-08-10
decision-makers: gokselozgur5 (owner), the resident machine (pair)
consulted: thread #4
informed: phase tracker #20
---

# D-005 — World mutation: pending queues vs snapshot vs double buffer

*In the context of entities spawning, dying and being replaced mid-tick, facing iterator safety and deterministic ordering, we lean toward pending add/remove queues flushed at tick end and against per-tick snapshots, to achieve safe in-place iteration with near-zero allocation, accepting carefully specified flush ordering.*

## Context and Problem Statement

During a tick, an infection replaces an entity, a death removes one, a fork adds one — all while the entity list is being iterated. The mutation strategy decides iterator safety, allocation pressure and the exact semantics the digest will capture.

## Decision Drivers

* No ConcurrentModification hazards, ever
* Deterministic mutation order (the digest sees one canonical result)
* Allocation budget: the hot path should not copy the world every tick (D-027)
* Simple mental model for contributors (including agents)

## Considered Options

* Pending add/remove queues, flushed at tick end
* Immutable snapshot per tick
* Double buffer (read world / write world)

## Decision Outcome

Chosen option: "pending queues", because they are the only option whose cost stays flat while the universe grows, and their one subtlety (flush order) is exactly the kind of thing our digest chain pins down forever. Accepted by the owner's verdict, 2026-08-10 (thread #4).

### Consequences

* Good, because Tick cost is O(entities), not O(entities + copy)
* Bad, because The within-tick visibility rule must be documented in the World crown and tested

### Confirmation

A dedicated flush-order test: same seed, entities spawning/dying in one tick, digest identical across runs; review confirms no direct list mutation outside the flush.

## Pros and Cons of the Options

### Pending add/remove queues, flushed at tick end

* Good, because iteration is over a stable list; mutations are deferred and ordered
* Good, because zero copies; allocation stays flat
* Good, because flush order is a single, reviewable function
* Neutral, because entities act on a world that is one flush behind within the tick — a defined, documented semantic
* Bad, because replace() (D-001) needs explicit rules for same-tick infect-then-cure chains
### Immutable snapshot per tick

* Good, because purest semantics; trivially safe
* Bad, because copies the entity list every tick — allocation storm at ecosystem scale
* Bad, because GC pressure fights the D-027 budgets
### Double buffer (read world / write world)

* Good, because clean read/write separation
* Neutral, because doubles the state footprint
* Bad, because every entity field must be copied or shared carefully; easy to get wrong quietly

## More Information

Related: [D-001](D-001-smith-infection-decorator.md), [D-010](D-010-determinism.md). Crown: #86.

Accepted with a naming spark: pending mutations are named WorldEvent from day one, so the Chronos migration (D-023) begins as a rename-free evolution.
