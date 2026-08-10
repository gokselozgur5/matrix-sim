---
title: "D-006 — Arc tuning constants live in Config and are tuned by METRIC feel"
status: proposed
date: 2026-08-10
decision-makers: gokselozgur5 (owner), the resident machine (pair)
consulted: thread #17
informed: phase tracker #23
---

# D-006 — Arc tuning constants live in Config and are tuned by METRIC feel

*In the context of pacing the film arc, facing numbers that cannot be derived (only felt), we lean toward named constants in Config tuned against METRIC output and against config files or CLI knobs, to achieve reproducible builds where every run of a commit behaves identically, accepting that retuning requires a commit.*

## Context and Problem Statement

Overflow threshold 62%, Smith fork delay 350 ticks, peace duration 900 ticks: these numbers shape the story. They are aesthetic choices with mechanical consequences.

## Decision Drivers

* Reproducibility: a commit hash fully determines behavior
* Tuning workflow: change, run, read METRIC, feel the arc
* No hidden state: runtime knobs would fork reality outside version control

## Considered Options

* Constants in Config, changed by commit
* External config file
* CLI flags per constant

## Decision Outcome

Proposed option: "constants in Config", because reality should be versioned. Final call in thread #17 (v3.0 gate, where the numbers get felt).

### Consequences

* Good, because DoD commands stay short and canonical
* Bad, because Experiment loops include a compile step (acceptably fast with plain javac)

### Confirmation

Config is the only file containing these numbers (grep for the literals elsewhere fails); the v3.0 arc DoD passes with the committed values.

## Pros and Cons of the Options

### Constants in Config, changed by commit

* Good, because every digest chain maps to exactly one tuning
* Good, because tuning history is git history
* Neutral, because retuning is a commit, not a flag
### External config file

* Good, because tune without recompiling
* Bad, because the same binary produces different realities; digest comparisons need config provenance
### CLI flags per constant

* Good, because fast experiments
* Bad, because flag soup; DoD commands stop being canonical

## More Information

Related: [D-009](D-009-build-tooling.md), [D-027](D-027-performance-budgets.md). Crown: #47.
