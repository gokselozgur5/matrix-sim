---
title: "D-037 — Division of labor: the owner holds theory, the machine holds practice"
status: accepted
date: 2026-08-10
decision-makers: gokselozgur5 (the Architect), the resident machine (the Oracle)
consulted: —
informed: PRINCIPLES.md (Dev11), CLAUDE.md
---

# D-037 — Division of labor: the owner holds theory, the machine holds practice

*In the context of the owner choosing to govern theory only, facing the removal of human code review from the process, we decided for machine-borne trust — five locks on every merge plus theory briefs in prose — and against either unreviewed merging or dragging the owner back to diffs, to achieve quality the owner never has to read for, accepting heavier verification machinery on every stage.*

## Context and Problem Statement

The owner's words: "I want to stay on the theory side. From now on, do not make me read code. Make me able to trust you." The constitution (D-000) assumed joint file-by-file review; that assumption is now retired. Trust must come from machinery that a human can audit at the level of claims, not lines.

## Decision Drivers

* The owner's sovereignty stays absolute where it matters to them: decisions, scope, lore-mechanics, roadmap
* Code quality must not depend on any human reading diffs
* Every trust claim must be checkable by a command or an independent adversary
* The owner still deserves to *know* — in prose, at the theory level

## Considered Options

* The five locks + theory briefs (machine merges after machine verification)
* Unreviewed merging on the machine's word alone
* Keep requesting owner code review (status quo)

## Decision Outcome

Chosen option: "the five locks", accepted by the owner's instruction, 2026-08-10. **No merge without all five:**

1. **Green evidence** — compile clean; DoD/`--selftest` commands pass exactly as written.
2. **The digest leash** — double-run digest equality on the same seed (once the engine ticks); any optimization additionally proves an identical chain against its parent commit (D-027).
3. **Confirmation checklist** — every accepted ADR touched by the change has its Confirmation section executed and quoted in the PR.
4. **The skeptic pass** — an independent agent with fresh context, prompted to refute (contract violations, decision drift, determinism holes, correctness bugs); real findings fixed before merge, the report posted to the PR (Ag5, now mandatory since the human skeptic retired from code).
5. **The theory brief** — a short prose report to the owner: what now exists, what it means in the architecture and the fiction, what the instruments say. Zero code in it.

The owner's court: decision threads, scope, the lens work, the story. Asking the owner to read a diff is banned; presenting them a brief is mandatory.

### Consequences

* Good, because the owner's time goes where their joy is — theory — while quality gains a second, tireless reviewer
* Good, because trust becomes auditable: every merge carries its evidence in public
* Bad, because each stage pays the verification tax (a skeptic run and a brief per stage)
* Bad, because prose briefs can hide what diffs would show — mitigated by lock 4's adversary having no stake in the prose

### Confirmation

Every merged phase-PR shows the five locks in its thread (evidence block, digest proof, confirmation quotes, skeptic report, brief link); no comment addressed to the owner requests code review; briefs exist per stage in the phase tracker.

## Pros and Cons of the Options

### The five locks

* Good, because it replaces one human reviewer with a system stronger than the reviewer was
* Neutral, because merge authority moves to the machine — bounded by the locks and the owner's standing right to halt anything
* Bad, because slower per stage (worth it; a wrong merge costs more)

### Unreviewed merging

* Good, because fastest
* Bad, because "trust me" is exactly what this decision exists to replace

### Status quo (owner reviews code)

* Good, because a second pair of human eyes
* Bad, because those eyes resigned, and dragging them back would cost the collaboration its joy

## More Information

Amends [D-000](D-000-process-constitution.md) (joint review clause) and [D-036](D-036-finish-line.md)'s original stage-per-review tempo — the tempo is now stage-per-verification. Elevates Ag5/Ag6 from agent rules to merge law. Principle: Dev11. The owner's standing rights are untouched: halt anything, reopen any decision, ask any question — including, whenever they wish, "show me the code" (a right, never a duty).

The owner named the roles from the lore, and the fit is exact: **the Architect holds theory, the Oracle holds practice.** In the fiction too, the Architect wrote the equations in a room of monitors — and it was the Oracle who made the system survive contact with minds. Front matter of future records carries the role names.
