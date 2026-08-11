---
title: "D-017 — Neighbor queries via a bucketed spatial hash"
status: accepted
date: 2026-08-10
decision-makers: gokselozgur5 (owner), the resident machine (pair)
consulted: thread #15
informed: phase tracker #22
---

# D-017 — Neighbor queries via a bucketed spatial hash

*In the context of thousands of entities seeking neighbors every tick, facing O(n^2) scan costs, we lean toward a per-tick rebuilt spatial hash and against both linear scans and trees, to achieve flat neighbor-query cost, accepting a rebuild pass and strict iteration-order rules.*

## Context and Problem Statement

nearest() is a full scan today. Fine at 200 entities; fatal at 5,000 with boids asking for neighbors constantly. The v2.5 PERF budget (>= 100 ticks/s at 5,000) cannot survive quadratic scans.

## Decision Drivers

* PERF budgets are contractual (D-027)
* Determinism: bucket iteration order must be canonical (D-010)
* Grid cells (D-004) make bucketing natural
* Behavior neutrality: the hash must return the same answers the scan did

## Considered Options

* Bucketed spatial hash, rebuilt each tick
* Keep O(n) scans
* Quadtree / k-d tree

## Decision Outcome

Chosen option: "the spatial hash", because it is the boring, correct answer the grid was quietly designed for. Accepted by the owner's verdict, 2026-08-10 (thread #15).

### Consequences

* Good, because Boids, hunts and infection all query one structure with one order rule
* Bad, because An equivalence run (scan vs hash, same seed, same digests) must gate the swap

### Confirmation

Equivalence proof in the v2.5 PR: digest chains identical scan-vs-hash on the same seed; PERF meets the 5,000-entity budget; allocation profile flat across the rebuild.

## Pros and Cons of the Options

### Bucketed spatial hash, rebuilt each tick

* Good, because query cost tracks local density, not world size
* Good, because rebuild is one linear pass with zero allocations (reused arrays)
* Good, because maps 1:1 onto the integer grid
* Neutral, because a rebuild pass per tick (linear, cheap, measurable)
* Bad, because bucket iteration order needs an explicit canonical rule for digest stability
### Keep O(n) scans

* Good, because zero new code
* Bad, because quadratic total cost; the v2.5 budget dies on arrival
### Quadtree / k-d tree

* Good, because elegant at extreme scale and uneven density
* Neutral, because pointer-heavy, allocation-heavy, order-fragile
* Bad, because wrong tool for a dense fixed-size grid at our scale

## More Information

Related: [D-004](D-004-field-model.md), [D-016](D-016-movement-strategies.md), [D-027](D-027-performance-budgets.md). Crown: #77.

**Errata (2026-08-10, skeptic):** Equivalence is defined against the TICK-START SNAPSHOT: all entities perceive the world as it was when the tick began — simultaneous perception; a live mid-tick scan would make perception depend on iteration order, which is worse, not better. near() returns exactly the set a naive filter over that snapshot returns; result order is cell-major canonical and documented — consumers must never read order as proximity. PERF scope: the D-036-sealed ecosystem (500+ entities). The 5,000-entity figure belongs to the D-024 attention-LOD era, which also moves hunts and infection onto the hash; until then they remain honest O(n) scans.

**Errata 2 (2026-08-10, skeptic round 3):** The first errata claimed snapshot semantics the code did not yet implement — the skeptic proved it (5,501 mismatches against a snapshot-naive filter) and called the prose a rationalization. It was. The implementation now matches the physics: perception coordinates are frozen on every entity at rebuild, and BOTH sides of every query use them — seeker center and sought location; FLOCK and SWARM geometry reads snapshots as well. near() is set-equivalent to a naive filter over the tick-start snapshot BY CONSTRUCTION. A consequence is canon: a same-tick death may still be perceived — the news has not reached you yet.

**Errata 3 (2026-08-11, #135):** The first errata's parked debt is paid — `nearestAgent` / `nearestRed` / `nearestNonReplicating` leave their linear scans for an expanding-ring search over the hash buckets. The scans' semantics are reproduced exactly, and they are LIVE-read semantics, deliberately distinct from `near()`'s snapshot law: a snapshot cell is only a candidate index; every distance and every predicate (alive, pill, the One's exemption) recomputes from live state at call time, and the tie-break — first in list order at equal distance — rides a `seq` the rebuild stamps while the list is frozen. Ring *d*'s live-distance floor is *(d−1)·cell − HUNT_DISP_BOUND_CM*, sound because any entity displaced beyond the bound latches onto a per-tick **far-mover ledger** at the moment it moves (rain's ground recycle, an exile gone to ground) and the ledger is swept linearly after the rings; the search stops only when the next floor strictly exceeds the best distance, since an equal distance farther out could carry a smaller `seq`. Outside the rebuild-to-flush window (boot, observers, console) the index refuses and the scan answers — exact by definition. The referee: `-Dmatrix.huntVerify=true` replays the scan against every ring answer and throws on the first divergence — full 6,000-tick arcs at seeds 42 and 7 ran divergence-free, digest chains diffed empty against the scan implementation, allocation flat. Perception is unchanged by construction; only the finding got cheap.

Referenced by: [D-004](D-004-field-model.md), [D-016](D-016-movement-strategies.md), [D-027](D-027-performance-budgets.md).
