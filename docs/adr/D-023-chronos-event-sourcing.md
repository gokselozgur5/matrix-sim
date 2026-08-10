# D-023 — Chronos proper: event-sourced state, reload = replay

Status: 🔵 idea · Phase gate: v4.0 · Thread: #27

## Context
Today objects are the state and the log is observability; the Vision inverts that relationship.

## Decision
Parked: event log as the only truth, state = fold(events), snapshots, Architect reload as literal replay.

## Consequences
The digest chain (D-020) is this idea's embryo; revisit after v3.0 ships the simple reload.
