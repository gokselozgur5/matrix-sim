---
title: "D-008 — Processor-mode mechanics: pods as the Matrix's compute substrate"
status: accepted
date: 2026-08-10
decision-makers: gokselozgur5 (owner), the resident machine (pair)
consulted: thread #19
informed: phase tracker #24
---

# D-008 — Processor-mode mechanics: pods as the Matrix's compute substrate

*In the context of the battery-vs-processor lore debate, facing the opportunity to make substrate loss mechanically meaningful, we park the idea of compute-budget coupling (node loss degrades Matrix fidelity) until v4.0, to achieve focus on the core arc first, accepting that ComputeModel stays flavor for now.*

## Context and Problem Statement

The original film concept had humans as processing substrate, not batteries. If pods ARE the datacenter, then flushing pods should cost the Matrix compute — glitches should rise as the farm empties.

## Decision Drivers

* Lore depth: the theory deserves mechanics, not just a boot banner
* Scope: v1-v3 must ship first
* Interacts with attention-LOD (D-024): both modulate fidelity

## Considered Options

* Compute-budget coupling (parked)
* Flavor only (status quo)

## Decision Outcome

Accepted by the owner's verdict, 2026-08-11, in session — *"hepsine agreed kanka barajı aç"* — all six gates in one breath; recorded in the gate thread.

Parked until v4.0, to be designed together with [D-024](D-024-attention-lod.md). Thread #19 holds the idea.

### Consequences

* Good, because v1-v3 stay lean
* Bad, because The boot banner promises more than the engine delivers, for now

### Confirmation

Not applicable until unparked; the revisit trigger is D-024 entering design.

## Pros and Cons of the Options

### Compute-budget coupling (parked)

* Good, because pod count becomes a live systemic pressure; Zion raids would have physics
* Neutral, because needs a fidelity knob to exist first (D-024)
* Bad, because meaningless before the ecosystem and LOD land
### Flavor only (status quo)

* Good, because zero cost now
* Bad, because a great idea stays a banner string

## More Information

Related: [D-024](D-024-attention-lod.md). Crown: #32.

Referenced by: [D-024](D-024-attention-lod.md), [D-032](D-032-pirate-broadcast.md), [D-038](D-038-season-two.md).
