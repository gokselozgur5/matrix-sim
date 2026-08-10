# D-026 — Implementation language: Java 17

Status: 🟡 proposed · Phase gate: v1.0 · Thread: #92

## Context
C++ was weighed seriously — it is the owner's daily driver and RAII ownership maps beautifully to Smith.

## Decision
Proposed: Java 17 — JVM semantics make the digest DoD nearly free; GC matches the Decorator restore; sealed types fit the taxonomy.

## Consequences
Revisit only if D-024 scale ever demands a native hot path.
