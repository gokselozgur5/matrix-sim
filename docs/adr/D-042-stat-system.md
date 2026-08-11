---
title: "D-042 — The stat system: one contest grammar, four vocabularies"
status: proposed
date: 2026-08-11
decision-makers: gokselozgur5 (owner), the resident machine (pair)
consulted: thread #212
informed: milestone v6.0
---

# D-042 — The stat system: one contest grammar, four vocabularies

*In the context of flat constants deciding iconic moments (AGENT_KILL_CHANCE=0.10), facing the need for characters whose capabilities differ, we lean toward one shared contest law with four family-specific stat vocabularies, identity-derived and digest-declared, and against four separate mechanics or statless characters, to achieve cross-family scenes that compile (evasion vs pursuit, will vs authority), accepting that the first stats migrate live mechanics and must re-prove every beat.*

## Context and Problem Statement

Every memorable scene in the films is a CROSS-family contest: a human's evasion against a program's pursuit (the rooftop run), a system's tolerance against a program's replication (the Smith overflow we already simulate — as a flat fraction), a human's will against a system's authority (the Architect's room). Four isolated mechanics could not express these. The repo has already shipped its first stat without naming it: the Kid's disbelief threshold, a pure function of the name, JLS-stable, digest-visible — the derivation grammar is proven. Config's flat constants (kill chance, speeds, flee triggers) are the migration surface.

## Decision Drivers

* Cross-family contests are the point — the math must span families
* Identity-derived sheets keep the rng stream untouched (the Kid precedent; a declared segment carries them into the digest)
* Contest outcomes must be deterministic and replayable — the chronos fold must reproduce every duel
* Migration must be beat-safe: seeds 42/7 films re-proven at every step
* Smith's cross-family license (D-014) must be expressible, not special-cased away

## Considered Options

* One contest grammar + four vocabularies (humans: evasion, will, faith, disbelief · machines: power, precision, relentlessness · systems: stability, tolerance, authority, version-fatigue · programs: purpose-integrity, privilege, replication)
* Four fully separate mechanics, one per family
* No stats — keep tuned constants

## Decision Outcome

Leaning: one grammar, four vocabularies. Awaiting the Architect's verdict in the gate thread (#212); the machine performs the flip on his word.

### Consequences

* Good, because the rooftop, the overflow, and the Architect's room all become the same law with different words
* Good, because a single grammar has a single digest segment and a single fold story
* Bad, because vocabulary discipline is now a review axis (a human must never grow 'replication')

### Confirmation

contest(a, b) is pure and seeded-deterministic; the AGENT catch at seed 42 re-runs as a contest with the flat-constant outcome reproduced at neutral sheets (migration equivalence proof), then diverges only when sheets differ — both runs byte-stable; the sheet segment rides digest, snapshot, and fold identically.

## Pros and Cons of the Options

### One grammar, four vocabularies

* Good, because cross-family scenes compile
* Good, because one referee segment
* Bad, because vocabulary policing

### Four mechanics

* Good, because families evolve freely
* Bad, because the films' scenes are precisely the interactions this cannot express

### No stats

* Good, because zero work
* Bad, because the miniature stays miniature — D-041's whole point unmet

## More Information

Related: [D-041](D-041-season-three-character-layer.md), [D-033](D-033-self-substantiation.md) (the first stat's birthplace), [D-014](D-014-smith-lsp-violation.md), [D-020](D-020-observability-contract.md). Thread: #212.
