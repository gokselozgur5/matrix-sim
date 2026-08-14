---
title: "D-010 — Determinism: one seeded Rng, no wall clock, no unordered iteration"
status: accepted
date: 2026-08-10
decision-makers: gokselozgur5 (owner), the resident machine (pair)
consulted: thread —
informed: phase tracker —
---

# D-010 — Determinism: one seeded Rng, no wall clock, no unordered iteration

*In the context of a simulation whose tests, replays and reviews all diff two runs, facing the many ways the JVM can smuggle nondeterminism in, we decided for a single seeded Rng and a ban on wall-clock time and unordered iteration in domain logic, and against per-call convenience randomness, to achieve bit-identical runs from equal seeds, accepting ceremony around every random draw.*

## Context and Problem Statement

Replay is the project's oxygen: the DoDs diff digest chains, optimization PRs must prove behavioral equality, and the reload mechanic will eventually be a literal replay (D-023). One stray Math.random() poisons all of it.

## Decision Drivers

* Same seed, same film — bit for bit (the constitutional sentence)
* Reviewability: any divergence must be attributable to a code change
* Future event sourcing (D-023) requires it retroactively

## Considered Options

* Single seeded Rng owned by World; bans enforced by review
* Free use of java.util.Random / Math.random
* Seeded substreams per entity

## Decision Outcome

Chosen option: "single seeded Rng with bans", because everything this repo promises is downstream of replayability. Accepted 2026-08-09.

### Consequences

* Good, because The double-run diff is a one-line test of the entire engine
* Bad, because A future parallel tick would need a substream design (acceptable, deferred)

### Confirmation

grep finds no banned APIs in src; the v1.0 DoD runs the engine twice with seed 42 and diffs the digest chains to empty.

**Confirmation clause (2026-08-13, #837) — the two tiers of a borrowed number.** The birth-seed ruling's hygiene clause (#212) reads *the die derives from our own digest mixing, never from anything JVM-shaped*, and four call sites read `String.hashCode`, all four reaching state — one of them, `World.digestEntity`, inside the canonical chain's own preimage. Rather than treat that as a violation or as an exception, this record states the distinction it turns on, because the two are not the same thing:

| tier | what it is | examples | status |
|---|---|---|---|
| **specification-shaped** | an algorithm a published spec fixes; reproducible outside the JVM by anyone with the spec | `String.hashCode` (JLS), UTF-8 byte order, int wraparound | **permitted in state, and pinned** |
| **implementation-shaped** | a value the JVM is free to choose | `Object.hashCode`, identity hashes, `HashMap`/`HashSet` iteration order, default charset, `enum.hashCode` | **refused in state** |

`String.hashCode` is tier one: `s[0]*31^(n-1) + …` is normative, not incidental, which makes it exactly as portable as the byte order this record already leans on. The clause was written against tier two, and tier two appears nowhere in `src/` — `grep -rn 'hashCode()\|HashMap\|HashSet\|identityHashCode' src` returns the four `String.hashCode` sites and one `LinkedHashSet`, whose order is insertion and therefore ours.

Tier one is **permitted and checked, never permitted and trusted.** `matrix.character.Sheets` states the reason better than this record can: FNV-1a over bytes is stable *because it is arithmetic we define*, while `String.hashCode` is stable *because a specification says so*. A repository whose method is that an unmeasurable rule is a mood cannot leave a borrowed specification as the only thing between it and a different seal. So `probes/SealHygiene` pins every value the seal actually takes from the JLS — twelve `Species` ids, and the birth key and the name hash the two doors read over six canon names — and is judged on the bench. It pins the borrowed value and prints the tuned one (#1016): a door's threshold is that value plus `KID_BASE` or `PETITION_BASE`, which this repository sets, so pinning the threshold made a lawful retune of a constant report as a JLS deviation. Both thresholds still print on every row; neither is judged.

The pin's larger catch is not a deviant JVM, which is unlikely; it is a **rename**, which is not. Editing `"black cat"` to `"stray cat"` in the Bestiary reads as a caption change and moves the canonical digest from `421d7263…e0e3ce10` to `ec0a1b61…686a9f5263` in silence. Measured, 2026-08-13; `SealHygiene` reports `SEAL_HYGIENE_BROKEN` on that tree and names both halves.

**What #837 deliberately did not do.** Retiring the sites — the species *ordinal*, or FNV-1a of the id — buys independence from a specification this repository does not control, and costs the canonical chain its identity: a new sha, a reseal of the NEUTRAL lane's fixture (#528), a release note. That is a declared digest move, and the pre-v6 chain is under seal as the control group's fixture while #871 is open; #884 exists because the last such move landed beside an instrument being built for it. The pin is what makes deferring it safe rather than merely convenient, and it is the thing that would have to be re-measured, not re-argued, if the retirement ever lands.

## Pros and Cons of the Options

### Single seeded Rng owned by World; bans enforced by review

* Good, because one stream, one seed, total reproducibility
* Good, because violations are trivially greppable (Random, System.currentTimeMillis, nanoTime)
* Neutral, because all randomness flows through one object (also an audit feature)
* Bad, because contributors must learn the discipline once
### Free use of java.util.Random / Math.random

* Good, because zero ceremony
* Bad, because reproducibility dies quietly, discovered at the worst moment
### Seeded substreams per entity

* Good, because parallel-friendly someday
* Neutral, because more machinery than v1 needs
* Bad, because premature; can be added under the same constitution later

## More Information

Related: [D-020](D-020-observability-contract.md), [D-023](D-023-chronos-event-sourcing.md), [D-027](D-027-performance-budgets.md). Principle: A3. Crown: #46.

Referenced by: [D-004](D-004-field-model.md), [D-005](D-005-world-mutation.md), [D-020](D-020-observability-contract.md), [D-023](D-023-chronos-event-sourcing.md), [D-026](D-026-language-java17.md), [D-027](D-027-performance-budgets.md).
