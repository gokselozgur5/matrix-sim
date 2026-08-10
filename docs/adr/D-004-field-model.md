# D-004 — Field model: grid vs continuous 2D

Status: 🟡 proposed · Phase gate: v1.0 · Thread: #3

## Context
The world needs a spatial model; determinism and cheap neighbor queries matter more than smoothness at v1.

## Decision
Proposed: 72x20 integer grid with Chebyshev adjacency; revisit at v2.5 if flocking feels too coarse.

## Consequences
Simple, deterministic, terminal-native metrics; boids math gets quantized.
