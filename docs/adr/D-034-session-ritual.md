---
title: "D-034 — The session ritual: jack in, work under gates, exit through a hardline"
status: accepted
date: 2026-08-10
decision-makers: gokselozgur5 (owner), the resident machine (pair)
consulted: —
informed: CLAUDE.md, PRINCIPLES.md
---

# D-034 — The session ritual: jack in, work under gates, exit through a hardline

*In the context of many AI sessions building one big system over time, facing the entropy of each session starting cold and ending ragged, we decided for a codified three-part ritual (jack in, work under gates, hardline exit) auto-served by CLAUDE.md, and against ad-hoc session habits, to achieve continuity that survives any single session, accepting a little ceremony at both ends.*

## Context and Problem Statement

The owner's framing: work begins with a besmele — a deliberate opening. Sessions that start without situational awareness re-litigate settled decisions or bulldoze gates; sessions that end without closure strand the next machine in archaeology. The project needed its opening and closing rites written down where they load automatically.

## Decision Drivers

* Continuity: the next session must be able to start from the last session's final line
* Gate safety: awareness of 🟢/🟡/🔵 state must precede any edit
* Mission discipline: no edit without a named mission and issue number (Ag2 generalized)
* Zero-cost delivery: CLAUDE.md auto-loads — the ritual arrives before the first prompt

## Considered Options

* Codified ritual in PRINCIPLES, short rite in CLAUDE.md (auto-loaded)
* Ad-hoc session habits, trust each session's judgment
* Heavyweight process: session logs, mandatory forms, CI-enforced checklists

## Decision Outcome

Chosen option: "codified ritual, auto-served", because a rite that loads itself is the only rite that will actually be performed. Accepted by the owner's commission, 2026-08-10.

### Consequences

* Good, because every session opens aware (index state, field state, named mission) and closes clean (proofs, site hygiene, torch line)
* Good, because "next: …" lines turn the tracker into a relay baton
* Bad, because ceremony adds minutes to short sessions (worth it; a crashed exit costs hours)

### Confirmation

Spot-checkable: recent PRs and phase trackers contain mission statements with issue numbers and *next: …* torch lines; sessions leave no uncommitted drift; CLAUDE.md carries the short rite and points to the full one.

## Pros and Cons of the Options

### Codified ritual, auto-served

* Good, because delivery is automatic (CLAUDE.md) and the full text has a canonical home (PRINCIPLES)
* Neutral, because the rite must stay short enough to be lived, not skimmed
* Bad, because it is one more text to keep true

### Ad-hoc habits

* Good, because zero ceremony
* Bad, because continuity then depends on every session's mood — entropy wins

### Heavyweight process

* Good, because maximally auditable
* Bad, because forms outlive their meaning; the ritual would become paperwork cosplay

## More Information

Full text: PRINCIPLES.md → "The Ritual". Related: [D-028](D-028-five-document-canon.md) (the door), [D-030](D-030-agent-operating-model.md) (Ag2, Ag4). The closing law: a session that ends without a hardline exit did not end — it crashed.

Referenced by: [D-035](D-035-lens-catalog.md).
