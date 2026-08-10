# D-016 — Behavior = Movement strategy composition

Status: 🟡 proposed · Phase gate: v2.5 · Thread: #14

## Context
Different kinds move differently: flocking, swarming, rooted, drifting, wandering, commuting.

## Decision
Proposed: a Movement interface with six gaits, selected via the Species row; blue-pill avatars reuse COMMUTE.

## Consequences
New behavior = new strategy; no subclass tree; boids get their own testable class.
