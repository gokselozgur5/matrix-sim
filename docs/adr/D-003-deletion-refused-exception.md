# D-003 — GC refusal as DeletionRefusedException

Status: 🟡 proposed · Phase gate: v2.0 · Thread: #11

## Context
The Source calls handleDeletion(); Smith must be able to refuse. Exceptions-as-control-flow is a known anti-pattern.

## Decision
Proposed: keep the deliberate sin — the refusal IS an exceptional collapse of a recorded assumption.

## Consequences
Perfect lore fidelity; a documented anti-pattern lives in the codebase on purpose (see PRINCIPLES A9).
