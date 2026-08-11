---
title: "D-029 — ADRs: one MADR record per decision; DECISIONS.md becomes the index"
status: accepted
date: 2026-08-10
decision-makers: gokselozgur5 (owner), the resident machine (pair)
consulted: thread —
informed: phase tracker —
---

# D-029 — ADRs: one MADR record per decision; DECISIONS.md becomes the index

*In the context of a decision log outgrowing a single table, facing rows whose context and consequences no longer fit a cell, we decided for one MADR-format record per decision under docs/adr with DECISIONS.md as the index, and against keeping the table-only format, to achieve decisions with room for drivers, options and confirmation, accepting more files under a strict format fence.*

## Context and Problem Statement

At 25+ decisions the single table compressed every rationale into a phrase. The owner demanded real ADRs aligned with adr.github.io practice (MADR structure, Y-statements, lifecycle statuses, immutability via supersession).

## Decision Drivers

* Each decision needs room: context, drivers, options with pros/cons, confirmation
* The index must stay scannable in one screen
* ADR practice: records are immutable — supersede, do not rewrite history
* The five-document canon must not silently become fifty documents

## Considered Options

* docs/adr/ with MADR records + DECISIONS.md index
* Single table only (status quo)
* External wiki for decisions

## Decision Outcome

Chosen option: "MADR records with an index", decided by the owner on 2026-08-10 — with the explicit instruction to align with adr.github.io practice.

### Consequences

* Good, because Confirmation sections gave the evidence culture a per-decision home
* Bad, because Index/record sync is a real maintenance duty (kept mechanical via the generator)

### Confirmation

Every index row links an existing record; every record carries front matter, Y-statement, drivers, options with pros/cons, outcome, consequences and confirmation; TEMPLATE.md exists for the next decision.

## Pros and Cons of the Options

### docs/adr/ with MADR records + DECISIONS.md index

* Good, because full rationale per decision, reviewable in isolation
* Good, because statuses and supersession links carry the lifecycle honestly
* Good, because TEMPLATE.md makes future decisions cheap to record well
* Neutral, because a generator or discipline keeps index and records in sync
* Bad, because thirty-plus files (fenced: records under this decision are not free-form documents)
### Single table only (status quo)

* Good, because one file, one glance
* Bad, because rationale compressed to slogans; options and confirmation had nowhere to live
### External wiki for decisions

* Good, because rich formatting
* Bad, because decisions leave version control and review; link rot

## More Information

Amends: [D-000](D-000-process-constitution.md), [D-028](D-028-five-document-canon.md). Practice source: adr.github.io (MADR), Nygard 2011, Zdun et al. Y-statements.

Referenced by: [D-000](D-000-process-constitution.md), [D-028](D-028-five-document-canon.md), [D-035](D-035-lens-catalog.md).
