---
title: "D-017 — Neighbor queries via a bucketed spatial hash"
status: proposed
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

Proposed option: "the spatial hash", because it is the boring, correct answer the grid was quietly designed for. Final call in thread #15 (v2.5 gate).

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
