---
title: "D-062 — The human is the subject: causal agency before character mechanics"
status: accepted
date: 2026-08-19
decision-makers: gokselozgur5 (owner), the resident machine (pair)
consulted: thread #1662
informed: phase tracker #1661
---

# D-062 — The human is the subject: causal agency before character mechanics

*In the context of evolving a deterministic film simulator into a Matrix inhabited by people, facing a model in which a Human is representable but does not yet perceive, remember, intend, or accumulate a biography, we lean toward a human-subject causal contract — world truth becomes per-mind perception, meaning, belief and memory, then intent, validated action, consequence and biography — and against adding further character-sheet, mission, or observer-output mechanics first, to achieve meaningful agency and identity continuity while preserving the no-upload boundary and deterministic replay, accepting that existing state boundaries, digests, snapshots, and plans must be re-audited.*

## Context and Problem Statement

The engine proves that one seed can reproduce one film. That is valuable physics, but it is not yet proof that anyone lives inside it: `Human` carries real-world identity and life, `PerceptionFrame` projects world facts for an operator, and human behavior still reaches canonical world state without a persistent mind mediating perception into intent. The person is represented in the model while the principal causal route runs around them.

Determinism and agency are not opposites here. The same complete state may still produce the same future; agency means the resident's situated internal state is a necessary cause of that future. Under a lawful counterfactual to what a resident perceives or remembers, their interpretation, intent, and reachable future must be able to diverge *through that resident*, rather than through a name check or scripted plot branch.

The decision is therefore prior to fields and classes: what causal contract makes a Human the subject of the Matrix rather than a row consumed by it?

## Decision Drivers

* The product must be judged from inside one ordinary life, not only from an operator log or a hero arc
* Perception must affect the world through a mind; otherwise it is output formatting, not experience
* Identity needs continuity: consequences must return as memory, biography, relationships, and changed future possibilities
* The contract must preserve D-013's no-upload boundary, D-010's determinism, and replay as a truth test
* The first gate must stay above implementation detail so psychology fields, formulas, and Java boundaries remain discussable
* Existing decisions must be extended or superseded explicitly; a new metaphor cannot silently rewrite accepted law

## Considered Options

* Human Contract first: define the causal subject, then decompose realization across existing boundaries
* Character layer first: continue D-041's identity-derived sheets atop sealed physics and add agency later
* Film and apparatus first: keep the canonical arc and its evidence plane as the product's primary center

## Decision Outcome

Chosen option: "Human Contract first", because a stat can influence an outcome without making anyone the subject of that outcome, while a causal contract tells every later mechanic what it must preserve. The Architect accepted this option in thread #1662 on 2026-08-19.

The accepted contract is:

```text
world truth
  -> situated perception
  -> meaning / belief / memory
  -> needs / values / goals
  -> intent
  -> validation and committed action
  -> consequence
  -> biography / relationships / institutions
  -> future perception
```

A resident is a subject only when three links are real: truth can change that resident's situated experience and internal history; their accumulated interpretation can change which intentions are reachable; and a committed choice can persistently change their future, relationships, or world. The Matrix may conceal reality and constrain action. It may not reduce every path from truth to future to a script that bypasses the person.

This record does not choose a psychology field list, a utility function, metaphysical free will, a class layout, or a rewrite. It also does not decide whether anomaly debt belongs to resistance, to a system that violates this contract, or to both; that is branch #1663.

### Consequences

* Good, because every future feature receives one test: does it enter and leave the resident's causal loop?
* Good, because an ordinary day becomes a stronger vertical proof than a pre-scripted hero beat
* Good, because deterministic replay remains compatible with agency understood as causal mediation
* Bad, because all causal per-mind state must be reproducible; #1664 and later realization gates must decide what is sealed directly and what is derived from sealed history
* Bad, because dependent Season Three runtime work must wait for #1664 to decide which existing boundaries remain valid

### Confirmation

