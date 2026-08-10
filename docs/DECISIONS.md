# DECISIONS (ADR-lite)

One table, one rule: a 🟡 decision is **never merged into code before being discussed together**. A closed decision becomes 🟢 (accepted) or ❌ (rejected) with a one-line rationale. 🔵 is an idea whose time hasn't come.

| ID | Decision | Status | Summary / open question |
|---|---|---|---|
| D-000 | Process: main is docs-first, every phase a draft PR, four-document policy | 🟢 | This repo's constitution; established at the owner's request. |
| D-001 | Smith infection = **Decorator** (`SmithCopy` keeps the original inside) | 🟡 | The restore guarantee lives in the type system. Alternatives: State pattern, an `infected` flag. v2.0 gate. |
| D-002 | Agent catch: 90% replug / 10% terminate (→ pod flush) | 🟡 | Tune the ratio, or a different mechanic altogether? v1.0 gate. |
| D-003 | GC refusal = `DeletionRefusedException` (exception-as-control-flow) | 🟡 | True to the lore but a classic anti-pattern. Deliberate sin or refactor? v2.0 gate. |
| D-004 | Field: 72×20 grid + Chebyshev adjacency | 🟡 | Simple and deterministic. Alternative: continuous 2D coordinates. v1.0 gate. |
| D-005 | World mutation: pending add/remove queues | 🟡 | Safe during iteration. Alternative: immutable snapshot / double buffer. v1.0 gate. |
| D-006 | Tuning: overflow 62%, Smith fork +350 ticks, peace 900 ticks | 🟡 | Numbers live in `Config`; tuned together by arc feel. v3.0 gate. |
| D-007 | Interface: terminal ANSI + stdin line commands | ❌ | Rejected by the owner (2026-08-10): there is no frontend. Superseded by D-019/D-020; stdin survives only as the ops console (an admin plane, not a UI). |
| D-008 | Processor-mode mechanics: node loss = compute loss → the Matrix drops "fps" | 🔵 | Turns the battery-vs-processor theory from flavor into mechanics. v4.0 idea. |
| D-009 | Build: plain `javac`, zero dependencies | 🟡 | Reopened when a Gradle/JUnit need arises (linked to the v4.0 CI idea). |
| D-010 | Determinism: seeded `Rng`; bare `Random`/`System.time` banned | 🟢 | Same seed → same film; replay and tests build on this. |
| D-011 | `Human` becomes a real class in `realworld` (owns `Brain`, may hold a `NeuralLink`) | 🟡 | Unplugged humans become representable; treaty opt-out = liberation, not deletion. Lean: yes. |
| D-012 | `Simulation` root object; `PodFarm` moves out of `World` | 🟡 | The Matrix must not contain the real world — the universe contains both. Fixes an inverted ownership in the draft. |
| D-013 | Drop `Avatar.brain`; death propagates via a NeuralLink observer bridge | 🟡 | `entities` imports nothing from `realworld`; the death rule becomes a property of the connection — which is exactly the lore. |
| D-014 | `AgentSmith`'s contract break is a documented LSP violation | 🟡 | The crisis mechanic IS a subtype breaking its parent's contract. Smith is a walking Liskov violation; keep it, document it. |
| D-015 | Species are **data** (`Species` catalog), never classes | 🟡 | A class is opened only for a behavioral difference. A thousand species = a thousand catalog rows, one `EnvironmentProgram` class. |
| D-016 | Behavior = Strategy composition (`Movement`: FLOCK, SWARM, ROOTED, DRIFT, WANDER, COMMUTE) | 🟡 | New behavior = new strategy plugged into the catalog; also drives blue-pill avatar routines. No subclass explosion. |
| D-017 | Spatial hash grid for neighbor queries | 🟡 | O(n) scans die at ecosystem scale. Bucketed lookup keeps the tick budget flat. |
| D-018 | Tick-rate scheduling + per-species population caps | 🟡 | Flowers barely think, birds think often, nobody exceeds their budget. First step toward attention-graded fidelity. |
| D-019 | **Backend only.** No presentation types anywhere in the domain; `glyph`/`color`/`renderPriority` purged from the entity API | 🟢 | Owner's call (2026-08-10): "we are coding the Matrix itself, just its backend." The Matrix's real output is the dream, not a screen. |
| D-020 | Observability contract: append-only event log + `METRIC` lines + `DIGEST` chain (canonical state hash every N ticks) | 🟢 | Every DoD asserts on these. Two runs, same seed → identical digest chain; a diff pinpoints the tick where reality diverged. |
| D-021 | Perception feed: per-brain sensory frames over NeuralLink are the system's true output | 🟡 | v1 ships the interface + `--follow <name>` (log one brain's dream); the full stream is backlog. |
| D-022 | Acceptance loop replaces the flat anomaly counter | 🟡 | Each link: propose → accept/resist; resistance residue accrues in an anomaly ledger. The One's birth becomes bookkeeping, not a constant. v3.0 gate. |
| D-023 | Chronos proper: event-sourced state, snapshots, reload = replay | 🔵 | Today objects are the state and the log is observability; the Vision inverts that. Revisit once v3.0's reload works the simple way. |
| D-024 | Attention-graded fidelity: unwatched regions are not simulated | 🔵 | The lazy-reality principle as mechanics; D-018's budgets are the embryo. v4.0 idea. |
| D-025 | Supervision tree and the deletion protocol (SIGTERM + grace period + orphan registry) | 🟡 | The Source becomes a real supervisor; exiles become tracked orphans instead of ad-hoc dodgers. v2.0 gate. |

**Recorded assumption (will age badly, on purpose):** `processes accept SIGTERM`. The entire trilogy is the collapse of this one line; in this codebase the collapse has a name — `DeletionRefusedException`.
