# D-017 — Spatial hash grid for neighbor queries

Status: 🟡 proposed · Phase gate: v2.5 · Thread: #15

## Context
nearest() is an O(n) scan; at ecosystem scale every tick becomes O(n^2).

## Decision
Proposed: a bucketed spatial hash rebuilt per tick, queried by radius.

## Consequences
Neighbor queries stay flat; the tick budget survives 5,000 entities.
