---
title: "D-001 — Smith infection mechanics: Decorator vs State vs flag"
status: accepted
date: 2026-08-10
decision-makers: gokselozgur5 (owner), the resident machine (pair)
consulted: thread #10
informed: phase tracker #21
---

# D-001 — Smith infection mechanics: Decorator vs State vs flag

*In the context of Smith converting entities while the finale must restore every victim intact, facing the risk of restore bugs erasing state, we lean toward a Decorator (SmithCopy wrapping the victim) and against a State enum or an infected flag, to achieve a type-guaranteed restore, accepting entity-replacement plumbing in World.*

## Context and Problem Statement

When Smith copies himself onto a victim, the victim must vanish from play yet return exactly as they were when Smith is deleted — the mass restore at the treaty is canon. Where does the original live in the meantime?

## Decision Drivers

* Restore fidelity is non-negotiable (the finale depends on it)
* The mechanism should be legible in the type system, not in scattered if-checks
* Per-tick cost must stay flat (D-027)
* Lore fidelity: a copy stands where the victim stood

## Considered Options

* Decorator: SmithCopy wraps the victim, original kept inside
* State pattern: entity carries an infected state machine
* Boolean infected flag

## Decision Outcome

Chosen option: "Decorator", because the restore guarantee belongs in the type system, not in discipline. Accepted by the owner's verdict, 2026-08-10 (thread #10).

### Consequences

* Good, because The treaty's mass restore is a loop of identity swaps — trivially correct
* Bad, because World.replace() becomes a load-bearing API and needs careful pending-queue semantics (see D-005)

### Confirmation

v2.0 DoD run: infect N entities, delete all Smiths, assert every original returns (event log count match); code review confirms no infection checks outside the Smith classes.

## Pros and Cons of the Options

### Decorator: SmithCopy wraps the victim, original kept inside

* Good, because restore is object-identity: swap the original back, zero state loss
* Good, because infection is visible in the type system (instanceof SelfReplicating)
* Good, because the World API stays generic (replace(old, new))
* Neutral, because one extra object per infected entity
* Bad, because replacement plumbing must handle pending-mutation edge cases
### State pattern: entity carries an infected state machine

* Good, because no entity replacement; identity is stable
* Neutral, because state checks spread through every behavior method
* Bad, because every entity type must know about infection — the virus leaks into all classes
### Boolean infected flag

* Good, because cheapest to write
* Bad, because restore fidelity depends on remembering to reset every field
* Bad, because the type system knows nothing; bugs hide in ifs

## More Information

Related: [D-005](D-005-world-mutation.md), [D-014](D-014-smith-lsp-violation.md), [D-025](D-025-supervision-tree.md). Crown: #67.

Referenced by: [D-005](D-005-world-mutation.md), [D-026](D-026-language-java17.md).
