---
title: "D-014 — AgentSmith stays a protected Liskov violation"
status: accepted
date: 2026-08-10
decision-makers: gokselozgur5 (owner), the resident machine (pair)
consulted: thread #12
informed: phase tracker #21
---

# D-014 — AgentSmith stays a protected Liskov violation

*In the context of Smith refusing deletion, facing a subtype that breaks its parent's contract, we lean toward keeping and documenting the violation and against redesigning it away, to achieve a codebase whose crisis mechanic is the actual crisis, accepting a permanently flagged anti-pattern.*

## Context and Problem Statement

AgentSmith.handleDeletion() throws where every other Program completes. A subtype that cannot substitute for its parent is a textbook LSP violation — and it is also, precisely, the plot: the system breaks because one component stops honoring the contract everyone assumed.

## Decision Drivers

* Story-as-architecture: the violation IS the inciting incident
* Honesty: if we keep an anti-pattern, we fence it and sign it
* Interaction with D-003 (the same event seen from the exception side)

## Considered Options

* Keep, fence, document (javadoc links to this ADR; PRINCIPLES A6 says: do not fix Smith)
* Legalize refusal in the contract (outcome enum, D-003 alternative)
* Remove Smith's special case entirely

## Decision Outcome

Chosen option: "keep and fence", because this codebase exists to make the failure mode a character. Accepted by the owner's verdict, 2026-08-10 (thread #12).

### Consequences

* Good, because A one-class museum of why LSP matters, visited by every reader
* Bad, because Requires the fence to actually exist (javadoc + principles + review item)

### Confirmation

AgentSmith's javadoc links this ADR; PRINCIPLES A6 contains the fence sentence; the review checklist for v2.0 includes 'did anyone try to fix Smith'.

## Pros and Cons of the Options

### Keep, fence, document (javadoc links to this ADR; PRINCIPLES A6 says: do not fix Smith)

* Good, because the design and the drama are the same object — the repo's thesis in one class
* Good, because reviewers get a written fence instead of a recurring argument
* Neutral, because every static-analysis pass will flag it forever
### Legalize refusal in the contract (outcome enum, D-003 alternative)

* Good, because no violation remains
* Bad, because rewrites the fiction: anticipated refusal is not a crisis, it is a feature
### Remove Smith's special case entirely

* Good, because clean hierarchy
* Bad, because no fork, no virus, no film — delete the repo while at it

## More Information

Related: [D-003](D-003-deletion-refused-exception.md). Principle: A6. Crown: #66.
