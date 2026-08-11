---
title: "D-020 — Observability contract: event log + METRIC lines + DIGEST chain"
status: accepted
date: 2026-08-10
decision-makers: gokselozgur5 (owner), the resident machine (pair)
consulted: thread —
informed: phase tracker —
---

# D-020 — Observability contract: event log + METRIC lines + DIGEST chain

*In the context of a headless system that must be understood, tested and trusted, facing the absence of any visual feedback, we decided for a three-instrument contract (append-only event log, periodic METRIC lines, canonical DIGEST chain) and against ad-hoc logging, to achieve machine-checkable DoDs and diffable reality, accepting instrumentation as a permanent tax on every feature.*

## Context and Problem Statement

With no screen (D-019), the system's face is its instrumentation. Tests grep it, reviews diff it, and the digest chain is the arbiter of whether two realities are the same reality.

## Decision Drivers

* Every DoD must be a command over system output
* Divergence must be locatable to a tick (digest chain granularity)
* The narrative (event log) and the numbers (METRIC) serve different readers
* Performance evidence needs a home (PERF, added by D-027)

## Considered Options

* Three instruments with fixed grammars
* Ad-hoc logging as needed
* Full event sourcing now

## Decision Outcome

Chosen option: "the three instruments", because a headless world without a contract for truth is just a process burning CPU. Accepted 2026-08-10.

### Consequences

* Good, because The v1 DoD (identical digest chains) tests the entire engine in one diff
* Bad, because Canonical serialization for the digest is real design work (World crown)

### Confirmation

v1.0 ships EventLog, MetricsCollector, DigestCalculator; the DoD commands in ROADMAP execute exactly as written; digest divergence between two intentionally different runs localizes to the first differing tick.

## Pros and Cons of the Options

### Three instruments with fixed grammars

* Good, because DoDs become greps and diffs — arguments end
* Good, because the digest chain turns determinism from a hope into a check
* Good, because operators, tests and future AIs read the same truth
* Neutral, because every feature ships with its instrumentation
* Bad, because grammar changes are breaking changes for tests (versioned carefully)
### Ad-hoc logging as needed

* Good, because no upfront design
* Bad, because every test invents its own parsing; the log becomes a junk drawer
### Full event sourcing now

* Good, because the strongest possible observability
* Bad, because a v4-sized architecture (D-023) bought before the engine exists

## More Information

Related: [D-010](D-010-determinism.md), [D-019](D-019-backend-only.md), [D-027](D-027-performance-budgets.md), [D-023](D-023-chronos-event-sourcing.md). Principle: A4. Crowns: #79-#82.

Referenced by: [D-007](D-007-terminal-ui.md), [D-010](D-010-determinism.md), [D-019](D-019-backend-only.md), [D-023](D-023-chronos-event-sourcing.md), [D-027](D-027-performance-budgets.md), [D-030](D-030-agent-operating-model.md).
