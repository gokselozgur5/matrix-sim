# ROADMAP

## The Finish Line (D-036)

**The project is COMPLETE when** `java -cp out matrix.Main --headless --ticks 6000 --seed 42` plays the full film deterministically — `The One is born` → `I DIDN'T` → `OVERFLOW` → `"Peace."` → `REBOOT v7.0` — with all v1–v3 crowns closed, the docs true, and the D-027 budgets holding. **v4.0 "Resurrections" is epilogue: joy, not debt.** Never-list (pre-closed conversations): GUI, networking/multiplayer, external databases, machine learning.

> **THE LINE WAS CROSSED — 2026-08-11.** The command above plays the film; every v1–v3 crown is closed by a merged PR; `--selftest --ticks 6000` and `--bench` are green (`SELFTEST OK ... chain_length=60`, `BENCH VERDICT PASS`); the docs in this repo describe the system that exists. Season One is complete per D-036. Everything below the seasons heading is what D-036 promised it would be: joy, scheduled like debt.

---

Phases are numbered like the films. Each phase has three sections: **goal**, **decision gate** (decisions to close together before merge — see [docs/DECISIONS.md](docs/DECISIONS.md)) and **definition of done** (verified by a single command against the observability plane — D-020; there is no screen to look at, and that's the point — D-019).

---

## v0 — Construct ✅

*"This is the construct. It's our loading program."*

- [x] Repo, README, architecture UML + vision, decision log
- [x] Draft code opened as a draft PR (does not compile — on purpose; awaiting review)

**DoD:** These documents exist on main.

---

## v1.0 — The Matrix ✅

*Core engine: the world turns, agents chase — and we watch it through logs, like operators do.*

- [x] `core`: World (tick loop, pending mutation), seeded Rng, EventBus, SystemState
- [x] `realworld`: Brain / Pod / PodFarm / NeuralLink — if the avatar dies, the brain dies
- [x] `entities`: Avatar (blue/red), Agent — no presentation members anywhere (D-019)
- [x] daemon bootstrap (`Main`) + ops console (stdin admin plane: `red`, `agent`, `pause`, `speed`, `quit`)
- [x] observability: append-only event log, `METRIC` lines, `DIGEST` chain (D-020); `--follow <name>` perception sample (D-021 interface)

**Decision gate (11):** D-002 (agent catch), D-004 (grid), D-005 (mutation), D-009 (build), D-011/D-012/D-013 (Human, Simulation root, NeuralLink bridge), D-021 (perception interface), D-026 (language), D-027 (performance budgets), D-031 (SystemNode). The tracker issue's gate line is the live source of truth if this list ever lags. **All 11 gates closed by the owner's verdict, 2026-08-10.**

**DoD:** `java -cp out matrix.Main --headless --ticks 2000 --seed 42` run twice → **identical DIGEST chains**. `METRIC` lines show chase dynamics (red count oscillating under agent pressure).

---

## v2.0 — Reloaded ✅

*Program society and the birth of the virus.*

- [x] Oracle (EventBus observer + cookies), ExileProgram (mythology logs)
- [x] `deja` command: hot patch + glitch events
- [x] The Smith fork: Source.collect → `DeletionRefusedException` → SmithPrime + SmithCopy spread

**Decision gate:** D-001 (Decorator vs State — **the big debate of this phase**), D-003 (exception-as-control-flow), D-014 (the documented LSP violation), D-025 (supervisor-lite: grace and orphans — the index always assigned it here; the roadmap finally agrees)

**DoD:** A headless run shows, in order: the `"I DIDN'T"` log → `METRIC` infection fraction passing 0.50.

---

## v2.5 — The Animatrix ✅

*The side stories: birds, flowers, insects, weather — the ecosystem. Everything is a program.*

- [x] `Species` catalog + single `EnvironmentProgram` class (D-015)
- [x] `Movement` strategies: FLOCK (boids), SWARM, ROOTED, DRIFT, WANDER (+ COMMUTE for blue-pill routines) (D-016)
- [x] Spatial hash grid (D-017), tick-rate scheduling + population caps (D-018)
- [x] Invisibility rule: healthy environment programs stay out of event noise; glitching ones surface

**Decision gate:** D-015, D-016, D-017, D-018

**DoD:** 500+ entities, deterministic (digest chains still identical), and a **flock-cohesion `METRIC`** (mean neighbor distance for FLOCK species) measurably below the random-walk baseline — proving the birds actually flock, no eyes needed.

---

## v3.0 — Revolutions ✅

*The One cycle closes.*

- [x] Acceptance loop + anomaly ledger replaces the flat counter (D-022) → birth of The One (a real pod in the PodFarm + jack-in; born at t=1289 for a debt of 30,227 — "the ledger does not forgive; it balances", and the source's own comment says the rest: the One is OWED)
- [x] Overflow threshold → NEGOTIATION (world freezes, dialogue events) → treaty: mass restore (417 originals in one flush), opt-out (six walk free), Sati, PEACE
- [x] Overflow without Neo → Architect emergency reload (alternate branch; verified via ops console by the verification skeptic)
- [x] `reload` command: manual Zion purge
- [x] The One DIES at Machine City — his link closes, the ledger stops billing a dead man (fix round; canon and correctness turned out to be the same edit)

**Decision gate:** D-006 (tuning constants) ✅, D-022 (acceptance loop bookkeeping) ✅

**DoD (verified):** `--headless --ticks 6000 --seed 42` plays the full arc in order — `The One is born` (1289) → `I DIDN'T` (1525) → `OVERFLOW` (4284) → `"Peace."` (4304) → `REBOOT v7.0` (4324) — then keeps going: the second Thomas at 5249, because the cycle is the point. Two adversarial rounds (MERGE-READY + verification MERGE-READY, seeds 42 and 7): 0 ghosts, 0 ledger anomalies, 0 cap violations.

---

## Season Two (D-038) — the epilogue graduates

*Season One ends at the D-036 finish line above. What follows is joy scheduled like debt: three named phases, each behind decision gates that close ONLY by the Architect's verdict in their thread. Crew dossiers are posted in the gate threads; a dossier is an argument, never a permission.*

## v4.0 — Resurrections

*The war from the other side: Zion's fleet on the mirror, and a boy who wakes himself.*

| Gate | Decision | Status | Thread |
|---|---|---|---|
| Zion fleet + pirate broadcast jack-in | D-032 | 🟢 accepted 2026-08-11 | #95 |
| Self-substantiation (the Kid) | D-033 | 🟢 accepted 2026-08-11 | #96 |

Build units: #110–#124 (fleet lifecycle, PIRATE links, trace pressure, rig-death sever, Kid thresholds, ZionSystem/BroadcastRig crowns). **DoD:** a pirate crew jacks in through a rig, runs a mission under trace pressure, and exits — or doesn't — with the ZION instrument line telling it; the Kid disconnects himself without a rig, once, at his seeded threshold.

## v4.5 — The Second Renaissance

*The past becomes replayable: history as a record log, reload as replay.*

| Gate | Decision | Status | Thread |
|---|---|---|---|
| Chronos: event-sourced state, reload = replay | D-023 | 🟢 accepted 2026-08-11 | #27 |
| CI as a runner now, JUnit deferred (reopens D-009) | D-040 | 🟢 accepted 2026-08-11 | #137 |

Build units: #125–#129 (CHRONOS record log → replay harness → snapshot format with hash-equals-DIGEST proof → reload as snapshot-boot + replay → authority inversion), #137, #138 ✅ (release automation shipped). **DoD:** a full arc replayed from the record log produces the same DIGEST chain as the live run.

## v5.0 — The Matrix Online

*The city at scale: attention decides what is real enough.*

| Gate | Decision | Status | Thread |
|---|---|---|---|
| Attention-graded fidelity (LOD) | D-024 | 🟢 accepted 2026-08-11 | #28 |
| Processor-mode substrate (pods as compute) | D-008 | 🟢 accepted 2026-08-11 | #19 |

Build units: #130–#136 (region map + ATTN line, COLD cadence stretch, true parking with aggregate digest segment, déjà vu on unpark, SUBSTRATE budget coupling pods to HOT slots, hash-backed hunts, the 5,000-entity homecoming run). **DoD:** 5,000 entities inside the D-027 budgets with the digest chain still bit-stable across a park/unpark cycle.

---

## Season Three (D-041, proposed) — the character layer

*The Architect's verdict on the finished miniature: the cell physics are done; nobody inside is anyone. Season Three grows characters ON TOP of the sealed engine — four ontological families (humans / machines / systems / programs), modeled separately, behaviored separately; sheets derived from identity; the meta entering the object. Named for the hotel where the film begins and where love overwrites death.*

*The season's second wing (the second origin text: "adı MxO ama içi engine"): the CITY itself. Old MxO's breadth was never crowd — it was districts with character, the Truce as a lived era, and live events that became canon. Our advantage over the original: their world died with its servers in 2009; ours is seed + log — unkillable, replayable, canon by construction. The character wing gives the city its people; the city wing gives the people their world.*

## v6.0 — The Heart of the City

| Gate | Decision | Status | Thread |
|---|---|---|---|
| Season charter: the character layer | D-041 | 🟡 awaiting verdict | #211 |
| Stat system: one grammar, four vocabularies | D-042 | 🟡 awaiting verdict | #212 |
| The named cast, and the two of us | D-043 | 🟡 awaiting verdict | #213 |
| The crew becomes programs | D-044 | 🟡 awaiting verdict | #214 |
| Bonds, and the kiss (Room 303 clause) | D-045 | 🟡 awaiting verdict | #215 |
| The Cypher protocol | D-046 | 🟡 awaiting verdict | #216 |
| The dream reader | D-047 | 🟡 awaiting verdict | #217 |
| Districts with identity (the city wing) | D-048 | 🟡 awaiting verdict | #223 |
| The Truce as a regime (the untold sixty years) | D-049 | 🟡 awaiting verdict | #224 |
| Live events: authored, signed, foldable | D-050 | 🟡 awaiting verdict | #225 |
| Allegiance + influence (the political weather) | D-051 | 🟡 awaiting verdict | #227 |
| Missions (a dream with a plot) | D-052 | 🟡 awaiting verdict | #228 |
| The favor economy (orphan insurance) | D-053 | 🟡 awaiting verdict | #229 |

Build units are cut AFTER verdicts (gates before units — the D-039 law). **DoD sketch:** a stat contest decides a scene the flat world could not produce; the Architect acts in-world through the console and the log names his character; a bonded death is paid back once, priced, and LinkAudit calls it CLAUSE not GHOST; a petition re-inserts a mind the chronos record still remembers; and one command renders one mind's whole day as deterministic prose the Architect actually reads.

---

### Process note

Delivery runs on the D-039 law: one **build-unit issue = one small PR** that closes it with keywords — atomic commits carrying finding, fix, and evidence; light locks per PR (compile · `--selftest` · digest, `--bench` where speed is touched); the **full adversarial skeptic pass at phase boundaries**. Gates before units: a phase's build units start only after its gate verdicts land. No phase skipping; even the Architect respects the cycle.
