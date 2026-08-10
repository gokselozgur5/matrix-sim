---
title: "D-004 — Field model: integer grid vs continuous 2D plane"
status: proposed
date: 2026-08-10
decision-makers: gokselozgur5 (owner), the resident machine (pair)
consulted: thread #3
informed: phase tracker #20
---

# D-004 — Field model: integer grid vs continuous 2D plane

*In the context of the engine's spatial model, facing the digest chain's demand for bit-exact replays, we lean toward an integer grid with Chebyshev adjacency and against continuous doubles, to achieve trivially deterministic movement and cheap neighbor logic, accepting quantized motion that boids will feel at v2.5.*

## Context and Problem Statement

Every entity needs a position and a movement rule. The choice between integer cells and floating-point coordinates ripples into determinism, distance math, the spatial hash, and how convincing flocking can look in metrics.

## Decision Drivers

* Bit-exact determinism across runs and machines (D-010, D-020)
* Cheap adjacency for hunts and infection (contact = Chebyshev distance <= 1)
* Boids quality at v2.5 (FLOCK needs some angular freedom)
* Simplicity of the v1 engine

## Considered Options

* Integer grid (72x20), Chebyshev adjacency
* Continuous 2D doubles

## Decision Outcome

Proposed option: "Integer grid", because determinism is the constitution and the grid makes it free; we accept revisiting the cell resolution (not the model) if v2.5 cohesion metrics look too blocky. Final call in thread #3 (v1.0 gate).

### Consequences

* Good, because The digest canonicalization is a byte-dump of ints — no float formatting questions
* Bad, because FLOCK at v2.5 may need a finer grid or sub-cell heading state

### Confirmation

grep shows no float positions in entities; the v1 DoD double-run digest compare passes; movement code contains no Math.sqrt on hot paths.

## Pros and Cons of the Options

### Integer grid (72x20), Chebyshev adjacency

* Good, because integer math is exactly reproducible everywhere
* Good, because contact and radius checks are trivial and branch-cheap
* Good, because SpatialHash buckets fall out naturally (cells ARE buckets)
* Neutral, because 72x20 is a Config number, not a law; the grid can grow
* Bad, because movement is 8-directional; flocking reads coarser in cohesion metrics
### Continuous 2D doubles

* Good, because smooth vectors; textbook boids math
* Neutral, because needs quantization anyway for digest canonicalization
* Bad, because float determinism requires care (strictfp, ordering discipline)
* Bad, because every distance check pays a sqrt or a squared-compare

## More Information

Related: [D-005](D-005-world-mutation.md), [D-017](D-017-spatial-hash.md), [D-027](D-027-performance-budgets.md). Crown: #36.
