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

Referenced by: [D-007](D-007-terminal-ui.md), [D-010](D-010-determinism.md), [D-019](D-019-backend-only.md), [D-023](D-023-chronos-event-sourcing.md), [D-027](D-027-performance-budgets.md), [D-030](D-030-agent-operating-model.md), [D-068](D-068-agent-run-identity.md).

**Errata (2026-08-13, #836):** The three grammars were fixed; the charset they are written in was not, and for eleven versions it came from whatever locale the box exported. D-026's errata pinned `javac -encoding UTF-8` so the *source* is read the same everywhere — this record needed the other half, because the JVM resolves its default charset the same way at *run* time. On a box with no locale exported (JDK 17 reports `file.encoding = ANSI_X3.4-1968` — D-027's Debian reference box, and any shell that has not exported a locale) every non-ASCII character printed by a `main` was silently replaced by `?`: measured on pristine main under `LC_ALL=C`, `--help` printed `5,269 entities ? the D-027 retargeted row's scale`, `--bench` printed `digests untouched ? bench runs quiet`, `DrawMeter` printed `the world holds its breath ? zero draws`, and `DistrictNeutral`'s catalog lost every `·` separator. Nothing was wrong on a UTF-8 box and nothing was ever wrong with the digest — the seal is over ints, never text — but a line quoted in a PR was not the line the next box printed, which is exactly the property the v1 skeptic round bought when it killed the `%n`/locale byte instability. **Charset ownership is now part of the contract:** `matrix.Streams.utf8()` rebuilds `System.out` and `System.err` on UTF-8 as the first statement of the daemon's `main` and of all 21 probe `main`s. `Simulation` was never exposed — it constructs its own `PrintStream` with an explicit UTF-8 — so METRIC, DIGEST and event lines were always locale-proof; what was exposed was everything a `main` prints. The lock is byte-level, not a promise: CI captures `--help`, a stderr refusal and `DistrictNeutral` under UTF-8 and under `LC_ALL=C` and refuses any difference, and scans every probe `main` for the pin so the next probe cannot forget it. It forces the hostile locale rather than trusting the runner's, because the hosted lane exports a UTF-8 one and is therefore the wrong witness for its own bug — measured, not assumed: pre-fix `main` printed `digests untouched — bench runs quiet` on the runner with the em-dash bytes intact.
