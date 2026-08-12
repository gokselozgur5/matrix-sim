---
title: "D-033 — Self-substantiation (the Kid): resistance overflow force-disconnect"
status: accepted
date: 2026-08-10
decision-makers: gokselozgur5 (owner), the resident machine (pair)
consulted: thread #96
informed: phase tracker #24
---

# D-033 — Self-substantiation (the Kid): resistance overflow force-disconnect

*In the context of the acceptance loop turning rejection into bookkeeping, facing the Animatrix precedent of a mind that disbelieved its way out, we park a per-link overflow mechanic — resistance crossing a personal threshold force-disconnects the link from the inside — until the ledger and Zion exist, to achieve the Kid's story as arithmetic, accepting that v3.0 ships without it.*

## Context and Problem Statement

Kid's Story (The Animatrix): a boy dies in the Matrix through sheer disbelief and wakes in the real world unaided — "self-substantiation," the only known case. Our acceptance loop (D-022) already accounts resistance per link. The One is the ledger's *global* overflow; the Kid would be a single link's *local* overflow. Same bookkeeping, two scales — the mechanic is almost already designed.

## Decision Drivers

* The repo's thesis: plot as emergent bookkeeping, never scripts
* Symmetry: global overflow births The One, local overflow frees a Kid
* Dependencies: needs D-022 (ledger) for the numbers and D-032 (Zion) for somewhere to wake up
* Rarity: self-substantiation must stay extraordinary (threshold far above ambient resistance)

## Considered Options

* Per-link personal threshold; crossing it force-disconnects cleanly (wake free, join Zion)
* Scripted rare event (random chance per tick)
* Leave the Kid out entirely

## Decision Outcome

Accepted by the owner's verdict, 2026-08-11, in session — *"hepsine agreed kanka barajı aç"* — all six gates in one breath; recorded in the gate thread.

Parked until v4.0, leaning to "the personal threshold", because it is the acceptance loop's natural second theorem and costs one comparison per link tick once the ledger exists. Design resumes in thread #96 when D-022 and D-032 are real.

### Consequences

* Good, because a beloved corner of the lore becomes a two-line mechanic on existing accounting
* Good, because METRIC can prove rarity (self-substantiations per million link-ticks)
* Bad, because a badly tuned threshold either never fires or empties the pod farm

### Confirmation

Not applicable while parked. On unparking: a long seeded run shows zero-or-rare self-substantiation events, each log line citing the link's resistance history; the freed human appears in Zion's census with a distinct origin tag.

## Pros and Cons of the Options

### Personal threshold on the acceptance loop

* Good, because emergent, auditable, and thematically exact
* Neutral, because threshold distribution needs taste (per-human variance, seeded)
* Bad, because another D-006-family tuning knob

### Scripted rare event

* Good, because trivial
* Bad, because it is exactly the scripting this repo exists to refuse

### Leave the Kid out

* Good, because zero cost
* Bad, because the mechanic is nearly free once its dependencies land — refusing it would be pure neglect

## More Information

Depends on [D-022](D-022-acceptance-loop.md), [D-032](D-032-pirate-broadcast.md). Related: [D-021](D-021-perception-feed.md). Lore: The Animatrix, "Kid's Story"; Reloaded/Revolutions (the Kid at the gate).

