# D-006 — Arc tuning constants

Status: 🟡 proposed · Phase gate: v3.0 · Thread: #17

## Context
The film arc needs pacing numbers: overflow threshold, Smith fork delay, peace duration.

## Decision
Proposed: overflow 62%, fork +350 ticks, peace 900 ticks — all in Config, tuned by METRIC feel.

## Consequences
The arc is adjustable without touching behavior code.
