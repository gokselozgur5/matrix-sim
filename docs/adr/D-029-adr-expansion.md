# D-029 — ADR files: one record per decision, DECISIONS.md becomes the index

Status: 🟢 accepted · Phase gate: — · Thread: —

## Context
The single table grew past 25 rows; context and consequences no longer fit a cell.

## Decision
Accepted (owner, 2026-08-10): docs/adr/ holds one structured record per decision; DECISIONS.md remains the single index. ADR files are records under this decision, not free-form documents — the canon stays five.

## Consequences
Each decision gains room for context, options and consequences; the index stays scannable.
