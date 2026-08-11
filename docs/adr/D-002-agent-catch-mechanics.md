---
title: "D-002 — Agent catch mechanics: replug ratio and lethality"
status: accepted
date: 2026-08-10
decision-makers: gokselozgur5 (owner), the resident machine (pair)
consulted: thread #2
informed: phase tracker #20
---

# D-002 — Agent catch mechanics: replug ratio and lethality

*In the context of agents suppressing rogue clients, facing the tension between population stability and real stakes, we lean toward 90% replug / 10% terminate and against pure replug or pure kill, to achieve drama with a survivable ecosystem, accepting a tunable magic ratio in Config.*

## Context and Problem Statement

Agents must do something when they catch a red-pill avatar. Pure catch-and-release makes agents toothless; pure lethality empties the simulation and wastes the pod-flush mechanic.

## Decision Drivers

* The chase must matter (METRIC red count should visibly oscillate)
* Death must exist: the pod-flush path (brain flatline) needs real traffic
* Long runs must not collapse the population
* Numbers belong in Config (D-006 discipline)

## Considered Options

* 90% replug / 10% terminate
* Always replug
* Always terminate
* Escalation model (warnings, then lethal force)

## Decision Outcome

Chosen option: "90/10", because it keeps every code path alive — including the one that kills. Accepted by the owner's verdict, 2026-08-10 (thread #2).

### Consequences

* Good, because The death rule (avatar dies → brain dies → pod flushes) gets continuous, natural test traffic
* Bad, because One more tunable to argue about at D-006 time

### Confirmation

A 2,000-tick v1 run shows: red count oscillating in METRIC lines, at least one pod-flush event in the log, population above zero at the end.

## Pros and Cons of the Options

### 90% replug / 10% terminate

* Good, because population survives long runs
* Good, because pod flush fires often enough to be tested
* Good, because stakes are real but not apocalyptic
* Neutral, because the ratio is a feel-number, tuned by METRIC observation
### Always replug

* Good, because maximum stability
* Bad, because death code paths never execute; the death rule rots untested
### Always terminate

* Good, because maximum drama
* Bad, because red population hits zero; the anomaly engine starves
### Escalation model (warnings, then lethal force)

* Good, because richest fiction
* Neutral, because more state per agent
* Bad, because v1 scope creep; nothing in the v1 DoD needs it

## More Information

Related: [D-006](D-006-arc-tuning.md), [D-013](D-013-neurallink-bridge.md). Crown: #65.

Accepted with an evolution path: once the D-004 place graph matures, the flat ratio is intended to evolve into the emergent exit-race mechanic (capture decided by route geometry, not dice); a future decision will formalize it.

Referenced by: [D-004](D-004-field-model.md).
