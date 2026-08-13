---
title: "D-052 — Missions: templates as data, one executor"
status: accepted
date: 2026-08-11
decision-makers: gokselozgur5 (owner), the resident machine (pair)
consulted: thread #228
informed: milestone v7.0
---

# D-052 — Missions: templates as data, one executor

*In the context of orgs needing agency and a dream nobody watches, facing the temptation of a mission class hierarchy, we lean toward MissionTemplate catalog rows executed by ONE Mission class through the pending-mutation queue, with a MissionTrace probe verdicting step completeness, and against subclassed missions or scripted set-pieces, to achieve lawful org agency AND a perception feed with a plot, accepting a bounded step-grammar DSL in plain Java.*

## Context and Problem Statement

Orgs act or the era is scenery; and the oldest critique of the repo — nobody sees the dream — has a structural answer: a mind on a mission emits a feed WITH A PLOT. Templates are rows (kind, issuer, target selector, step grammar, tick budget, payoff vector: PLANT_DOUBT, SUPPRESS, ESCORT, RETRIEVE, SEVER_TRACE, INVESTIGATE_POCKET); one executor mutates the world only through D-005's queue; carriers are the machinery Season Two shipped (operatives over pirate links, loyalist programs, exile brokers). --follow an operative through a full mission and the stream reads as a detective story — checkably, because the step grammar is finite.

## Decision Drivers

* A5: templates are rows; ONE executor class
* D-005: missions mutate only through the pending queue — lawful like everything else
* The step grammar is finite so completeness is a probe verdict, not a vibe
* Pairs with D-047: the teleprinter's reader gets an EPISODE

## Considered Options

* Template rows + one executor + MissionTrace
* Mission subclasses per kind
* No missions

## Decision Outcome

Accepted by the owner's verdict, 2026-08-12, in session — *"hepsine agreed kanka barajı aç"* — all thirteen Season Three gates in one breath, the same word that opened Season Two; recorded in the gate thread.

*Recorded before the verdict, kept unedited:* Leaning: rows + one executor. Awaiting the Architect's verdict in #228; the machine performs the flip on his word.

### Consequences

* Good, because org agency and dream-watching land as one mechanism
* Good, because v4.0's fleet finally has WORK beyond drops
* Bad, because the step DSL must stay bounded (plain Java, no interpreter creep)

### Confirmation

One operative followed through a full mission passes MissionTrace completeness; the run replays byte-identically; the dream reader renders the mission as a story the Architect enjoys reading.

## Pros and Cons of the Options

### Rows + executor

* Good, because A5 and D-005 both hold by construction
* Bad, because grammar discipline

### Subclasses

* Good, because each kind is free
* Bad, because the catalog becomes a class farm

### None

* Good, because simple
* Bad, because orgs stay decorative and the dream stays plotless

## More Information

Related: [D-051](D-051-allegiance-influence.md) (payoffs move influence), [D-047](D-047-dream-reader.md) (the episode's reader), [D-005](D-005-world-mutation.md), [D-032](D-032-pirate-broadcast.md) (the carriers). Thread: #228. Origin: the Truce architecture dossier (third origin document, tracker #218).
