# D-007 — Terminal ANSI UI

Status: ❌ rejected · Phase gate: — · Thread: —

## Context
An ANSI renderer with glyphs and colors was drafted for watching the simulation.

## Decision
Rejected by the owner (2026-08-10): there is no frontend. Superseded by D-019/D-020; stdin survives only as the ops console.

## Consequences
The domain sheds all presentation members; observation happens through logs, metrics and digests.
