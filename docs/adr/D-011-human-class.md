---
title: "D-011 — Human as a first-class real-world entity"
status: proposed
date: 2026-08-10
decision-makers: gokselozgur5 (owner), the resident machine (pair)
consulted: thread #6
informed: phase tracker #20
---

# D-011 — Human as a first-class real-world entity

*In the context of modeling people who exist outside the simulation, facing a draft where a person is just Brain-plus-Pod, we lean toward a Human class owning its Brain and optionally holding a Pod and a NeuralLink, and against composition-only, to achieve representability of unplugged humans, accepting a realworld-side refactor.*

## Context and Problem Statement

The treaty's open door currently deletes people: opt-out removes their objects because an unplugged person has no representation. Zion crews jack in and out; between sessions they are nobody. The domain says humans exist only while dreaming — which is precisely the Matrix's own lie.

## Decision Drivers

* Unplugged humans must exist (Zion, opt-outs, the freed)
* Opt-out must be liberation, not deletion
* Ownership truth: a person owns their brain; a pod merely hosts a person
* Lore: 'residual self-image' implies a self that persists outside

## Considered Options

* Human class: owns Brain (same fate), Pod 0..1, NeuralLink 0..1, jackIn/jackOut
* Composition only (Brain + Pod, status quo draft)

## Decision Outcome

Proposed option: "Human class", because a person who leaves the pod should not cease to exist — that is the villain's ontology, not ours. Final call in thread #6 (v1.0 gate).

### Consequences

* Good, because The treaty and Zion mechanics get honest objects to stand on
* Bad, because A small draft refactor before any engine code lands

### Confirmation

After the treaty in a v3.0 run, opted-out humans appear in the RealWorld registry (log line with count) and no deletion events fire; unit check: jackOut leaves Human alive with link=null.

## Pros and Cons of the Options

### Human class: owns Brain (same fate), Pod 0..1, NeuralLink 0..1, jackIn/jackOut

* Good, because free humans are real objects with history
* Good, because treaty opt-out becomes a state change, not a delete
* Good, because PodFarm grows Humans, which reads true
* Neutral, because realworld gains one aggregate; RealWorld registry tracks them
* Bad, because NeuralLink and PodFarm signatures change from the draft
### Composition only (Brain + Pod, status quo draft)

* Good, because fewer classes
* Bad, because unplugged people are unrepresentable; opt-out deletes a person
* Bad, because the 'human' concept exists only implicitly, scattered

## More Information

Related: [D-012](D-012-simulation-root.md), [D-013](D-013-neurallink-bridge.md). Crown: #50.
