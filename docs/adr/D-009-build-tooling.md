---
title: "D-009 — Build tooling: plain javac until CI forces the question"
status: proposed
date: 2026-08-10
decision-makers: gokselozgur5 (owner), the resident machine (pair)
consulted: thread #5
informed: phase tracker #20
---

# D-009 — Build tooling: plain javac until CI forces the question

*In the context of building a zero-dependency JVM project, facing the eventual need for JUnit and CI, we lean toward plain javac now and against adopting Gradle upfront, to achieve a repo where the toolchain is the JDK, accepting a migration cost when v4.0 CI lands.*

## Context and Problem Statement

The whole engine is standard library by design. The only build need today is compile-and-run; the only foreseeable need is test execution in CI at v4.0.

## Decision Drivers

* Zero dependencies as a stated feature
* Onboarding: clone, javac, run — nothing to install
* Honest deferral: adopt tools when a need exists, not before

## Considered Options

* Plain javac + documented commands
* Gradle from day one
* Maven from day one

## Decision Outcome

Proposed option: "plain javac", because a build tool with nothing to build is scaffolding around an empty lot. Final call in thread #5 (v1.0 gate); explicitly reopened by the v4.0 CI item.

### Consequences

* Good, because The quickstart never lies
* Bad, because The v4.0 migration is a real, budgeted cost

### Confirmation

README commands compile the repo on a clean JDK-17 box (verified in v1.0 DoD); no build files exist in the tree.

## Pros and Cons of the Options

### Plain javac + documented commands

* Good, because no build files to maintain or update
* Good, because the README quickstart IS the build system
* Neutral, because a find+javac one-liner instead of an IDE button
* Bad, because no dependency or test orchestration when that day comes
### Gradle from day one

* Good, because JUnit and CI plug in instantly later
* Neutral, because wrapper scripts, versions, caches — surface area today for value tomorrow
* Bad, because contradicts the zero-dep story while there is nothing to build
### Maven from day one

* Good, because convention-heavy and stable
* Bad, because same as Gradle with more XML

## More Information

Related: [D-026](D-026-language-java17.md), [D-027](D-027-performance-budgets.md). Reopens at: v4.0 CI (tracker #24).
