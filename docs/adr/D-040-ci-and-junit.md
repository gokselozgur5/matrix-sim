---
title: "D-040 — CI and JUnit: reopening D-009 now that the question is forced"
status: proposed
date: 2026-08-11
decision-makers: gokselozgur5 (owner), the resident machine (pair)
consulted: thread #137
informed: milestone v4.5
---

# D-040 — CI and JUnit: reopening D-009 now that the question is forced

*In the context of Season Two's unit-PR flood, facing lock enforcement that currently lives in one operator's discipline, we lean toward GitHub Actions running the existing zero-dependency instruments (selftest, bench, probes) on every PR, and against adopting JUnit in the same motion, to achieve enforced locks without surrendering the zero-dependency principle, accepting that assertion ergonomics stay homegrown until the bench proves insufficient.*

## Context and Problem Statement

D-009 ruled: plain `javac` until CI forces the question. The question is now forced from two sides. First, D-039's delivery model produces many small PRs per day — each promising the light locks (compile · `--selftest` · digest), enforced today only by the operator remembering to run them. Second, the bench grew real teeth this season: `--selftest` reaches the finale, `--bench` verdicts the budget table by exit code, and eight probes answer invariant questions with greppable verdicts (`LEDGER_ANOMALIES=0`, `CAP_BREACHES=0`, `BEATS_IN_ORDER`). Everything a CI run needs already exists as a command with an exit code. What does not exist is the machine that runs them on every PR without being asked.

The build-unit issue (#137) says "JUnit + CI". This record deliberately splits that phrase: CI is one decision, JUnit is another, and they are separable.

## Decision Drivers

* Locks must not depend on operator discipline at Season Two's PR volume
* D-009's zero-dependency principle survived three phases and is load-bearing (Dev7)
* The instruments already exist and speak exit codes — CI can be a runner, not a framework
* A red X on a PR is evidence the Architect can see without reading code (D-037)
* JUnit's real value (assertion ergonomics, granular reports) is unproven need here — probes have answered every question so far

## Considered Options

* **CI as a runner:** GitHub Actions workflow per PR — compile, `--selftest` (2,000 and 6,000), `--bench`, probe sweep. No new dependencies; Actions is infrastructure like `.github/` templates, not a build tool.
* **CI + JUnit together:** the same workflow plus JUnit 5 as the first external dependency, migrating probe assertions into test classes.
* **Status quo:** the operator runs locks by hand, forever.

## Decision Outcome

Proposed: **CI as a runner** now; JUnit deferred to its own future record with a named trigger — the day a probe needs assertion granularity that greppable verdict lines cannot express, that probe's PR carries the JUnit proposal. This is D-009's own pattern reapplied: adopt the machine when the question is forced, not before.

### Consequences

* Good, because every PR carries enforced locks — the trio stays honest at flood volume
* Good, because zero dependencies survives another season; the daemon still builds with `javac` alone
* Good, because probe verdicts become CI lines the Architect reads without opening a diff
* Bad, because homegrown assertions stay homegrown — if probe complexity grows, we may pay the migration later that JUnit-now would pre-pay
* Neutral, because Actions minutes are free at this repo's scale

### Confirmation

The workflow file exists, runs on every PR to `main`, and a deliberately broken digest (one-off test branch) turns the check red before any human looks. The first season-two unit PR merges behind a green check.

## Pros and Cons of the Options

### CI as a runner

* Good, because the entire lock kit is reused as-is — selftest, bench, probes, exit codes
* Good, because rollback is deleting one YAML file
* Neutral, because matrix-testing (multiple JDKs/OSes) can come later on the same skeleton — the cross-platform fate claim gains a standing witness
* Bad, because probe output parsing in CI is string matching, which JUnit reports would structure

### CI + JUnit together

* Good, because industry-standard assertions and per-test reporting from day one
* Bad, because the first external dependency lands bundled with an infrastructure change — two reopenings in one motion, harder to reason about and harder to revert
* Bad, because nothing today needs it: every invariant this project has cared about fit a greppable verdict line

### Status quo

* Good, because zero new machinery
* Bad, because lock enforcement stays a memory exercise precisely when PR volume multiplies — the failure mode is silent and cumulative

## More Information

Related: [D-009](D-009-build-tooling.md) (the record this reopens, by its own sunset clause), [D-027](D-027-performance-budgets.md) (bench as the speed lock), [D-030](D-030-agent-operating-model.md) (the bench and the crew), [D-039](D-039-unit-pr-granularity.md) (the flood that forces the question). Thread: #137. Principle: Dev7.
