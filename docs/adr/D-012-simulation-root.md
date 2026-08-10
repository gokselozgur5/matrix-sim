---
title: "D-012 — Simulation as the only composition root; PodFarm leaves World"
status: accepted
date: 2026-08-10
decision-makers: gokselozgur5 (owner), the resident machine (pair)
consulted: thread #7
informed: phase tracker #20
---

# D-012 — Simulation as the only composition root; PodFarm leaves World

*In the context of wiring the universe, facing a draft where the Matrix owns the pod farm, we lean toward a Simulation root owning RealWorld and World as siblings and against the status quo, to achieve ownership that reads true, accepting an extra top-level object.*

## Context and Problem Statement

The draft had World.pods() — the simulation containing the biological layer that hosts it. Inverted ownership: the prison was carrying the prisoners' bodies in its pocket.

## Decision Drivers

* Ownership must mirror the fiction: the universe contains both sides
* Package boundary = deployment boundary (A1) needs an owner above both
* Wiring belongs in one place (composition root pattern)

## Considered Options

* Simulation root owning RealWorld + World + Director + EventBus
* World owns PodFarm (draft status quo)
* Static globals / service locator

## Decision Outcome

Chosen option: "Simulation root", because the object graph is the first diagram anyone reads, and it should not lie. Accepted by the owner's verdict, 2026-08-10 (thread #7).

### Consequences

* Good, because World becomes purely the Matrix; RealWorld purely the flesh
* Bad, because Constructor wiring is more explicit (which is also a Good)

### Confirmation

World has no realworld imports or fields (grep clean); Main constructs exactly one Simulation; the class diagram's ownership edges match the code.

## Pros and Cons of the Options

### Simulation root owning RealWorld + World + Director + EventBus

* Good, because ownership diagram reads exactly like the deployment diagram
* Good, because one place constructs everything; tests can build small universes
* Neutral, because one more class, one more level of indirection
### World owns PodFarm (draft status quo)

* Good, because fewer objects
* Bad, because the Matrix contains the real world — architecturally false
* Bad, because realworld types leak into core
### Static globals / service locator

* Good, because least wiring code
* Bad, because untestable, un-replayable, and beneath this repo

## More Information

Related: [D-011](D-011-human-class.md), [D-013](D-013-neurallink-bridge.md). Principle: A7. Crowns: #86, #87, #88.
