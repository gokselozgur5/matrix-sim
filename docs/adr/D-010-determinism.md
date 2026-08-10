# D-010 — Determinism: seeded Rng only

Status: 🟢 accepted · Phase gate: — · Thread: —

## Context
Replay, testing and the digest chain all require bit-identical runs.

## Decision
Accepted: one seeded Rng; bare Random, wall-clock time and unordered iteration are banned from domain logic.

## Consequences
Same seed, same film; every optimization and refactor is verifiable by digest.
