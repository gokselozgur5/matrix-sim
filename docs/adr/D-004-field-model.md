---
title: "D-004 — Field model: fixed-point city coordinates + a place graph"
status: accepted
date: 2026-08-10
decision-makers: gokselozgur5 (owner), the resident machine (pair)
consulted: thread #3
informed: phase tracker #20
---

# D-004 — Field model: fixed-point city coordinates + a place graph

*In the context of the engine's spatial model, facing both the digest chain's demand for bit-exact replays and the discovery that the drafted 72x20 grid was a fossil of the deleted frontend, we decided for fixed-point integer coordinates (centimeter precision, city scale) with a semantic place-graph layer on top, and against both the char-grid and floating-point doubles, to achieve continuous-feeling motion with integer determinism in a backend-real space, accepting fixed-point arithmetic discipline and a graph layer to grow.*

## Context and Problem Statement

The original debate was framed as integer grid versus continuous doubles. The owner rejected both — and a Space-lens question exposed why: the grid's 72x20 dimensions were the character width of a terminal renderer that D-019 had already buried. Presentation had leaked its geometry into the domain. Asked as a backend question — how does the Matrix's backend represent space? — the dichotomy dissolves.

## Decision Drivers

* Bit-exact determinism across runs and machines (D-010, D-020) — integers only
* Backend-real scale: the space is a city, not a screen
* Continuous-feeling motion for boids (v2.5) without float hazards
* Lore structure: exits, routes and districts are addresses, not coordinates
* Cheap contact and radius math at thousands of entities (D-017, D-027)

## Considered Options

* Integer char-grid (72x20, Chebyshev) — the draft
* Continuous 2D doubles
* Fixed-point integer coordinates (cm precision) + a semantic place graph

## Decision Outcome

Chosen option: "fixed-point + place graph", because centimeter-precision integers give the smoothness of a plane with the determinism of integers (the Doom method), the city scale removes the last frontend fossil from the domain, and the place graph (districts, buildings, phone-booth exit nodes) carries the lore's actual geometry: chases are routes to exits, not Euclidean pursuit. Accepted by the owner's verdict, 2026-08-10 (thread #3); the synthesis itself was forced by the owner's rejection of both original options.

### Consequences

* Good, because digest canonicalization stays a byte-dump of ints — no float formatting questions exist
* Good, because D-024's attention-LOD gains a natural unit (the graph's regions) years early
* Good, because D-002 can later evolve into the emergent exit-race mechanic on real routes
* Bad, because fixed-point discipline is real: no doubles anywhere in position math, ratios via integer arithmetic
* Bad, because the place graph is one more structure — v1 ships only its skeleton (a few zones + exit nodes)

### Confirmation

grep finds no float/double in domain position math; world dimensions in Config are city-scale centimeters (not screen numbers); the v1.0 double-run digest DoD passes; v1 contains a minimal place-graph skeleton with at least phone-booth exit nodes represented.

## Pros and Cons of the Options

### Fixed-point + place graph (chosen)

* Good, because integer determinism and continuous feel stop being a tradeoff
* Good, because addresses model the fiction (exits, districts, Mobil Ave as an inter-network station)
* Neutral, because cm precision over an 8 km city fits comfortably in int (and long for squared distances)
* Bad, because two coupled structures must stay consistent (coordinates locate, the graph names)

### Integer char-grid (the draft)

* Good, because trivially deterministic and simple
* Bad, because its dimensions were literally the width of a deleted terminal — presentation geometry in the domain
* Bad, because 8-directional hops read as a toy at any scale

### Continuous 2D doubles

* Good, because textbook vector math
* Bad, because float determinism needs permanent vigilance (ordering, strictfp, platform variance)
* Bad, because every distance pays a sqrt or a squared-compare anyway

## More Information

Supersedes the grid-vs-plane framing of the original thread. Credit: the owner's Space-lens catch ("where does 72x20 come from?") and the owner's rejection of both drafted options — the lens catalog's founding receipt (D-035). Affects crowns #36 (Cell becomes fixed-point Position), #47 (Config dimensions), #77 (SpatialHash buckets over coordinates). Related: [D-010](D-010-determinism.md), [D-017](D-017-spatial-hash.md), [D-024](D-024-attention-lod.md), [D-002](D-002-agent-catch-mechanics.md).
