# ROADMAP

Phases are numbered like the films. Each phase has three sections: **goal**, **decision gate** (decisions to close together before merge — see [docs/DECISIONS.md](docs/DECISIONS.md)) and **definition of done** (verified by a single command).

---

## v0 — Construct ✅

*"This is the construct. It's our loading program."*

- [x] Repo, README, architecture UML, decision log
- [x] Draft code opened as a draft PR (does not compile — on purpose; awaiting review)

**DoD:** These documents exist on main.

---

## v1.0 — The Matrix

*Core engine: the world turns, agents chase.*

- [ ] `core`: World (tick loop, pending mutation), seeded Rng, EventBus, SystemState
- [ ] `realworld`: Brain / Pod / PodFarm / NeuralLink — if the avatar dies, the brain dies
- [ ] `entities`: Avatar (blue/red), Agent
- [ ] `sim`: terminal ANSI renderer + stdin commands (`red`, `agent`, `pause`, `speed`, `quit`)

**Decision gate:** D-002 (agent catch mechanics), D-004 (grid), D-005 (mutation), D-007 (interface), D-009 (build)

**DoD:** `java -cp out matrix.Main --headless --ticks 2000 --seed 42` → two runs produce **bit-identical logs**. Live mode shows the blue/red/agent chase.

---

## v2.0 — Reloaded

*Program society and the birth of the virus.*

- [ ] WorkerProgram (invisibility rule), Oracle (EventBus observer + cookies), ExileProgram (mythology logs)
- [ ] `deja` command: live patch + glitch
- [ ] The Smith fork: Source.collect → `DeletionRefusedException` → SmithPrime + SmithCopy spread

**Decision gate:** D-001 (Decorator vs State — **the big debate of this phase**), D-003 (exception-as-control-flow)

**DoD:** A headless run shows, in order: the `"I DIDN'T"` log → infection fraction passing 50%.

---

## v3.0 — Revolutions

*The One cycle closes.*

- [ ] Anomaly metric → birth of The One (a real pod in the PodFarm + jack-in)
- [ ] Overflow threshold → NEGOTIATION (world freezes, dialogue logs) → treaty: mass restore, opt-out, Sati, PEACE
- [ ] Overflow without Neo → Architect emergency reload (alternate branch)
- [ ] `reload` command: manual Zion purge

**Decision gate:** D-006 (tuning constants: 62% / 350 / 900)

**DoD:** `--headless --ticks 6000 --seed 42` output contains the full arc, in this order: `The One is born` → `I DIDN'T` → `OVERFLOW` → `"Peace."` → `REBOOT v7.0`.

---

## v4.0 — Resurrections (backlog)

*Out-of-band ideas; discussed when their time comes.*

- [ ] **Processor-mode mechanics:** node loss = compute loss → the Matrix drops "fps", glitches increase (D-008)
- [ ] JUnit: determinism + arc tests in CI
- [ ] The Zion side: hovercrafts, a pirate jack-in economy
- [ ] Maybe: Swing/JavaFX visualization (the terminal stays the primary interface)

---

### Process note

Every phase opens as a **draft PR** → file-by-file joint review → decisions closed in DECISIONS.md → the proof command is pasted into the PR description → merge. No phase skipping; even the Architect respects the cycle.