The decision is realized only when a deterministic **ordinary-day proof** follows one non-hero resident through the whole loop. A baseline and a counterfactual share seed and history until one lawful perceptual fact differs; that difference changes situated perception, interpretation, belief and memory; flows through need, value and goal; produces an attributable intent and action divergence; persists in biography and at least one relationship; receives an institutional response; and changes a later perception. Each branch replays byte-identically, and removing observer prose does not change the world.

The dynamic proof and the static choice-boundary fence have separate terminal lines so neither can stand in for the other. The fence's population is not a hand-written sample: #1664 must first approve a mechanically recognizable boundary grammar, then the fence walks the entire runtime source tree in both directions — every human-choice entry toward its perception/intent boundary, and every canonical effect attributed to a human back toward a classified choice entry. `PASS` requires both derived populations to be nonzero, both checked counts to equal their totals, and no unclassified, unreadable, or bypass path. Their terminal shapes are fixed before realization is decomposed:

```text
HUMAN_SUBJECT VERDICT PASS truth=DELIVERED perception=CHANGED meaning=UPDATED belief=CHANGED memory=RETAINED needs=CAUSAL values=CAUSAL goals=CAUSAL intent=DIVERGED action=COMMITTED consequence=PERSISTED biography=CHANGED relationship=CHANGED institution=RESPONDED future_perception=CHANGED replay=IDENTICAL observer_isolation=PASS
HUMAN_CAUSALITY_FENCE VERDICT PASS choice_entries_total=N choice_entries_checked=N human_effect_sites_total=M human_effect_sites_checked=M unclassified=0 direct_truth_bypasses=0 unreadable=0
```

For this accepted documentation gate, `DocLint` must report `VERDICT DOCS_TRUE` for record/index/ROADMAP status agreement and `DocFigures` must report `VERDICT FIGURES_AGREE` for the record/index counts. Three anchored searches separately require `consulted: thread #1662` in this record, the exact D-062/#1662 row in `docs/DECISIONS.md`, and the exact D-062/#1662 gate row in `ROADMAP.md`; `git diff --name-only origin/main...HEAD` must name exactly those three documentation files and no runtime path.

## Pros and Cons of the Options

### Human Contract first

* Good, because it defines character by causal participation rather than by the presence of a sheet
* Good, because it gives perception, memory, intention, consequence, and biography one composable order
* Neutral, because the contract constrains later design without choosing its data model
* Bad, because it may reopen boundaries previously described as sealed

### Character layer first

* Good, because it continues the accepted Season Three plan with minimal architectural disturbance
* Good, because derived sheets can make contests vary immediately
* Bad, because outcome variance can still bypass lived perception, memory, meaning, and intention
* Bad, because identity risks becoming a row of attributes rather than continuity through consequence

### Film and apparatus first

* Good, because the existing digest, replay, and probe practices are mature and productive
* Bad, because more evidence about an externally observed film does not make its inhabitants causal subjects
* Bad, because contribution activity and apparatus growth can become substitutes for world growth

## More Information

Extends: [D-011](D-011-human-class.md), [D-013](D-013-neurallink-bridge.md), and [D-021](D-021-perception-feed.md). Preserves: [D-010](D-010-determinism.md) and [D-019](D-019-backend-only.md). Acceptance puts D-041's implementation priority into explicit tension without deciding its fate here; #1664 owns whether that priority is retained, amended, or superseded. D-022 debt semantics are deliberately deferred to #1663; D-036/D-041 compatibility to #1664; D-054/D-060/D-061 process supersession to #1665. Thread: #1662. Parent: #1661.

**Successor resolution (2026-08-19):** [D-064](D-064-reciprocal-debt.md) separates mind resistance from actor-owned obligation; [D-065](D-065-inhabited-finish-line.md) fixes the ordinary-day proof; and [D-066](D-066-human-causal-boundary.md) resolves the D-041 tension with the only permitted receipt → mind → intent → commit → effect grammar. These successors realize this contract without changing its accepted subject definition.