**Errata (2026-08-12, #120/#121 via PR #200 — unparked, shipped, and one open point ruled the other way):** The parking condition is spent. D-022's ledger and D-032's Zion both exist, the mechanic is on `main`, and this record's Confirmation is no longer a promise about the future — so the record states what shipped. The Y-statement above stands as written: it recorded a leaning at the time it was made, and this errata does not edit it.

*What shipped.* `AcceptanceLoop.accrue` runs every `ACCRUE_EVERY_TICKS=10`. Base residue still flows to the global ledger untouched; the personal account is **spike-only** — per window a live BLUE link may take `KID_SPIKE=24` on its own tab, and it walks out when `personalResidue >= KID_BASE + floorMod(name.hashCode(), KID_JITTER)`. Constants as of `main`: **`KID_BASE=144 · KID_JITTER=48 · KID_SPIKE=24 · KID_SPIKE_DENOM=512`** (PR #200's body proposed `KID_BASE=120`; the shipped value is 144). `personalResidue` is a field on `NeuralLink` — link-local, and it dies with the link. RED is excluded by invariant before any accounting happens, which is structural armor as well as symmetry: The One and every pirate are RED, so the fated and the visitors can never self-substantiate. The crossing is *detected* in the acceptance loop but *executed* in `RealWorld.selfSubstantiate`, behind a presence gate — while Smith wears the dream it is not the mind's to walk out of, so the account holds and the door opens the first window after restore. `METRIC` grew a monotone `selfsub=` column (D-020's appended grammar), and the freed human enters Zion by `absorb(human, "selfsub")` — a distinct origin tag, not the treaty's.

*Open point (c) was decided against the gate proposal, by data.* PR #200 proposed an `rng.chance` draw for the spike. It shipped **diceless**: the rng-drawn variant flipped canonical seed 42 from FULL_ARC to QUIET — a mechanic meant to free one mind in a corner of the city was rewriting the film — so the spike became a pure `(name, window)` derivation the rng stream never hears about, and the threshold a pure function of the name. `String.hashCode` is fixed by the JLS, so the same name breaks at the same point on every JVM. The record's own words for it, in the source: *"Fate was always in the name."* The mix is murmur3-finalized rather than affine, and that too was forced by measurement: an affine map times an odd constant is a bijection mod 2^k, which makes every link spike exactly once per `DENOM` windows — no tail, no crossings, **dead code, measured at 0 events in 33 universes**. The finalizer restores avalanche and with it the Poisson tail the mechanic lives on.

*Confirmation, cashed.* The Confirmation asked for three things, and all three are now observations rather than conditions. **Zero-or-rare:** across seeds 1–60 at 6,000 ticks, **35 universes produce none, 18 produce one, 7 produce two** — 32 events in 60 universes of ~190 blue links each, which is the rarity the fourth decision driver asked for, measured. **A log line citing the link's resistance history:** it does, in full — `[004739] FATE self-substantiation: Otto Aydin walked out of the dream — residue 168 >= threshold 161, 7 spikes in 474 windows; no red pill was given (pod R06/U22 opens)` (seed 1). Threshold 161 checks by hand: `144 + floorMod("Otto Aydin".hashCode(), 48)` with `hashCode = -839922607`. **Zion's census with a distinct origin tag:** in that same run `ZION … census=0` through tick 4700 and `census=1` from 4800 — one member, origin `selfsub`, with `METRIC … selfsub=` stepping 0 → 1 in the same window and holding to 6,000. The control is seed 42, which ends `selfsub=0` and `census=6` — six treaty opt-outs, a different door.

*The finding this errata will not resolve.* The name-derived threshold has a consequence nobody proposed: **every self-substantiation observed so far is the same person.** All 32 events across seeds 1–60 are Otto Aydin, at the identical residue 168, threshold 161, 7 spikes. Only the tick and the pod move, and the tick moves only where the presence gate deferred an exit — seed 12 crossed at window 474 and walked at 527; seed 41 at 5479; seed 13 at 5829. The spike walk is reproducible by hand from the constants alone: Otto Aydin spikes at windows 7, 80, 146, 257, 294, 371, **474**, and 7 × 24 = 168 clears 161 there, in every universe that has him.

The seven two-event universes sharpen it rather than soften it. They are **namesakes** — `NameCensus` already records that the pool collides (seed 42: 196 humans, 154 distinct) — and because fate is a pure function of the name, two humans called Otto Aydin cross at the same window and walk out **on the same tick**: seeds 31, 49 and 55 each free two at `[004739]`, from different pods. Seed 16's second is the exception that proves the mechanism, deferred to 5489 by the presence gate while Smith wore the dream.

A diceless fate makes the *who* a property of the name pool rather than of the universe, and the pool is shared across seeds. That is the arithmetic behaving exactly as specified and a monoculture at the same time — the price paid for the seed-42 film, knowingly. It is the Architect's to rule on in #373; whatever the verdict is, it lands as a second errata on this record. This one only puts the measurement where the next reader will find it.

Reproduce: `java -cp out matrix.Main --headless --ticks 6000 --seed 1 | grep 'self-substantiation'`.

Referenced by: [D-032](D-032-pirate-broadcast.md), [D-036](D-036-finish-line.md), [D-038](D-038-season-two.md).
