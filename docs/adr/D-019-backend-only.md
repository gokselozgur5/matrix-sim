---
title: "D-019 — Backend only: no presentation anywhere in the domain"
status: accepted
date: 2026-08-10
decision-makers: gokselozgur5 (owner), the resident machine (pair)
consulted: thread —
informed: phase tracker —
---

# D-019 — Backend only: no presentation anywhere in the domain

*In the context of deciding what this project IS, facing the fork between a watchable game and a real backend, we decided for a headless daemon with zero presentation types and against any rendered view, to achieve a domain that models the Matrix rather than displays it, accepting that all observation happens through instruments.*

## Context and Problem Statement

The owner's words: we are coding the Matrix itself — just its backend; a frontend would make it a game. The draft's renderer, glyphs and colors were purged the same day (issue #25 tracks the PR cleanup).

## Decision Drivers

* The fiction: the Matrix's output is the dream, never a screen
* Domain purity: entities should not know what they look like
* Everything a screen showed must remain knowable via D-020 instruments

## Considered Options

* Headless daemon; presentation banned from the domain
* Keep a minimal renderer besides the backend

## Decision Outcome

Chosen option: "backend only", because that is the project's identity, decided by the owner on 2026-08-10. Supersedes [D-007](D-007-terminal-ui.md).

### Consequences

* Good, because The class inventory got cleaner (the purge removed three members from every entity)
* Bad, because Demo-ability depends on good instruments (an investment we wanted anyway)

### Confirmation

grep -rE 'glyph|renderPriority|Ansi' over src returns nothing (the #25 cleanup closes on this); no class in matrix.entities imports anything presentation-shaped.

## Pros and Cons of the Options

### Headless daemon; presentation banned from the domain

* Good, because entity API shrinks to behavior only
* Good, because observability becomes first-class product surface
* Good, because the fiction and the architecture agree
* Bad, because no pictures, ever; newcomers must learn to read instruments
### Keep a minimal renderer besides the backend

* Good, because easier demos
* Bad, because presentation members creep back into entities; the ban erodes commit by commit

## More Information

Supersedes: [D-007](D-007-terminal-ui.md). Related: [D-020](D-020-observability-contract.md), [D-021](D-021-perception-feed.md). Principle: A2.
