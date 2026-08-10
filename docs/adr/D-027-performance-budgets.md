# D-027 — Performance budgets, --bench, digest-invariant optimization

Status: 🟡 proposed · Phase gate: v1.0 · Thread: #93

## Context
A promise of speed must be falsifiable, and speed must never change reality.

## Decision
Proposed: v1 >= 2,000 ticks/s @ ~200 entities; v2.5 >= 100 ticks/s @ 5,000; v3 arc < 5 s; allocation-free hot path; PERF line + --bench mode; optimization PRs prove identical digests + better PERF.

## Consequences
The daemon carries its own benchmark evidence; bullet time stays headroom, not a hack.
