# ROADMAP

Phases are numbered like the films. Each phase has three sections: **goal**, **decision gate** (decisions to close together before merge — see [docs/DECISIONS.md](docs/DECISIONS.md)) and **definition of done** (verified by a single command against the observability plane — D-020; there is no screen to look at, and that's the point — D-019).

---

## v0 — Construct ✅

*"This is the construct. It's our loading program."*

- [x] Repo, README, architecture UML + vision, decision log
- [x] Draft code opened as a draft PR (does not compile — on purpose; awaiting review)

**DoD:** These documents exist on main.

---

## v1.0 — The Matrix

*Core engine: the world turns, agents chase — and we watch it through logs, like operators do.*

- [ ] `core`: World (tick loop, pending mutation), seeded Rng, EventBus, SystemState
- [ ] `realworld`: Brain / Pod / PodFarm / NeuralLink — if the avatar dies, the brain dies
- [ ] `entities`: Avatar (blue/red), Agent — no presentation members anywhere (D-019)
- [ ] daemon bootstrap (`Main`) + ops console (stdin admin plane: `red`, `agent`, `pause`, `speed`, `quit`)
- [ ] observability: append-only event log, `METRIC` lines, `DIGEST` chain (D-020); `--follow <name>` perception sample (D-021 interface)

**Decision gate (11):** D-002 (agent catch), D-004 (grid), D-005 (mutation), D-009 (build), D-011/D-012/D-013 (Human, Simulation root, NeuralLink bridge), D-021 (perception interface), D-026 (language), D-027 (performance budgets), D-031 (SystemNode). The tracker issue's gate line is the live source of truth if this list ever lags. **All 11 gates closed by the owner's verdict, 2026-08-10.**

**DoD:** `java -cp out matrix.Main --headless --ticks 2000 --seed 42` run twice → **identical DIGEST chains**. `METRIC` lines show chase dynamics (red count oscillating under agent pressure).

---

## v2.0 — Reloaded

*Program society and the birth of the virus.*

- [ ] Oracle (EventBus observer + cookies), ExileProgram (mythology logs)
- [ ] `deja` command: hot patch + glitch events
- [ ] The Smith fork: Source.collect → `DeletionRefusedException` → SmithPrime + SmithCopy spread

**Decision gate:** D-001 (Decorator vs State — **the big debate of this phase**), D-003 (exception-as-control-flow), D-014 (the documented LSP violation)

**DoD:** A headless run shows, in order: the `"I DIDN'T"` log → `METRIC` infection fraction passing 0.50.

---

## v2.5 — The Animatrix

*The side stories: birds, flowers, insects, weather — the ecosystem. Everything is a program.*

- [ ] `Species` catalog + single `EnvironmentProgram` class (D-015)
- [ ] `Movement` strategies: FLOCK (boids), SWARM, ROOTED, DRIFT, WANDER (+ COMMUTE for blue-pill routines) (D-016)
- [ ] Spatial hash grid (D-017), tick-rate scheduling + population caps (D-018)
- [ ] Invisibility rule: healthy environment programs stay out of event noise; glitching ones surface

**Decision gate:** D-015, D-016, D-017, D-018

**DoD:** 500+ entities, deterministic (digest chains still identical), and a **flock-cohesion `METRIC`** (mean neighbor distance for FLOCK species) measurably below the random-walk baseline — proving the birds actually flock, no eyes needed.

---

## v3.0 — Revolutions

*The One cycle closes.*

- [ ] Acceptance loop + anomaly ledger replaces the flat counter (D-022) → birth of The One (a real pod in the PodFarm + jack-in)
- [ ] Overflow threshold → NEGOTIATION (world freezes, dialogue events) → treaty: mass restore, opt-out, Sati, PEACE
- [ ] Overflow without Neo → Architect emergency reload (alternate branch)
- [ ] `reload` command: manual Zion purge

**Decision gate:** D-006 (tuning constants), D-022 (acceptance loop bookkeeping)

**DoD:** `--headless --ticks 6000 --seed 42` output contains the full arc, in this order: `The One is born` → `I DIDN'T` → `OVERFLOW` → `"Peace."` → `REBOOT v7.0`.

---

## v4.0 — Resurrections (backlog)

*Out-of-band ideas; discussed when their time comes.*

- [ ] **Processor-mode mechanics:** node loss = compute loss → the Matrix drops "fps", glitches increase (D-008)
- [ ] Full perception feed: per-brain sensory frames streamed over the NeuralLink protocol; an external client can jack in as a real connection (D-021)
- [ ] JUnit: determinism + arc tests in CI
- [ ] The Zion side: hovercrafts, a pirate jack-in economy

---

### Process note

Every phase opens as a **draft PR** → file-by-file joint review → decisions closed in DECISIONS.md → the proof command is pasted into the PR description → merge. No phase skipping; even the Architect respects the cycle.
