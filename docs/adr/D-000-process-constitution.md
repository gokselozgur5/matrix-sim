---
title: "D-000 — Process constitution: docs-first main, draft PRs, joint decision gates"
status: accepted
date: 2026-08-10
decision-makers: gokselozgur5 (owner), the resident machine (pair)
consulted: thread —
informed: phase tracker —
---

# D-000 — Process constitution: docs-first main, draft PRs, joint decision gates

*In the context of starting a two-party (human + AI) hobby project with real engineering standards, facing the risk of unreviewed AI output flooding the repo, we decided for a docs-first main with draft-PR phases and joint decision gates and against code-first iteration, to achieve shared ownership of every design choice, accepting slower time-to-first-code.*

## Context and Problem Statement

The project began with an AI generating 30 classes uninvited; the owner stopped it mid-keystroke and demanded that every decision be made together. The repo needed a constitution before it needed code.

## Decision Drivers

* Every consequential choice must be discussed before it binds
* Evidence culture: claims carry proofs, reviews are machine-verifiable
* Documentation must not pile up
* The history should read honestly (drafts are drafts)

## Considered Options

* Docs-first main + draft PRs + decision gates
* Code-first, document later
* External wiki / issue-only documentation

## Decision Outcome

Chosen option: "Docs-first main + draft PRs + decision gates", because the project exists to be built together, and the constitution makes together enforceable.

### Consequences

* Good, because Nothing merges undiscussed; the repo reads true at every commit
* Bad, because Ceremony overhead on small changes

### Confirmation

The five documents exist on main; every phase enters as a draft PR; every 🟡 index row has an open thread before its code lands.

## Pros and Cons of the Options

### Docs-first main + draft PRs + decision gates

* Good, because the main branch is always presentable and true
* Good, because AI pace is harnessed instead of feared
* Good, because every merge has a paper trail
* Neutral, because code arrives later than a solo sprint would deliver it
### Code-first, document later

* Good, because fastest visible progress
* Bad, because decisions get made implicitly by whoever types fastest
* Bad, because documentation becomes archaeology
### External wiki / issue-only documentation

* Good, because low friction
* Bad, because knowledge leaves the repo
* Bad, because link rot; no review gate on prose

## More Information

Amended by [D-028](D-028-five-document-canon.md) (five documents) and [D-029](D-029-adr-expansion.md) (ADR files). Principles: Dev1, Dev6.
