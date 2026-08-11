---
title: "D-016 — Behavior variety via Movement strategy composition"
status: accepted
date: 2026-08-10
decision-makers: gokselozgur5 (owner), the resident machine (pair)
consulted: thread #14
informed: phase tracker #22
---

# D-016 — Behavior variety via Movement strategy composition

*In the context of kinds that move differently, facing the choice between subclassing and composition, we lean toward a Movement strategy interface with six gaits and against behavior subclasses, to achieve pluggable, individually testable motion, accepting one virtual dispatch per move.*

## Context and Problem Statement

Flocking birds, boiling insects, rooted flowers, drifting rain, wandering cats, commuting blue-pills: six motion styles shared across many species — a textbook strategy seam.

## Decision Drivers

* New gait = new class, zero touch to existing entities
* Gaits must be unit-testable in isolation (FLOCK has real math)
* Blue-pill avatars should reuse COMMUTE (predictability is the point)
* No instanceof-driven behavior anywhere

## Considered Options

* Movement interface: FLOCK, SWARM, ROOTED, DRIFT, WANDER, COMMUTE
* Subclass per behavior (FlockingProgram, RootedProgram, ...)
* Full ECS split (components + systems)

## Decision Outcome

Chosen option: "the strategy seam", because six gaits shared by dozens of kinds is exactly what strategies are for, and ECS stays our documented escape hatch. Accepted by the owner's verdict, 2026-08-10 (thread #14).

### Consequences

* Good, because FlockMovement's math gets its own crown, tests and cohesion metric
* Bad, because A gait needing per-entity memory (COMMUTE waypoints) defines where that state lives

### Confirmation

Each gait class has an isolated test; swapping a species' gait is a catalog-only diff (demoed in the v2.5 PR); the flock-cohesion METRIC beats the random-walk baseline.

## Pros and Cons of the Options

### Movement interface: FLOCK, SWARM, ROOTED, DRIFT, WANDER, COMMUTE

* Good, because each gait is a small pure class with its own tests
* Good, because species select gaits by data (D-015); avatars can borrow them
* Good, because boids live in one reviewable file
* Neutral, because one interface dispatch per entity move
### Subclass per behavior (FlockingProgram, RootedProgram, ...)

* Good, because no dispatch
* Bad, because behavior x kind matrix explodes the hierarchy
* Bad, because avatars cannot reuse program movement
### Full ECS split (components + systems)

* Good, because maximum flexibility; industry standard at scale
* Neutral, because a paradigm, not a pattern — heavy for this engine
* Bad, because overkill until profiling (D-027) says objects are the bottleneck

## More Information

Related: [D-015](D-015-species-as-data.md), [D-017](D-017-spatial-hash.md). Crowns: #44, #54-#59.

Referenced by: [D-015](D-015-species-as-data.md), [D-017](D-017-spatial-hash.md).
