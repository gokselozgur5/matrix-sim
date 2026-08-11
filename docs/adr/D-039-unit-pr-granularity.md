---
title: "D-039 — Delivery granularity: unit PRs, proportional locks"
status: accepted
date: 2026-08-11
decision-makers: gokselozgur5 (the Architect), the resident machine (the Oracle)
consulted: —
informed: PRINCIPLES.md, season-two trackers
---

# D-039 — Delivery granularity: unit PRs, proportional locks

*In the context of season one landing whole phases as single mega-PRs (three merged PRs for a hundred files of work), facing the owner's observation that both the contribution record and reviewability suffer, we decided for unit-sized delivery — one build-unit issue closed by exactly one small PR via closing keywords, with locks applied proportionally — and against both mega-PRs and lockless micro-commits, to achieve a granular, linked, honest delivery history, accepting more PR ceremony per week of work.*

## Context and Problem Statement

Season one's phase-PR model optimized for narrative (one PR = one film) at the cost of granularity: three merged PRs, bulk-closed issues, a contribution graph that hides a hundred files of verified work. The owner's mandate: grind CP with substance — many small, linked, evidenced merges.

## Decision Drivers

* Each merged PR should be a reviewable unit closing its issue via keywords (the CP chain)
* The five locks must not drown small changes in phase-sized ceremony
* Skeptic depth belongs where risk accumulates: phase boundaries, not every fifty-line diff
* The build-unit issues must be real work, never confetti

## Considered Options

* Unit PRs with proportional locks
* Keep phase mega-PRs (status quo)
* Micro-commits straight to main without locks

## Decision Outcome

Chosen option: "unit PRs, proportional locks", commissioned by the owner, 2026-08-11. The protocol:

- **Work arrives as build-unit issues** (label `build-unit`): one deliverable, one DoD line, its gate named.
- **One unit = one PR**, body carries `Closes #N`; crowns close by the PR that lands their class.
- **Per-PR locks (always):** compile clean · `--selftest` green · double-run digest diff (bit-identical unless the unit's DoD says the digest legitimately changes, in which case the PR says so and why).
- **Phase-boundary locks (accumulated):** full adversarial skeptic pass over the milestone's merged units + the theory brief + the tagged release. REFUTED findings open fix-units.
- Season one's merged history stands as-is; the model applies from v3.0's merge onward.

### Consequences

* Good, because the delivery record becomes granular, linked and honest — and each PR is actually reviewable
* Good, because lock cost scales with risk instead of drowning small diffs
* Bad, because phase-boundary skeptic passes must diligently cover accumulated units (the orchestrator's standing duty)

### Confirmation

Season-two merges show one-unit-one-PR with closing keywords; per-PR lock evidence in each PR body; a full skeptic pass recorded at each milestone close before its release is tagged.

## Pros and Cons of the Options

### Unit PRs with proportional locks

* Good, because granularity serves both the graph and the reviewer
* Neutral, because phase narrative moves from PR bodies to milestone/release notes
* Bad, because more ceremony per unit (worth it at season-two scale)

### Phase mega-PRs

* Good, because one PR tells a whole film
* Bad, because three merged PRs for a hundred files is a rounding error of a history

### Lockless micro-commits

* Good, because maximum apparent velocity
* Bad, because velocity without locks is how digests drift and repos rot

## More Information

Amends the delivery clauses of [D-030](D-030-agent-operating-model.md)/[D-037](D-037-theory-practice-split.md) (locks now two-tier). Companion to [D-038](D-038-season-two.md). The 29 inaugural build-units were cut from the crew dossiers on the same day.
