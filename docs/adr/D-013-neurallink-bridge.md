# D-013 — NeuralLink observer bridge replaces Avatar.brain

Status: 🟡 proposed · Phase gate: v1.0 · Thread: #8

## Context
Avatar holding a Brain lets the Matrix side see pod internals; the dependency points the wrong way.

## Decision
Proposed: Avatar emits a death event; the NeuralLink observes it and flatlines the brain, flushes the pod.

## Consequences
entities imports nothing from realworld; the death rule becomes a property of the connection — as in the lore.
