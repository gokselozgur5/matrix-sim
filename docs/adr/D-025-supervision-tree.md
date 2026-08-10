---
title: "D-025 — Supervisor-lite: grace periods and an orphan registry under the Source"
status: proposed
date: 2026-08-10
decision-makers: gokselozgur5 (owner), the resident machine (pair)
consulted: thread #26
informed: phase tracker #21
---

# D-025 — Supervisor-lite: grace periods and an orphan registry under the Source

*In the context of program lifecycle, facing a draft where the Source is a flat GC and exiles dodge deletion ad hoc, we lean toward supervisor-lite (SIGTERM + grace period + tracked OrphanRegistry) and against both a full OTP-style tree and the status quo, to achieve mythology as queryable data, accepting registry bookkeeping.*

## Context and Problem Statement

Deprecation should be a protocol: notice, grace, compliance or refusal. Refusal today is scattered — Smith throws, exiles teleport. The machine side deserves an honest ledger of everything that refused to die.

## Decision Drivers

* Exiles should be data (the mythology METRIC wants a source of truth)
* Smith's refusal should be the EXCEPTIONAL case against a normal protocol backdrop
* Scope: a full supervision tree is more machinery than v2 needs

## Considered Options

* Supervisor-lite: grace period + OrphanRegistry owned by Source
* Full OTP-style supervision tree
* Flat GC status quo

## Decision Outcome

Proposed option: "supervisor-lite", because the drama needs a lawful background to be visible against, and a registry is the cheapest lawful background there is. Final call in thread #26 (v2.0 gate).

### Consequences

* Good, because The v2.0 DoD gains a checkable exile lifecycle
* Bad, because A second lifecycle structure (after the wheel) with order rules to document

### Confirmation

A v2.0 run shows: deprecation notice → grace window → compliance (GC line) or refusal (orphan registered); the mythology event cites the registry count.

## Pros and Cons of the Options

### Supervisor-lite: grace period + OrphanRegistry owned by Source

* Good, because deletion becomes a narratable protocol with log lines per stage
* Good, because exile mythology events draw from a real registry
* Good, because Smith's exception stands out against a lawful backdrop (sharpens D-003/D-014)
* Neutral, because one registry and a grace timer per collection
### Full OTP-style supervision tree

* Good, because restart strategies, hierarchies — the real thing
* Bad, because v2 does not restart anything; the tree would supervise air
### Flat GC status quo

* Good, because zero new code
* Bad, because exiles stay ad-hoc teleporters; mythology stays vibes instead of data

## More Information

Related: [D-003](D-003-deletion-refused-exception.md), [D-014](D-014-smith-lsp-violation.md). Crowns: #71, #72. Principle: A9.
