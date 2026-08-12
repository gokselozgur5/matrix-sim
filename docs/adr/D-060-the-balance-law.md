---
title: "D-060 — The balance law: four quarters, and the meter that proves it"
status: proposed
date: 2026-08-12
decision-makers: gokselozgur5 (owner), the resident machine (pair)
consulted: thread #781
informed: every crew, every day of the year
---

# D-060 — The balance law: four quarters, and the meter that proves it

*In the context of a year measured in daily contributions, facing a first day that produced 603 issues and zero reviews, we lean toward a four-quarter balance — commits, issues, pull requests and reviews each holding ~25% of a day — enforced by a meter that reads the same API the profile graph reads, and against unmeasured intent or a volume-only target, to achieve days that plan, build, ship and doubt in equal measure, accepting that the meter will call us lagging on days we felt productive.*

## Context and Problem Statement

The Architect's rule, in session (2026-08-12): the four kinds of contribution GitHub counts should each hold a quarter of the day. It began as a trio (D-039's "commits, issues and PRs grow in proportion") and grew a fourth leg when reviews entered the flow — and the first measurement showed why the rule was needed: **2026-08-11 ran 863‰ issues and 0‰ reviews**, a day that planned brilliantly and doubted nothing. The reasoning is not arithmetic hygiene:

* a day of only PRs is a day nobody planned;
* a day of only issues is a day nobody shipped;
* a day without reviews is a day nothing was doubted;
* a day without commits is a day nothing was built.

The balance is a shape of work, and the meter exists because in this house a rule that cannot be measured is a mood (D-027's founding line, reapplied to process).

## Decision Drivers

* The four legs are the four halves of a working day: plan, build, ship, doubt
* The measurement must come from the same source the profile graph uses, so it cannot flatter us
* Reviews must be REAL reviews — the rule creates pressure toward the ceremony, and the answer to that pressure is adversarial passes, not rubber stamps
* Deficits should be actionable: the meter names the lagging leg and the count that clears it
* A second identity approving one's own work is refused explicitly (see Consequences)

## Considered Options

* Four quarters with a meter (`tools/balance.sh`), reviews earned by real passes
* The old trio (commits/issues/PRs), reviews uncounted
* Volume only: hit the daily total however it lands

## Decision Outcome

Leaning: four quarters with the meter. `tools/balance.sh [YYYY-MM-DD]` prints the day's mix in per mille against a 250‰ target per leg, names the lagging leg, and reports how many artifacts of each kind close the gap — solving for the larger day that adding them creates, rather than the day already behind us.

**Explicitly refused, and recorded so it stays refused:** using the owner's second GitHub account to approve the first account's pull requests. It fails on three counts — it credits the reviewing account, so it does not even serve the graph it would be gaming; it drags a work identity into a personal repository's history; and an approval badge is the one artifact an outside reader takes as evidence that a second person looked. In a repository whose whole constitution is *evidence or it didn't happen*, that badge would be the only false line in it. Self-review COMMENTS are legitimate and count; the missing green stamp is a cost we accept.

### Consequences

* Good, because the day's shape becomes visible and correctable while the day is still running
* Good, because the review leg forces the adversarial passes to become routine rather than heroic
* Bad, because the meter will report LAGGING on days that felt excellent — which is the point, and will still sting

### Confirmation

`tools/balance.sh` prints a verdict line for any day, and its first two readings are recorded here: 2026-08-11 `verdict=LAGGING:review` (0‰ reviews against 863‰ issues) and 2026-08-12 `verdict=LAGGING:issue` at the time of writing, both from the live API.

## Pros and Cons of the Options

### Four quarters with a meter

* Good, because balance stops being a promise and becomes a reading
* Good, because the deficit is actionable mid-day
* Bad, because a quarter is a blunt target — some days genuinely belong to one leg (a phase-boundary skeptic pass is a review day by nature)

### The old trio

* Good, because it is already law (D-039) and needs nothing
* Bad, because it counts the day's doubt as free, and yesterday proved it is not

### Volume only

* Good, because simplest to chase
* Bad, because volume without shape is the hollow-row failure the constitution bans

## More Information

Related: [D-039](D-039-unit-pr-granularity.md) (the trio this extends), [D-054](D-054-the-year.md) (the year this shapes), [D-030](D-030-agent-operating-model.md) (the crews whose reviews fill the fourth leg), [D-027](D-027-performance-budgets.md) (the falsifiability principle borrowed here). Thread: #781. Principle: Dev14.
