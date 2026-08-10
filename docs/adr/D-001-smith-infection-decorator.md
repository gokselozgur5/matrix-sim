# D-001 — Smith infection: Decorator vs State vs flag

Status: 🟡 proposed · Phase gate: v2.0 · Thread: #10

## Context
Infected entities must be fully restorable at the finale — the mass restore is canon.

## Decision
Proposed: SmithCopy wraps the victim and keeps the original object inside; deleting the copy swaps the original back.

## Consequences
The restore guarantee lives in the type system; the cost is entity replacement plumbing in World.
