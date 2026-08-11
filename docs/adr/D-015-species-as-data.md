---
title: "D-015 — Species are catalog data; classes are for behavior only"
status: accepted
date: 2026-08-10
decision-makers: gokselozgur5 (owner), the resident machine (pair)
consulted: thread #13
informed: phase tracker #22
---

# D-015 — Species are catalog data; classes are for behavior only

*In the context of a universe with hundreds of kinds of things, facing the class-explosion trap, we lean toward a Species record catalog consumed by one EnvironmentProgram class and against class-per-kind, to achieve a universe that grows by data, accepting a lookup indirection on every tick.*

## Context and Problem Statement

Birds, roses, ants, black cats, rain: they differ in glyph-less attributes — kingdom, gait, cadence, population cap — not in code. Kind-as-class would mean a Sparrow.java for every feather in the sky.

## Decision Drivers

* Adding a creature must be a one-line change
* Class count must track behavioral variety, not zoological variety
* Budgets (D-018) hang naturally off species rows
* The Bestiary should read like a bestiary

## Considered Options

* Species record + Bestiary catalog + one EnvironmentProgram
* Class per species
* External manifest file (CSV/JSON) parsed at boot

## Decision Outcome

Chosen option: "the catalog", because the universe should scale like data, and this repo already versions its reality. Accepted by the owner's verdict, 2026-08-10 (thread #13).

### Consequences

* Good, because The v2.5 PR can demo a new species in a one-line diff
* Bad, because Species-specific quirks must be expressed as strategies or drivers, never instanceof checks

### Confirmation

Adding a test species is demonstrated as a one-line diff in the v2.5 PR; EnvironmentProgram remains the only class referencing Species (grep).

## Pros and Cons of the Options

### Species record + Bestiary catalog + one EnvironmentProgram

* Good, because a thousand kinds, one class
* Good, because budget and gait live beside the kind that owns them
* Good, because the catalog is reviewable at a glance
* Neutral, because every tick resolves behavior through the species row (one indirection)
* Bad, because exotic one-off behaviors will tempt special cases — the fence is D-016
### Class per species

* Good, because maximum flexibility per kind
* Bad, because dozens of near-empty classes; the tree becomes taxonomy homework
* Bad, because every new creature is a PR-sized event
### External manifest file (CSV/JSON) parsed at boot

* Good, because non-programmers could add species
* Neutral, because a parser and a format to maintain
* Bad, because runtime input breaks 'a commit hash fully determines behavior' unless the file is versioned anyway — so it is just a worse catalog

## More Information

Related: [D-016](D-016-movement-strategies.md), [D-018](D-018-tick-budgets.md). Principle: A5. Crowns: #38, #64, #76.

Referenced by: [D-016](D-016-movement-strategies.md), [D-018](D-018-tick-budgets.md).
