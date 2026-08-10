# D-025 — Supervision tree and the deletion protocol

Status: 🟡 proposed · Phase gate: v2.0 · Thread: #26

## Context
The Source is a flat GC; exiles dodge deletion ad hoc; the Vision wants a real supervisor.

## Decision
Proposed: SIGTERM + grace period; refusal lands the process in a tracked OrphanRegistry.

## Consequences
Exiles become data, mythology becomes queryable, Smith becomes the escaped special case.
