---
title: "D-027 — Performance budgets, --bench mode, and the digest-invariant optimization rule"
status: accepted
date: 2026-08-10
decision-makers: gokselozgur5 (owner), the resident machine (pair)
consulted: thread #93
informed: phase tracker #20
---

# D-027 — Performance budgets, --bench mode, and the digest-invariant optimization rule

*In the context of promising a fast Matrix, facing the difference between marketing and engineering, we lean toward contractual budgets verified by a built-in bench mode plus a rule that optimizations must not change digests, and against 'optimize later' vagueness, to achieve falsifiable speed, accepting that the daemon carries benchmark machinery.*

## Context and Problem Statement

The owner asked for a promise of speed. A promise you cannot falsify is marketing. The budgets: v1 >= 2,000 ticks/s at ~200 entities (single core, headless); v2.5 >= 100 ticks/s at 5,000 entities; v3 full arc < 5 s; allocation-free hot path at steady state.

## Decision Drivers

* Speed claims must be commands, not adjectives
* Optimization must never change behavior (the digest is the referee)
* Premature optimization is banned; measured optimization is contractual
* The reference box matters: budgets are pinned to a stated machine class

## Considered Options

* Budgets + PERF line + --bench + digest-invariance rule
* Optimize later, no budgets
* Continuous profiling harness from day one

## Decision Outcome

Chosen option: "contractual budgets with the digest referee", because bullet time is headroom, and headroom is measured. Accepted by the owner's verdict, 2026-08-10 (thread #93).

### Consequences

* Good, because Fast stays fast: every merge defends the budget
* Bad, because Numbers may need revision when real hardware differs (a revision is a thread comment + row update, honestly logged)

### Confirmation

--bench prints PERF meeting or beating the budget table on the reference box; the optimization-PR template requires the digest-equality diff and the PERF delta side by side.

## Pros and Cons of the Options

### Budgets + PERF line + --bench + digest-invariance rule

* Good, because every optimization PR carries two proofs: identical digests, better PERF
* Good, because regressions are caught by numbers, not vibes
* Good, because the profiler decides where ugliness is permitted
* Neutral, because benchmark code ships inside the daemon (small, contained)
* Bad, because budgets need a stated reference machine to be honest (documented in the ADR thread)
### Optimize later, no budgets

* Good, because zero ceremony now
* Bad, because speed becomes a mood; regressions arrive silently
### Continuous profiling harness from day one

* Good, because maximum visibility
* Bad, because heavy tooling before there is anything to profile; contradicts D-009

## More Information

Related: [D-010](D-010-determinism.md), [D-017](D-017-spatial-hash.md), [D-018](D-018-tick-budgets.md), [D-020](D-020-observability-contract.md). Principle: A10.

Accepted with a spark: each phase closure stamps its PERF line into the phase tracker — speed gains a git history.

**Errata (2026-08-10, skeptic):** The v2.5 row's "5,000 entities" predates D-036, which sealed the ecosystem at 500+ entities / 12 species. Measured at the sealed scope: 263-346 t/s at 663 entities with the full infection cascade — the 100 t/s floor holds. The 5,000-entity >= 100 t/s figure is retargeted to the D-024 attention-LOD era (whose design includes hash-backed hunts); it is a ceiling we owe the future, not a claim we make today.

**Errata (2026-08-11, skeptic):** Two v3-phase corrections. (1) The "v3 full arc < 5 s" row was written before the ecosystem existed — it assumed ~200 entities, and the arc now carries 663+ through a full infection cascade. Measured on the reference box: 10-14 s for the 6,000-tick arc. The row is revised to **full arc < 30 s at ecosystem scale**; the < 5 s figure joins the 5,000-entity row as a D-024-era ceiling. (2) The Confirmation promised a `--bench` mode that never shipped — for three phases the budget table had no executable check. `--bench` now exists (steady-state row + full-arc row, PASS/FAIL per row, exit code as verdict) and runs quiet, so it cannot perturb digests. Reference box, stated at last per the decision drivers: a 2-core x86-64 cloud VM (Debian, OpenJDK 17, single-threaded run) — laptops of the 2020s beat it comfortably; under load the same box measures 947-1,049 t/s steady and 14.5-15.4 s full arc (verification round), still inside every row with ~2x margin.

