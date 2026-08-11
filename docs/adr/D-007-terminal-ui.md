---
title: "D-007 — Terminal ANSI UI for watching the simulation"
status: rejected
date: 2026-08-10
decision-makers: gokselozgur5 (owner), the resident machine (pair)
consulted: thread —
informed: phase tracker —
---

# D-007 — Terminal ANSI UI for watching the simulation

*In the context of observing the simulation, facing the choice between a rendered view and pure instrumentation, we decided against the drafted ANSI terminal renderer and for a headless daemon (D-019) with an observability contract (D-020), to achieve a domain with zero presentation concerns, accepting that humans watch logs instead of pictures.*

## Context and Problem Statement

The v0 draft included a full ANSI renderer: glyphs, colors, render priorities on every entity. The owner rejected the entire concept: this project builds the Matrix's backend; a screen would make it a game.

## Decision Drivers

* Domain purity (no presentation members on entities)
* The fiction itself: the Matrix outputs dreams, not dashboards
* Everything the UI showed must remain knowable — via instruments

## Considered Options

* ANSI terminal renderer (the draft)
* Headless daemon + observability plane

## Decision Outcome

Rejected. Superseded by [D-019](D-019-backend-only.md) and [D-020](D-020-observability-contract.md); the stdin command loop survives only as the ops console (an admin plane, not a UI). Decided by the owner, 2026-08-10.

### Consequences

* Good, because The domain purge produced cleaner classes than the draft had
* Bad, because Issue #25 tracks tearing the dead renderer out of PR #1

### Confirmation

Absence is the confirmation: no presentation types or members anywhere in src (enforced by the D-019 grep and review checklist).

## Pros and Cons of the Options

### ANSI terminal renderer (the draft)

* Good, because instantly satisfying to watch
* Bad, because glyph()/color()/renderPriority() pollute every entity
* Bad, because the renderer becomes the de-facto spec of what matters
### Headless daemon + observability plane

* Good, because entities carry zero presentation code
* Good, because DoDs become machine-checkable assertions on logs/metrics/digests
* Bad, because no pictures; humans must read instruments

## More Information

Superseded by: [D-019](D-019-backend-only.md), [D-020](D-020-observability-contract.md). Cleanup: issue #25.

Referenced by: [D-019](D-019-backend-only.md).
