# D-005 — World mutation: pending queues vs snapshot

Status: 🟡 proposed · Phase gate: v1.0 · Thread: #4

## Context
Entities spawn, die and get replaced mid-tick; iteration must stay safe and deterministic.

## Decision
Proposed: pending add/remove queues flushed at tick end.

## Consequences
No ConcurrentModification hazards; replacement order is explicit; a snapshot model remains the fallback.
