# D-009 — Build: plain javac vs Gradle

Status: 🟡 proposed · Phase gate: v1.0 · Thread: #5

## Context
Zero dependencies is a stated feature; CI and JUnit will eventually want tooling.

## Decision
Proposed: plain javac until the v4.0 CI decision forces the question.

## Consequences
No build files to maintain now; a migration cost accepted later if tests demand it.
