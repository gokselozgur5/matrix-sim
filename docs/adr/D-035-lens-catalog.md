---
title: "D-035 — The lens catalog: asking the right question"
status: accepted
date: 2026-08-10
decision-makers: gokselozgur5 (owner), the resident machine (pair)
consulted: —
informed: docs/adr/README.md
---

# D-035 — The lens catalog: asking the right question

*In the context of closing decisions well, facing the observed fact that one right question outperforms twenty answers, we decided for a curated catalog of question-lenses in the ADR manual with a pick-two-or-three craft rule, and against both a mandatory all-lens checklist and uncodified intuition, to achieve repeatable creative interrogation of every gate, accepting that lens choice itself remains a craft no table can automate.*

## Context and Problem Statement

The owner's insight, earned live: the D-004 field-model debate was not won by comparing the given options harder — it was cracked by a single Space question ("where does 72×20 come from?") that exposed a fossil of the deleted frontend inside the domain. The point is not any one question set; it is that decisions have many faces, and each face yields to a different family of questions. Which families do we keep on the shelf, and how do we stop the shelf from becoming a form?

## Decision Drivers

* Right-question culture: the creative reframe beats the diligent comparison
* Repeatability: the next session (human or machine) should reach for the same shelf
* Anti-bureaucracy: lenses must inform gates, never gate the gates
* House memory: each lens should carry a receipt from this repo proving it fires

## Considered Options

* Curated catalog (twelve lenses) + pick-two-or-three rule, in the ADR manual
* Mandatory checklist: every lens answered on every decision
* No codification: trust each session's intuition

## Decision Outcome

Chosen option: "curated catalog with the craft rule", because a shelf of named lenses makes the right question findable while the pick-two-or-three rule keeps interrogation a craft instead of paperwork. Accepted on the owner's commission, 2026-08-10.

### Consequences

* Good, because gate discussions gain a shared vocabulary ("this smells like a Time question")
* Good, because the catalog is self-proving: every lens ships with a receipt from this repo
* Bad, because a curated list can ossify — new lenses must be admitted when they earn a receipt

### Confirmation

Gate-closing comments and Y-statements name the lenses used; the catalog lives in docs/adr/README.md with a receipt per lens; new lenses arrive by superseding this record or amending the table with their receipt.

## Pros and Cons of the Options

### Curated catalog + craft rule

* Good, because findable, teachable, and lore-flavored (the Spoon lens is the house's own)
* Neutral, because twelve is a shelf, not a doctrine — the number may drift
* Bad, because curation is a standing editorial duty

### Mandatory all-lens checklist

* Good, because nothing is ever forgotten
* Bad, because rigor's coat on bureaucracy's body; gates would slow to committee speed

### No codification

* Good, because zero ceremony
* Bad, because the best questions die with the session that asked them

## More Information

The catalog: docs/adr/README.md, "Asking the right question". Origin lenses: Time and Space named by the owner; Maturity (the five questions) also the owner's; Spoon, Secret and Pet contributed by the resident machine. Related: [D-029](D-029-adr-expansion.md), [D-034](D-034-session-ritual.md), Dev10.
