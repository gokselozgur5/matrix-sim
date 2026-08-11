---
title: "D-031 — System-of-systems: SystemNode composite under the Simulation root"
status: accepted
date: 2026-08-10
decision-makers: gokselozgur5 (owner), the resident machine (pair)
consulted: thread #94
informed: phase trackers #20, #24
---

# D-031 — System-of-systems: SystemNode composite under the Simulation root

*In the context of a universe that must eventually contain the Matrix, Zion and a fleet as peers, facing the choice between a flat root and a system hierarchy, we lean toward a SystemNode composite ticked by the Simulation root and against deferring all structure to v4.0, to achieve a root that grows by addition instead of refactor, accepting one interface built before its third implementor exists.*

## Context and Problem Statement

The owner's framing: everything is a system inside a system, all under one main system — model-based engineering for the whole universe. D-012 already gives us the Simulation root owning two sides. When Zion, its city and its fleet arrive (v4.0), is the root a hierarchy of uniform systems, or a bag of special cases?

## Decision Drivers

* The Matrix must stay one encapsulated node among siblings — its internals invisible to peer systems
* v4.0 must be an addition (new node), never a root refactor
* Cross-system interaction needs named ports, not reach-ins (A1 discipline generalized)
* Determinism: systems tick in one canonical order (D-010)
* MBSE pragmatism: the repo's models (diagrams, ADRs, crowns) must map 1:1 onto the system tree

## Considered Options

* SystemNode interface from v1.0: Simulation ticks MachineSystem + RealWorldSystem now, ZionSystem joins at v4.0
* Flat Simulation until v4.0, then restructure
* Full MBSE toolchain (SysML models, generators)

## Decision Outcome

Chosen option: "SystemNode from v1.0", because the interface costs one file while the root is being written anyway, and it converts the v4.0 Zion phase from a refactor into an append. Accepted by the owner's verdict, 2026-08-10 (thread #94).

### Consequences

* Good, because the object graph and the fiction agree: the universe is a composite of systems
* Good, because command ownership becomes explicit — PodFarm sits in the real world physically but under MachineSystem command (package = deployment layer, tree = command chain)
* Bad, because an interface with two implementors invites speculative generality (the fence: no methods beyond name/tick/health until a third node exists)

### Confirmation

Simulation contains no direct World or PodFarm field access outside its SystemNode children (review + grep); tick order of nodes is canonical and documented; the v1.0 digest DoD passes unchanged after the wrapping.

## Pros and Cons of the Options

### SystemNode from v1.0

* Good, because v4.0 Zion becomes `nodes.add(zion)` plus its own decisions
* Good, because health/metrics roll up per system (METRIC gains a per-node column when needed)
* Neutral, because two nodes barely exercise the abstraction at first
* Bad, because it must resist growing speculative methods

### Flat root until v4.0

* Good, because zero abstraction today
* Bad, because the v4.0 phase starts by breaking the root everything depends on

### Full MBSE toolchain

* Good, because models become machine-checked artifacts
* Bad, because a toolchain before an engine is cosplay (see D-023's reasoning)

## More Information

Extends [D-012](D-012-simulation-root.md). Related: [D-032](D-032-pirate-broadcast.md), [D-013](D-013-neurallink-bridge.md). Principle: A7. New crown: SystemNode.

Referenced by: [D-032](D-032-pirate-broadcast.md).
