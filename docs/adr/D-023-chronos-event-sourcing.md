---
title: "D-023 — Chronos proper: event-sourced state, reload as replay"
status: accepted
date: 2026-08-10
decision-makers: gokselozgur5 (owner), the resident machine (pair)
consulted: thread #27
informed: phase tracker #24
---

# D-023 — Chronos proper: event-sourced state, reload as replay

*In the context of the Vision's L1 layer, facing the gap between logging-as-observability and log-as-truth, we park full event sourcing (state = fold of events, snapshots, reload = replay) until after v3.0 ships the simple reload, to achieve focus now and a proven arc first, accepting that today objects remain the truth.*

## Context and Problem Statement

The digest chain already fingerprints reality per tick. The full inversion — events as the only truth — would make the Architect's reload a literal replay from snapshot. Powerful, and premature while the engine does not exist.

## Decision Drivers

* Reload-as-replay is the most honest possible reload
* Event sourcing before an engine exists is architecture cosplay
* The digest chain (D-020) builds the muscle this needs

## Considered Options

* Event-sourced core (parked)
* Objects as state + instruments (status quo)

## Decision Outcome

Accepted by the owner's verdict, 2026-08-11, in session — *"hepsine agreed kanka barajı aç"* — all six gates in one breath; recorded in the gate thread.

Parked until after v3.0; the revisit trigger is the simple reload shipping and hurting. Thread #27 holds the idea.

### Consequences

* Good, because v1-v3 stay achievable
* Bad, because Some v3 reload code is known-disposable (acceptable, documented)

### Confirmation

Not applicable while parked; on unparking, the first milestone is replaying a recorded v3 arc to identical digests.

## Pros and Cons of the Options

### Event-sourced core (parked)

* Good, because time travel, perfect audits, reload as replay
* Good, because optimization equivalence proofs become replay proofs
* Neutral, because a serious rewrite of World internals
* Bad, because costs are real: event schema design, fold performance, snapshot format
### Objects as state + instruments (status quo)

* Good, because simple, fast, shipping
* Bad, because reload resets by hand; history is derived, not primary

## More Information

Related: [D-010](D-010-determinism.md), [D-020](D-020-observability-contract.md). Vision: L1 Chronos.

**Errata (2026-08-15, #1128 — unparked, shipped, and three flags deep):** The parking condition is spent. `--chronos PATH` records genesis and inputs as JSONL, `--replay PATH` folds a recording back into a run, and `--audit PATH` verdicts a recording's internal consistency; `ChronosLog`, `ChronosLine` and `ReplayHarness` are on `main`, and `World` writes to them. The outcome and the Confirmation's "Not applicable while parked" are kept as written under D-029 — this errata records that the world moved past them and does not edit them.

*The Confirmation that replaces "not applicable":*

```
java -cp out matrix.Main --headless --ticks 300 --seed 42 --chronos /tmp/c.jsonl
java -cp out matrix.Main --audit /tmp/c.jsonl
  → AUDIT records=2 commands=0 flushes=1 boundaries=0 seals=0 births=0
    AUDIT OK records=2 seals_paired=0
```

The record's own first milestone — replaying a recorded arc to identical digests — is `--replay`'s contract, and it is exercised where the digest is: the replay path re-runs from the recording's genesis rather than trusting the recording.

Referenced by: [D-010](D-010-determinism.md), [D-020](D-020-observability-contract.md), [D-024](D-024-attention-lod.md), [D-038](D-038-season-two.md), [D-046](D-046-cypher-protocol.md), [D-050](D-050-live-events.md), [D-058](D-058-spec-shelf.md), [D-067](D-067-epistemic-revision.md).
