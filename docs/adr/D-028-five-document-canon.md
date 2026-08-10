---
title: "D-028 — The document canon grows to five; CLAUDE.md is the AI door"
status: accepted
date: 2026-08-10
decision-makers: gokselozgur5 (owner), the resident machine (pair)
consulted: thread —
informed: phase tracker —
---

# D-028 — The document canon grows to five; CLAUDE.md is the AI door

*In the context of principles needing a first-class home and future AI sessions needing an entry point, facing the four-document limit set by D-000, we decided for adding PRINCIPLES.md as the fifth canonical document plus CLAUDE.md as machine-loading infrastructure, and against folding principles into README, to achieve a durable home for the why, accepting one more file to maintain.*

## Context and Problem Statement

The owner asked for written principles — developing, architectural, and a door for the next AIs to feel the project from inside. That content outgrows a README section and deserves canon status.

## Decision Drivers

* Principles are the why; they deserve first-class placement
* The next AI should be onboarded by the repo itself, automatically
* The pile-prevention rule must survive the amendment

## Considered Options

* PRINCIPLES.md as fifth document + CLAUDE.md pointer
* Fold principles into README

## Decision Outcome

Chosen option: "fifth document plus the door", decided by the owner on 2026-08-10.

### Consequences

* Good, because Agent principles (D-030) later found their natural home in the same file
* Bad, because Document count discipline now depends on D-029's fence holding

### Confirmation

Exactly five canonical .md documents exist plus CLAUDE.md; README's policy paragraph names them; CLAUDE.md contains pointers only, never content.

## Pros and Cons of the Options

### PRINCIPLES.md as fifth document + CLAUDE.md pointer

* Good, because the why has a home; The Door section addresses future machines directly
* Good, because CLAUDE.md auto-loads in AI sessions — the door opens itself
* Good, because the canon stays enumerable: five documents, one pointer
* Bad, because one more document to keep true
### Fold principles into README

* Good, because no new files
* Bad, because READMEs are entrances, not constitutions; the content would be skimmed to death

## More Information

Amends: [D-000](D-000-process-constitution.md). Amended by: [D-029](D-029-adr-expansion.md). Related: [D-030](D-030-agent-operating-model.md).
