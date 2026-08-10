---
title: "D-021 — The perception feed is the system's true output"
status: accepted
date: 2026-08-10
decision-makers: gokselozgur5 (owner), the resident machine (pair)
consulted: thread #9
informed: phase tracker #20
---

# D-021 — The perception feed is the system's true output

*In the context of a Matrix whose only honest product is the dream delivered per brain, facing the temptation to treat logs as the output, we lean toward defining a minimal PerceptionFrame and a --follow mode in v1 and against deferring the concept entirely, to achieve an architecture whose output socket exists from day one, accepting that full streaming waits for v4.0.*

## Context and Problem Statement

Logs and metrics are OUR instruments; the Matrix's actual deliverable is sensory frames over the NeuralLink. If v1 does not fix the frame shape, every later feature will assume the instruments are the product.

## Decision Drivers

* Fiction honesty: the output is the dream
* A stable frame contract early is cheap; retrofitting one later is not
* An external client jack-in (v4.0) needs a wire format to exist

## Considered Options

* Minimal PerceptionFrame + --follow <name> in v1
* Rich typed senses from day one
* Defer everything to v4.0

## Decision Outcome

Chosen option: "minimal frame now, streaming later", because sockets are cheap on day one and expensive on day one thousand. Accepted by the owner's verdict, 2026-08-10 (thread #9).

### Consequences

* Good, because The NeuralLink crown gets a concrete deliverable in v1
* Bad, because Frame evolution must be tracked (a future ADR when it grows senses)

### Confirmation

--follow thomas on a v1 run emits at least one frame per 100 ticks with tick, nearby summary and events-in-earshot; the frame shape is documented in crown #41.

## Pros and Cons of the Options

### Minimal PerceptionFrame + --follow <name> in v1

* Good, because the output socket exists and is testable from the first phase
* Good, because --follow makes one brain's dream a readable artifact
* Good, because v4 streaming becomes an implementation, not a redesign
* Neutral, because one more record and one flag in v1
* Bad, because the minimal shape will grow; versioning discipline needed
### Rich typed senses from day one

* Good, because closest to the fiction
* Bad, because v1 scope explosion for fidelity nobody consumes yet
### Defer everything to v4.0

* Good, because leanest v1
* Bad, because the instruments quietly become the product; the fiction drifts

## More Information

Related: [D-013](D-013-neurallink-bridge.md), [D-019](D-019-backend-only.md). Crowns: #41, #51. Full streaming: tracker #24.

Accepted with a spark: perception frames stream as JSONL, so a dream is one jq away.
