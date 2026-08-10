# D-020 — Observability: event log + METRIC + DIGEST chain

Status: 🟢 accepted · Phase gate: — · Thread: —

## Context
With no screen, the system needs a face that operators and tests can trust.

## Decision
Accepted: append-only event log, METRIC lines every N ticks, and a canonical SHA-256 DIGEST chain of world state.

## Consequences
Every DoD asserts on these; two runs diff to the exact tick where reality diverged.
