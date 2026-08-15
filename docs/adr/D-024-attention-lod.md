---
title: "D-024 — Attention-graded fidelity: unwatched regions degrade to statistics"
status: accepted
date: 2026-08-10
decision-makers: gokselozgur5 (owner), the resident machine (pair)
consulted: thread #28
informed: phase tracker #24
---

# D-024 — Attention-graded fidelity: unwatched regions degrade to statistics

*In the context of the lazy-reality principle, facing the waste of simulating what no mind observes, we park attention-graded fidelity (regions without connected minds run as statistics) until v4.0, to achieve the core arc first, accepting uniform fidelity for now.*

## Context and Problem Statement

Nobody observes the whole world at once — consistency needs to follow attention. Mechanically: regions with no avatars nearby could tick as aggregate statistics, re-materializing entities when attention returns. Déjà vu becomes the visible cost of re-materialization.

## Decision Drivers

* The Vision's L2 made mechanical
* Interacts with D-008 (compute budget) and D-018 (cadence)
* Re-materialization must preserve digest determinism — hard and interesting

## Considered Options

* Region LOD by attention (parked)
* Uniform fidelity (status quo)

## Decision Outcome

Accepted by the owner's verdict, 2026-08-11, in session — *"hepsine agreed kanka barajı aç"* — all six gates in one breath; recorded in the gate thread.

Parked until v4.0; D-018's budgets are the embryo. Thread #28 holds the idea.

### Consequences

* Good, because Focus preserved for the film arc
* Bad, because The 'very big universe' ambition waits on this

### Confirmation

Not applicable while parked; on unparking, the first milestone is a two-region world where an unwatched region's statistics replay deterministically.

## Pros and Cons of the Options

### Region LOD by attention (parked)

* Good, because the deepest lore-mechanic in the backlog: déjà vu as cache invalidation
* Good, because massive headroom for a truly big universe
* Neutral, because needs regions, aggregation models, and re-materialization rules
* Bad, because determinism across materialization boundaries is genuinely hard
### Uniform fidelity (status quo)

* Good, because simple; digests trivially stable
* Bad, because the universe's size is capped by the tick budget alone

## More Information

Related: [D-008](D-008-processor-mode.md), [D-018](D-018-tick-budgets.md), [D-023](D-023-chronos-event-sourcing.md). Vision: L2.

**Errata (2026-08-15, #1128 — unparked; the city folds and unfolds on its own):** The parking condition is spent. `RegionMap` parks a region that has been un-HOT for `LOD_PARK_AFTER_TICKS` consecutive ticks and retries every `LOD_PARK_RETRY_TICKS`; `LOD_LINGER_TICKS`, `LOD_COLD_STRETCH`, `LOD_AGG_EVENT_DENOM` and `LOD_AGG_SPECIES_BOUND` are all read. The outcome ("Parked until v4.0; D-018's budgets are the embryo") and the Confirmation's "Not applicable while parked" are kept as written under D-029.

*The Confirmation that replaces "not applicable":* a park is observable in a live run —

```
java -cp out matrix.Main --headless --ticks 6000 --seed 42 | grep -i park
  → [005010] TRACE LOD: old city parks — 91 residents fold into statistics;
    nobody is watching
```

— which is this record's own milestone in the world's own words: an unwatched region degrades to statistics. The unpark side rides `LOD_PARK_RETRY_TICKS` and is the same mechanism read backwards; what the seed-42 arc does not do inside 6,000 ticks is look at that region again.

Referenced by: [D-004](D-004-field-model.md), [D-008](D-008-processor-mode.md), [D-018](D-018-tick-budgets.md), [D-026](D-026-language-java17.md), [D-038](D-038-season-two.md).
