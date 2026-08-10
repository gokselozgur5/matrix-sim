# D-014 — AgentSmith as a documented LSP violation

Status: 🟡 proposed · Phase gate: v2.0 · Thread: #12

## Context
AgentSmith.handleDeletion() throws where the parent contract completes — a subtype no longer substitutable.

## Decision
Proposed: keep and document; the crisis mechanic IS the violation.

## Consequences
A protected anti-pattern with a fence around it (PRINCIPLES A6: do not fix Smith).
