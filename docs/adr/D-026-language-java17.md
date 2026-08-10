---
title: "D-026 — Implementation language: Java 17 on the JVM"
status: proposed
date: 2026-08-10
decision-makers: gokselozgur5 (owner), the resident machine (pair)
consulted: thread #92
informed: phase tracker #20
---

# D-026 — Implementation language: Java 17 on the JVM

*In the context of choosing the implementation language, facing C++ as the owner's daily driver, we lean toward Java 17 and against C++, to achieve near-free bit-exact determinism and a GC-shaped restore mechanic, accepting distance from the owner's professional toolchain.*

## Context and Problem Statement

C++ was weighed seriously: RAII ownership would model Smith's theft of bodies beautifully, and it is the owner's home turf. But this repo's constitution is replayability, and its central mechanic is an object that must survive while decorated.

## Decision Drivers

* The digest DoD wants defined semantics: no UB, specified overflow, one float story
* SmithCopy restore is GC-shaped: the original lives because a reference exists
* Sealed interfaces + records fit the entity taxonomy
* 91 issues and the draft already assume the JVM

## Considered Options

* Java 17
* C++20

## Decision Outcome

Proposed option: "Java 17", because the constitution (determinism) and the central mechanic (restore) both point the same way; C++ remains the documented escape hatch if D-024-scale ever demands a native hot path. Final call in thread #92 (v1.0 gate).

### Consequences

* Good, because The determinism promise is structural, not heroic
* Bad, because If a native hot path is ever needed, the boundary design lands as a new ADR

### Confirmation

javac --release 17 builds the repo with no external dependencies; the v1.0 DoD digest diff passes on this box.

## Pros and Cons of the Options

### Java 17

* Good, because JVM semantics make bit-identical replays nearly free
* Good, because mass restore is reference-safe by construction — no use-after-free plot holes
* Good, because sealed hierarchies let the compiler hold the taxonomy
* Good, because zero-dependency stdlib covers SHA-256, collections, everything v1-v3 needs
* Neutral, because GC pauses are irrelevant at our scale but allocation discipline still matters (D-027)
* Bad, because no RAII: ownership stories are conventions, not types
* Bad, because further from the owner's daily toolchain
### C++20

* Good, because the owner's professional muscle; unique_ptr theft-of-ownership is thematically delicious
* Good, because the highest performance ceiling
* Bad, because UB and platform float behavior make bit-exact cross-run digests a compiler-flag negotiation
* Bad, because the restore mechanic becomes an ownership thesis; one wrong delete kills the Oracle for real
* Bad, because build system + sha256 vendoring reintroduce the dependency question

## More Information

Related: [D-009](D-009-build-tooling.md), [D-010](D-010-determinism.md), [D-001](D-001-smith-infection-decorator.md), [D-024](D-024-attention-lod.md).