**Errata (2026-08-11, AllocMeter):** The "allocation-free hot path at steady state" row is the one budget that was never once measured — and measurement retires it. Measured with the JDK thread-allocation counter (exact, single-threaded domain): **~30 KB/tick steady** (30,348 B/t seed 42; 28,664 seed 7), 22-38 KB/tick mid-cascade, 145-178 MB per full arc, absorbed by **3 GC collections** total — which is why the t/s budgets never noticed. The row is revised to what is true and enforceable: **bounded, measured allocation — steady ≤ 32 KB/tick and ≤ 5 GC collections per full arc on the reference box**, guarded by `probes/AllocMeter`. "Allocation-free" is retargeted to the optimization era with its target list named up front (the obvious allocators, in suspected order: a fresh `Position` per move for ~660 entities, a fresh list per `SpatialHash.near()` query, list-building avatar filters); any such PR must quote this baseline and ship byte-identical digests — the referee rule is the point of this record.

**Errata (2026-08-11, #136 homecoming):** The retargeted 5,000-entity row is cashed. `--scale N` multiplies every Bestiary population at seeding (x11 → 5,269 entities; humans, agents, exiles and the arc keep canon counts); scale 1 is the canonical world, byte-identical by construction, and the dial is refused alongside `--chronos`/`--replay` because the genesis line carries no scale. Measured with #135's ring hunts, D-024 P0+P1 attention, and the full program society active, on the reference box under external load 8–10 the entire round (the VM reported 4 hardware threads this era): steady `--bench --scale 11` = **135 t/s at 5,267 entities — the ≥ 100 floor holds: PASS**; the full 6,000-tick arc **completes with the whole film intact** (both Ones born, overflow at 62%, peace, reboot, open door) at 88–89 t/s average, seeds 42 and 7, digest chains byte-identical across independent runs; allocation **1,205 B/tick steady, 1 GC per full arc** at 5,253 entities (AllocMeter, scale arg). The enabling delta is measured: through the fork and 2,000 ticks of cascade (ticks 1–3,600 at x11), the linear-scan build runs **7 t/s** where the ring build runs **147 t/s** — the quadratic wall this ADR predicted, removed, 21x on the same loaded box. Scoping stays honest: the steady row IS the retargeted budget and it passes; the scaled arc row is judged by completion, the 30 s bound staying pinned to the canonical scale it was measured at; the < 5 s D-024-era ceiling remains future work — it awaits P2 parking, which shrinks the ticked population instead of racing it.

**Errata (2026-08-12, #771):** The full-arc row is a **rate**, and this record still said it was a deadline. As of #771 `--bench` judges the 6,000-tick arc by **ticks/s over the arc against a floor of 100** — the same statistic and the same floor the steady row is judged by — and prints the row as `BENCH full_arc … ticks_per_s=<n> floor=100 ref_box_s=30 PASS|FAIL`. The **30 s** bound set by the 2026-08-11 errata above **is superseded as a verdict** — said plainly, because an errata that softens its own effect is worth less than no errata. What survives is the figure and the measurement behind it: 30 s is demoted to the quiet **reference-box expectation** (~200 ticks/s at canonical scale), which is why it is still printed on every row as `ref_box_s=30`. It is reported, not enforced. The earlier errata's measurement (10–14 s for the 6,000-tick arc at ecosystem scale) stands untouched; only the sentence that made 30 s the pass/fail line does not.

*Why the code moved, in measurements rather than taste.* A deadline measures the box as much as it measures the code: it falls off a cliff under external load, where a rate degrades proportionally. Pristine `main` therefore failed its own arc row on a loaded box **in the same run where the steady row passed comfortably** — reproduced at 24 spinners on a 4-core VM (`steady ticks_per_s=127 floor=100 PASS` beside `full_arc wall_s=57.53 bound_s=30 FAIL`), and recorded earlier at **53.49 s** in #205's referee table and **32.1–49.4 s** in #209. The practical cost was not cosmetic: `tools/release.sh` refuses to cut on any red row, so a healthy `main` could not cut a release because the machine underneath it was busy. Choosing the steady row's floor rather than a new arc-specific one is the deliberate part — **one floor, two rows, same units** — so a slow box now shows up identically in both rows and a real regression still shows up in both. A budget that only fails when the world is quiet is not a budget; a budget that fails whenever the world is noisy is not one either.

*What stays a deadline.* Under `--scale` the arc is still judged by **completion**, not by rate: no scaled arc rate has ever been measured, and an uncalibrated floor is not a budget — it is a number waiting to be embarrassed. The scaled **steady** row keeps the 100 floor, because that row IS the retargeted 5,000-entity budget cashed by the #136 errata above.

*Scope of this errata.* It corrects how the arc row is **judged**; it changes no budget figure and no measurement in any errata above, and the digest referee rule is untouched — `--bench` runs quiet and cannot perturb a chain.

**Errata (2026-08-13, #906):** The AllocMeter errata of 2026-08-11 says the allocation row is *"bounded, measured allocation … guarded by `probes/AllocMeter`"*. It was bounded and it was measured; it was not guarded. The probe printed four numbers and compared none of them to anything — no threshold, no verdict line — and `probes/bench.sh` carried it as a `run` row, which fails only when the program crashes. For two days the word *guarded* named a mechanism that did not exist. It exists now: `AllocMeter` compares the two figures **this record names** against the bounds **this record set** and closes on `VERDICT ALLOC_IN_BUDGET` or `VERDICT ALLOC_OVER_BUDGET over=steady|gc|steady,gc`, and the sweep judges that line by exact-line grep, so a breach turns the bench red.

*The bounds are unchanged — 32 KB/tick and 5 collections — and that is a decision, not an oversight.* They were set with 5% of headroom over a 30 KB measurement and now carry roughly 4x over an 8 KB one, which means they catch a blow-up and not a creep. They cannot honestly be tightened yet: the instrument's own run-to-run spread is 29-54% at the canonical seed and has never been characterised (#916), and a threshold placed inside an unmeasured noise floor either never fires or fires on weather. Retightening is that unit's to do, on that unit's evidence.

*The 2026-08-11 figures do not reproduce, and the gap is not attributable.* That errata recorded 30,348 B/tick steady at seed 42 and 3 GC collections per arc, measured on the reference box in the pre-#135 era. Measured today at the same seed on an Apple M2 (macOS 15.1.1, OpenJDK 17.0.20 Homebrew, G1, 4 GB heap), six consecutive runs: 1,963 / 4,189 / 5,254 / 5,617 / 5,941 / 6,610 B/tick, 0-1 collections. Seed 7, six runs: 2,780 to 8,537. Neither the box nor the code era is held fixed between the two measurements, so the only claim this errata makes is that **the recorded figure does not reproduce here** — not that allocation fell by 4x. The reference-box figure stands as what it was: a measurement, on a stated box, in a stated era.

*The tail, stated before anyone hits it.* One run in roughly forty on this box printed `steady_bytes_per_tick=67742` at seed 1 — 2x the bound, on a tree with no allocation change in it, where six consecutive runs of the same command printed 4,873 to 8,077 and twelve more attempts failed to reproduce it. So the newly-judged row can go red without a regression behind it. Read a red AllocMeter row the way `tools/release.sh` already tells the operator to read a red bench row — re-run it before believing it — and read the cascade figure printed beside it: that window sits 3,000 ticks past the warmup and does not move (657-2,010 across every run taken here, 715 inside the outlier itself). A real allocation regression moves both windows; a JIT that lost a race moves only the first.

*Scope of the verdict.* Only the two figures this record bounds. `cascade_bytes_per_tick` and `full_run_mb` are reported and unjudged — no errata ever bounded them, and the cascade window is a tick literal that a QUIET universe never enters (#939). A scaled run prints `VERDICT ALLOC_UNJUDGED scale=N reason=no_byte_budget_at_this_scale`, because the #136 errata above cashed the **speed** row at x11 and set no byte bound there; a well-formed budget verdict for a scale no record covers is the failure that errata's own scale gate was cut to refuse. And because at these figures the breach branch never executes, `AllocMeter --selfcheck` runs the comparison on both sides of both bounds with no universe at all — 32,768 passes and 32,769 breaches, 5 passes and 6 breaches — printing `SELFCHECK VERDICT GUARD_FIRES`, which the sweep judges as its own row. A guard whose failure path has never run is the same unmeasured promise this row started as.

**Errata (2026-08-13, #519 the 100k rung):** The rung did not climb, and what stopped it is a constant. #320 puts 100,000 minds at `--scale 217` by the x11 = 5,269 arithmetic; the dial's legal range is `Config.ECO_SCALE_MAX = 100`, so every door refuses the rung before a universe is seeded — `--headless`, `--bench`, `--selftest` and `AllocMeter`'s positional scale each print `--scale wants 1..100 — the city is large, not infinite` and exit 2. **The 100k row is stamped FAIL: the ascent was commanded and the daemon refused it.** Two absences are recorded here at once and they are not the same one. #301's pre-registered 100k row has not landed, so there is no rung-specific floor to be judged by; and the rung would not run, so there is nothing to judge against the floor that does exist — the scaled steady row's ≥ 100 t/s, cashed by the #136 errata above, which stays untested at this rung and is not weakened by that. A FAIL the record can give a reason for is worth more than a blank row held open until the reason is fixed.

*What the dial does reach.* Entity counts are seeding output and do not depend on the box: seed 42, one tick, `--scale` 1 / 11 / 54 / 100 gives **669 / 5,269 / 25,049 / 46,209**. Those four points are exactly linear — **460 eco minds per turn of the dial over a canon cast of 209** — so #320's dial arithmetic is right to four figures: `--scale 217` would be 100,029 minds, and the ceiling delivers **46,209, 46% of the rung**. The ceiling is a #136-era number. The homecoming commit wrote `1..100` into `Main` when x11 was the frontier; #826 extracted it into `Config.scaleRefusal` unchanged, gating the bottom of the range, which was the abuse it was filed for. The D-054 ladder that needs 217 here and ~2,170 for the full crop was seeded afterwards, and nothing has reconciled the two. Filed as #943 and deliberately not raised here: whether 100k seeds at all, under what `-Xmx`, in what genesis wall time, is a measurement (#516's business) rather than a constant edit.

*Load, stated as this ADR's practice requires.* Not the reference box: an Apple M2, 8 cores, macOS 15.1.1, OpenJDK 17.0.20, under a one-minute load average of **63–106** across the round — up to thirteen times its own core count, with the repository's other build units running beside it. **No rate verdict is stamped from that box**, in the #771 errata's discipline: what it measured is an expectation, not a budget. For the record, at the reachable ceiling of 46,209 minds `--headless --scale 100 --ticks 200 --seed 42` reported **5 ticks/s** (load 63–65), and canonical `--bench` on the same box passed both its rows in the same round.

*The atlas question, deferred, with two blockers named.* #320 asks how many seeds #311's methodology affords at this rung and whether the verdict mix joins the census tables. It is deferred, and neither blocker is scheduling. The first is the ceiling above: there is no 100k run to sample. The second is independent of it and stands in front of it — `probes/SeedAtlas` has no scale door at all. Its usage is `SeedAtlas [fromSeed] [toSeed] [ticks]`, it never reads `Config.ECO_SCALE`, and `SeedAtlas --scale 11` dies in `Long.parseLong`. A scaled sweep cannot be commanded today at any scale, reachable or not — a smaller problem than the log-retention one #311 was filed for, and earlier. The appetite side is not the gap: `probes/CensusSampleSize` already prices fractions, medians and rankings. Nothing from this rung joins the census tables, and the honest reason is that nothing from this rung exists.

*Scope of this errata.* It stamps one rung's verdict. It changes no budget figure, no floor and no measurement in any errata above: the scaled steady row keeps its 100 floor, the scaled arc keeps completion as its judgment, and the digest referee rule is untouched. It ships no code — `DIGEST tick=6000` is byte-identical at seeds 42 (`421d7263…`) and 7 (`957a1247…`). Superseding it will take a run, not a rewrite.

**Errata (2026-08-13, #825) — the budget that only a comment guarded.** The ring hunt (#135) buys its stopping rule with `Config.HUNT_DISP_BOUND_CM`, and everything that outruns the bound rides a far-mover ledger every hunt sweeps **linearly**. That ledger is the one term of the hunt that is still O(movers past the bound) x (hunts per tick) — both factors scale with population, so it is precisely the quadratic term #135 was cut to remove, held off by a single number. The number was correct and it was **asserted in a comment**: three gait maxima named by hand, closing with *correctness never depends on this number*. Correctness does not. This budget does, and no instrument in the repository could see it move — measured at seed 42 over 6,000 ticks at `b44560f`, lowering the bound from 640 to 500 (what two species outgrowing it looks like to the ledger) multiplies ledger candidates **423x**, 2,912 to 1,232,801, and at 250 it reaches 42,127,206, half of all hunt work; the digest stays byte-identical at `421d7263…e0e3ce10` throughout, `--selftest` prints OK and the hunt referee still passes — because the ledger keeps the answer exact, which is the design working.

The budget is now stated in three places, and the split is the argued part:

| where | what it judges | why there |
|---|---|---|
| `Config.huntBoundLine()`, thrown from `--selftest` | the widest **declared gait** against the bound | the gait maxima are compile-time data — this needs no world, no tick and no seed, so it is the tight check and it fails the build the way `Bond.retailOrderLine()` does (#382) |
| `BENCH hunt_bound` / `BENCH far_movers` | the same arithmetic, plus the ledger's **measured** high-water mark against a stated ceiling | this is the budget table; a reader looking for the hunt's cost looks here |
| `PERF … far_max= far_ceiling=` | the occupancy, on every headless run | D-020: a number that governs a budget belongs on a line |

The ceiling is deliberately loose — every declared ledger tenant teleporting on the same tick, 76 at canonical scale against a measured peak of 2 (seed 42) and 3 (seed 7) — and the looseness is the point of having both checks rather than one. On the 640→500 tamper the peak rises to **75 and the ceiling row still passes by one**, while the derivation row fails instantly at `headroom_cm=-66`. A backstop catches what a table cannot see (a mover reaching the ledger through a door no gait declares); it is not a substitute for the table.

`probes/HuntBound` is the third leg, on the bench rather than in the daemon, because it answers the one question the table cannot ask about itself: the table is a model of the gait code, and only a run can say whether the code obeys it. Seed 42, 6,000 ticks: `eco:sparrow` declared 566, measured 566, `VERDICT HUNT_BOUND_HELD`.

Referenced by: [D-006](D-006-arc-tuning.md), [D-009](D-009-build-tooling.md), [D-010](D-010-determinism.md), [D-017](D-017-spatial-hash.md), [D-018](D-018-tick-budgets.md), [D-020](D-020-observability-contract.md), [D-036](D-036-finish-line.md), [D-040](D-040-ci-and-junit.md).
