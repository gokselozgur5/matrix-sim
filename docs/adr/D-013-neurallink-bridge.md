---
title: "D-013 — Death propagates over a NeuralLink observer bridge, not an Avatar.brain field"
status: accepted
date: 2026-08-10
decision-makers: gokselozgur5 (owner), the resident machine (pair)
consulted: thread #8
informed: phase tracker #20
---

# D-013 — Death propagates over a NeuralLink observer bridge, not an Avatar.brain field

*In the context of the mind-body death rule, facing a draft where the Matrix-side Avatar holds a real-world Brain reference, we lean toward an observer bridge on the NeuralLink and against the direct field, to achieve a one-way dependency (realworld knows entities, never the reverse), accepting event plumbing for death.*

## Context and Problem Statement

Avatar.die() currently reaches across the deployment boundary to flatline a brain. That makes the simulation aware of pod internals — the one dependency direction the whole architecture forbids.

## Decision Drivers

* entities must import nothing from realworld (A1, enforced forever)
* The death rule belongs to the CONNECTION: unplug cleanly or die — that is jack semantics in the lore
* Avatars of different kinds (pod humans, Zion pirates) die identically

## Considered Options

* NeuralLink observes avatar death; on event: brain.flatline() + pod flush
* Avatar.brain direct field (draft status quo)
* World-mediated callback registry

## Decision Outcome

Chosen option: "the observer bridge", because 'the body cannot live without the mind' is a property of the connection, and the package graph should say so. Accepted by the owner's verdict, 2026-08-10 (thread #8).

### Consequences

* Good, because grep -r matrix.realworld src/matrix/entities/ returning nothing becomes a permanent architectural test
* Bad, because A death-event contract to document and test

### Confirmation

The grep above is empty; a v1.0 run shows agent-kill → flush events with correct pod labels; unit check: killing an avatar with a detached link harms no brain.

## Pros and Cons of the Options

### NeuralLink observes avatar death; on event: brain.flatline() + pod flush

* Good, because dependency arrow points one way, verifiable by grep
* Good, because the death rule lives where the lore puts it — in the jack
* Good, because Avatar shrinks to pure Matrix behavior
* Neutral, because death becomes an event with a subscriber, slightly more indirection
* Bad, because event ordering within the tick must be specified (with D-005)
### Avatar.brain direct field (draft status quo)

* Good, because shortest code path
* Bad, because Matrix-side code holds biological objects — boundary broken
* Bad, because every future entities change can quietly deepen the coupling
### World-mediated callback registry

* Good, because no direct reference either
* Neutral, because World becomes a switchboard for realworld concerns
* Bad, because core inherits the coupling instead of entities — same disease, new host

## More Information

Related: [D-011](D-011-human-class.md), [D-005](D-005-world-mutation.md), [D-021](D-021-perception-feed.md). Principle: A1. Crown: #51.

Accepted with a spark: LinkKind is born today with a single value (HARDLINE), so D-032's PIRATE arrives as one enum constant.
