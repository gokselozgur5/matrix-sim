---
title: "D-022 — The acceptance loop and anomaly ledger replace the flat counter"
status: accepted
date: 2026-08-10
decision-makers: gokselozgur5 (owner), the resident machine (pair)
consulted: thread #18
informed: phase tracker #23
---

# D-022 — The acceptance loop and anomaly ledger replace the flat counter

*In the context of The One's birth, facing a draft where anomaly is a hand-tuned counter, we lean toward a per-link acceptance loop whose resistance residue accrues in a global ledger and against scripted increments, to achieve an emergent plot driven by bookkeeping, accepting more per-link computation.*

## Context and Problem Statement

The Vision's second principle: the dream is negotiated. Every link proposes frames; minds accept or resist; the residue is systemic debt. When the ledger overflows, The One is not summoned — The One is OWED.

## Decision Drivers

* The plot must emerge from mechanics, not from anomaly += 4
* The Architect's own line (the sum of the remainder) should be literally true in code
* Red-pill awakenings should shift resistance patterns measurably
* Tick cost per link must stay bounded (D-027)

## Considered Options

* Acceptance loop per link + global AnomalyLedger
* Flat counter (draft status quo)

## Decision Outcome

Chosen option: "the ledger", because the repo's thesis is that the plot falls out of the architecture — this is the decision where that either becomes true or stays a slogan. Accepted by the owner's verdict, 2026-08-11 (thread #18).

### Consequences

* Good, because The One's birth tick becomes derivable from logs alone
* Bad, because The resistance model needs taste to avoid pseudo-science knobs (kept minimal, reviewed together)

### Confirmation

In a v3.0 run the birth event cites the ledger total crossing its bound; deleting the old counter is part of the PR; METRIC includes a ledger column.

## Pros and Cons of the Options

### Acceptance loop per link + global AnomalyLedger

* Good, because The One's birth becomes an audit event: the ledger crossed its bound
* Good, because awakenings, patches and déjà vu all get honest mechanical meaning
* Good, because METRIC can chart systemic resistance over time
* Neutral, because a resistance model to design (simple first: per-pill base rates + event spikes)
* Bad, because per-link work every tick; must be O(1) per link
### Flat counter (draft status quo)

* Good, because works today; trivially cheap
* Bad, because the central event of the story is a scripted constant — the one thing this repo said it would not do

## More Information

Related: [D-021](D-021-perception-feed.md), [D-006](D-006-arc-tuning.md). Crowns: #52, #53. Principle: Vision L4.

**Errata (2026-08-11, deferred skeptic LOW made law):** Post-birth accrual is intentional, and the record now says so. The One's existence does not forgive residue — it only answers the bound crossing that already happened; the ledger keeps counting through the war years. Measured shape at seed 42: birth at 30,227 → ~125k by overflow t=4284 (unbounded by design) → treaty reset to 0 → 30,297 rebuilds by t=5249 → the second birth. The cycle's period is an emergent property of the population mix (seed 7 runs the same loop at 3334/4299), not a constant anywhere in Config. Explicit non-goal, recorded to close the question: **no cap on the ledger** — a capped debt would make overflow timing depend on the cap instead of the city, and the whole point of D-022 is that the city, not a constant, decides when the system owes the world an anomaly. The déjà-vu spike (`DEJA_RESIDUE_SPIKE=250`, ops console only) is the record's "patches gain mechanical meaning" promise, kept in the v3 fix round. Verification: `probes/LedgerMirror` holds every delta to the open-link residue mirror — `LEDGER_ANOMALIES=0` at seeds 42 and 7.

Referenced by: [D-033](D-033-self-substantiation.md).
