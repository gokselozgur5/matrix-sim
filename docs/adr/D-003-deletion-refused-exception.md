---
title: "D-003 — GC refusal as a thrown DeletionRefusedException"
status: accepted
date: 2026-08-10
decision-makers: gokselozgur5 (owner), the resident machine (pair)
consulted: thread #11
informed: phase tracker #21
---

# D-003 — GC refusal as a thrown DeletionRefusedException

*In the context of the Source deleting deprecated programs, facing the fact that Smith refuses, we lean toward a thrown DeletionRefusedException and against an outcome enum, to achieve lore-perfect semantics (the refusal IS exceptional), accepting a documented exception-as-control-flow anti-pattern.*

## Context and Problem Statement

Program.handleDeletion() is a template method the Source calls. Most programs comply. Smith does not. Should refusal be a return value (legal outcome) or an exception (contract violation)?

## Decision Drivers

* Lore fidelity: the system genuinely did not plan for refusal
* The recorded assumption (processes accept SIGTERM) should fail loudly, not politely
* Code honesty: an anti-pattern used on purpose must say so
* Interaction with D-014 (the LSP violation is the same event)

## Considered Options

* Throw DeletionRefusedException, catch in Source
* Return a DeletionOutcome enum (COMPLIED / REFUSED)
* Veto listener protocol (pre-deletion hook)

## Decision Outcome

Chosen option: "the thrown exception", because the whole point is that refusal was never part of the contract. Accepted by the owner's verdict, 2026-08-10 (thread #11).

### Consequences

* Good, because The trilogy's inciting incident is reproducible in a stack trace
* Bad, because A linter or reviewer unaware of the ADR will flag it forever (mitigation: javadoc links here)

### Confirmation

Exactly one catch site exists (Source.collect); AgentSmith/SmithPrime are the only throwers (grep); the v2.0 DoD log shows the refusal line before the fork.

## Pros and Cons of the Options

### Throw DeletionRefusedException, catch in Source

* Good, because the refusal reads as what it is: a broken contract
* Good, because one catch site; the fork logic lives with the GC
* Good, because PRINCIPLES A9 gets a concrete artifact
* Neutral, because exception-as-control-flow, documented as a deliberate sin
* Bad, because stack traces in normal operation if ever mishandled
### Return a DeletionOutcome enum (COMPLIED / REFUSED)

* Good, because idiomatically clean; no exceptions in control flow
* Bad, because makes refusal a legal, anticipated outcome — which rewrites the story: the machines did NOT anticipate it
### Veto listener protocol (pre-deletion hook)

* Good, because extensible
* Bad, because overdesign; only one program in history refuses

## More Information

Related: [D-014](D-014-smith-lsp-violation.md), [D-025](D-025-supervision-tree.md). Principle: A9. Crowns: #45, #66, #72.

Referenced by: [D-014](D-014-smith-lsp-violation.md), [D-025](D-025-supervision-tree.md).
