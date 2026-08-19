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

- [x] Acceptance loop + anomaly ledger replaces the flat counter (D-022) → birth of The One (a real pod in the PodFarm + jack-in; born for a debt the ledger names on the line itself — "the ledger does not forgive; it balances", and the source's own comment says the rest: the One is OWED)
- [x] Overflow threshold → NEGOTIATION (world freezes, dialogue events) → treaty: mass restore (417 originals in one flush), opt-out (six walk free), Sati, PEACE
- [x] Overflow without Neo → Architect emergency reload (alternate branch; verified via ops console by the verification skeptic)
- [x] `reload` command: manual Zion purge
- [x] The One DIES at Machine City — his link closes, the ledger stops billing a dead man (fix round; canon and correctness turned out to be the same edit)

**Decision gate:** D-006 (tuning constants) ✅, D-022 (acceptance loop bookkeeping) ✅

**DoD (verified):** `--headless --ticks 6000 --seed 42` plays the full arc **in order** — `The One is born` → `I DIDN'T` → `OVERFLOW` → the One's flatline → `"Peace."` → the open door → `REBOOT v7.0` — then keeps going: a second Thomas, because the cycle is the point. The ORDER is the definition of done and it is checked on every push by `probes/ArcBeats.java`; the TICKS are not written here, because a second copy of them is a second thing to go stale — this line carried five that had drifted by up to 260 ticks while reading `(verified)` (#1447). The live column lives in `README.md`, where `DocLint` compares it against a run. Two adversarial rounds (MERGE-READY + verification MERGE-READY, seeds 42 and 7): 0 ghosts, 0 ledger anomalies, 0 cap violations.

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

## The Year (D-054, accepted 2026-08-12) — six seasons, five programs

*The Architect's original horizon, 2026-08-11, named a full year at 200+ daily contributions — "1 yılda saatlerce çalışıp dolduracak kadar büyük düşün." D-063 removes that number as a floor or admission rule: the year is still filled by shape, human/world and dependency value choose the work, and high throughput breaks ties only among comparably valuable ready units:*

| Season | Window | Theme |
|---|---|---|
| **S3** The Heart of the City → The Truce | now–Oct | the four beats below: characters, the door, the detective city, the era |
| **S4** The Six Iterations | Oct–Dec | the failed heavens: Paradise v1, the Nightmare v2, the cycles — prior Matrices as config-eras |
| **S5** The Real World | Dec–Feb | Zion the settlement · machine city 01 · sentinel ecology · the surface |
| **S6** The Crop | Feb–Apr | the scale ladder: 5k → 100k → 1,000,000 minds; metropolis science |
| **S7** The Analyst | Apr–Jun | the D-036 pocket: fear/desire A/B, the sheeple loop, modal worlds |
| **S8** The Sixty Years | Jun–Aug 2027 | the decades resolved; the anniversary; the Parity Ledger closes |

*Continuous programs, all year: the Chronicle (authored canon most days) · the Census (atlas science) · the Bench (instruments) · the Archaeology (MxO, Animatrix, comics, the games) · the Spec (data survives; closed math dies). Gates open progressively; v1 then deepen; every season ends with its skeptic, its release, its brief. Activity can break a tie inside this eligible queue after value and dependencies agree; it never populates or outranks the queue by itself.*

## Human Foundation — the subject before the character

*Accepted constitutional turn; tracker #1661. A Human is a causal subject: the world must pass through their situated experience, accumulated interpretation, intention, and persistent consequence. Further human and character runtime units wait for the contract's successor compatibility and decomposition gates; the contract remains deliberately smaller than a psychology model or rewrite. A separate process gate asks whether delivery serves that outcome or a contribution graph.*

| Gate | Decision | Status | Thread |
|---|---|---|---|
| Human-subject causal contract | D-062 | 🟢 accepted | #1662 |
| Quality admits; value leads; activity breaks ties | D-063 | 🟢 accepted | #1668 |
| Reciprocal debt: resistance belongs to mind; breach belongs to actor | D-064 | 🟢 accepted | #1670 |

**Decision DoD:** the Architect's acceptance is recorded in #1662, and D-062, the DECISIONS index, and this gate mirror it through docs-only PR #1666. Realization is decomposed under D-059 before any Java build unit is cut. Its future proof begins with an ordinary resident's full causal day — truth → situated perception → meaning/belief/memory → needs/values/goals → intent → committed consequence → biography → future perception — not with a named hero or an operator string.

**Process-decision DoD:** the Architect's quality-first, numbers-underneath verdict is recorded in #1668; D-063, the DECISIONS index and this gate mirror it in one acceptance commit. D-054's numeric floor is superseded; D-060 becomes an advisory tie-breaker among comparably valuable, independently justified ready units after human/world and dependency value; D-039/D-059 and rebase remain on coherence, falsifiability and history grounds. No contribution total admits work, outranks value or satisfies this gate.

**Debt-decision DoD:** the Architect's two-ledger verdict is recorded in #1670; D-064, the DECISIONS index, this gate and D-022's reciprocal disposition agree. Epistemic resistance belongs to the persistent Human and changes only through received evidence plus mind revision. Contract debt belongs to the accountable actor or institution toward named subjects and clauses and changes only through named breach/repair entries. Film residue and the anomaly ledger retain their historical meanings. Runtime realization waits for D-065/D-066 and the exact child decomposition.

## Season Three (D-041, accepted 2026-08-12) — the character layer

*The Architect's verdict on the finished miniature: the cell physics are done; nobody inside is anyone. Season Three grows characters ON TOP of the sealed engine — four ontological families (humans / machines / systems / programs), modeled separately, behaviored separately; sheets derived from identity; the meta entering the object. Named for the hotel where the film begins and where love overwrites death.*

**The floor (the Architect's bar, 2026-08-11): at least The Matrix Online.** Every MxO system is a row in the Parity Ledger (tracker #231); the season does not close while a row stands red. Measured on the system/mechanic/world axis; visual assets are not our axis (D-019). Ceiling: none.

*The season's second wing (the second origin text: "adı MxO ama içi engine"): the CITY itself. Old MxO's breadth was never crowd — it was districts with character, the Truce as a lived era, and live events that became canon. Our advantage over the original: their world died with its servers in 2009; ours is seed + log — unkillable, replayable, canon by construction. The character wing gives the city its people; the city wing gives the people their world.*

## v6.0 — The Heart of the City

*Beat one: the pen, the legend, the telescope. One new author-class enters the world per phase; this phase introduces AUTHORSHIP itself.*

| Gate | Decision | Status | Thread |
|---|---|---|---|
| Stat system: two-die law + permanent NEUTRAL control group | D-042 | 🟢 accepted 2026-08-12 (birth-seed law ruled 2026-08-11) | #212 |
| Authored history, minimal: birth cards + AUTHOR mark | D-050 | 🟢 accepted 2026-08-12 (the pen before the legend) | #225 |
| The named cast, born signed — and the two of us | D-043 | 🟢 accepted 2026-08-12 (the pair boards chronicle-only) | #213 |
| The dream reader — the telescope before the era | D-047 | 🟢 accepted 2026-08-12 (draft #230 adopts) | #217 |
| Season charter | D-041 | 🟢 accepted 2026-08-12 (four-beat structure adopted 2026-08-11) | #211 |
| The crew becomes programs | D-044 | 🟢 accepted 2026-08-12 (units ride v7.0) | #214 |

**DoD:** a NEUTRAL run bit-identical to pre-v6 main · an enabled run births the signed cast · the reader renders one full biography with zero world-writes.

## v6.5 — Program

*Beat two: the acceptance loop learns loyalty, love, and return. Named for the Animatrix short about wanting back in.*

| Gate | Decision | Status | Thread |
|---|---|---|---|
| The Cypher protocol — the door's inward swing | D-046 | 🟢 accepted 2026-08-12 (four-step machine + residual scar) | #216 |
| Allegiance + the influence ledger | D-051 | 🟢 accepted 2026-08-12 | #227 |
| Bonds & the Room 303 clause | D-045 | 🟢 accepted 2026-08-12 (price: bond consumed + ledger deposit, will untouched) | #215 |

**DoD:** one run shows `DOOR out` + `DOOR in` + one 303 firing with its ledger deposit · DrawMeter/DoorFlux PASS.

## v7.0 — A Detective Story

*Beat three: a dream with a plot, in a city with names.*

| Gate | Decision | Status | Thread |
|---|---|---|---|
| Districts with identity | D-048 | 🟢 accepted 2026-08-12 | #223 |
| Missions: templates as data, one executor | D-052 | 🟢 accepted 2026-08-12 | #228 |
| The favor economy: orphan insurance | D-053 | 🟢 accepted 2026-08-12 | #229 |

**DoD:** one operative followed through a full mission, rendered by the reader as narrative — the two wings shake hands in one command.

## v7.5 — The Truce

*Beat four: the era itself. Season One becomes the fallback attractor — the film is the era's error handler.*

| Gate | Decision | Status | Thread |
|---|---|---|---|
| The Truce as a regime + treaty/org scheduler | D-049 | 🟢 accepted 2026-08-12 | #224 |

**DoD:** a 20,000-tick corridor hold (`TREATY` strain < 1.0 throughout) · a stress-seed amendment via NEGOTIATION without reboot · a chronicle-bearing run replaying digest-identical.

Build units are cut AFTER verdicts (gates before units — the D-039 law).

---

### Process note

Delivery runs on the D-039 law: one **build-unit issue = one small PR** that closes it with keywords — atomic commits carrying finding, fix, and evidence; light locks per PR (compile · `--selftest` · digest, `--bench` where speed is touched); the **full adversarial skeptic pass at phase boundaries**. Gates before units: a phase's build units start only after its gate verdicts land. No phase skipping; even the Architect respects the cycle.
