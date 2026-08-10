# DECISIONS (index)

One row per decision; the full MADR record (Y-statement, context, drivers, options with pros and cons, outcome, confirmation) lives under [docs/adr/](adr/) — next decision starts from [adr/TEMPLATE.md](adr/TEMPLATE.md). One rule stands: a 🟡 decision is **never merged into code before being discussed together** — discussion happens in the linked thread. Records are immutable: changed minds supersede, they do not rewrite. 🟢 accepted · 🟡 proposed · ❌ rejected · 🔵 parked idea.

| ID | Decision | Status | Gate | Thread |
|---|---|---|---|---|
| [D-000](adr/D-000-process-constitution.md) | Process constitution: docs-first main, draft PRs, joint decision gates | 🟢 | — | — |
| [D-001](adr/D-001-smith-infection-decorator.md) | Smith infection mechanics: Decorator vs State vs flag | 🟡 | v2.0 | #10 |
| [D-002](adr/D-002-agent-catch-mechanics.md) | Agent catch mechanics: replug ratio and lethality | 🟡 | v1.0 | #2 |
| [D-003](adr/D-003-deletion-refused-exception.md) | GC refusal as a thrown DeletionRefusedException | 🟡 | v2.0 | #11 |
| [D-004](adr/D-004-field-model.md) | Field model: integer grid vs continuous 2D plane | 🟡 | v1.0 | #3 |
| [D-005](adr/D-005-world-mutation.md) | World mutation: pending queues vs snapshot vs double buffer | 🟡 | v1.0 | #4 |
| [D-006](adr/D-006-arc-tuning.md) | Arc tuning constants live in Config and are tuned by METRIC feel | 🟡 | v3.0 | #17 |
| [D-007](adr/D-007-terminal-ui.md) | Terminal ANSI UI for watching the simulation | ❌ | — | — |
| [D-008](adr/D-008-processor-mode.md) | Processor-mode mechanics: pods as the Matrix's compute substrate | 🔵 | v4.0 | #19 |
| [D-009](adr/D-009-build-tooling.md) | Build tooling: plain javac until CI forces the question | 🟡 | v1.0 | #5 |
| [D-010](adr/D-010-determinism.md) | Determinism: one seeded Rng, no wall clock, no unordered iteration | 🟢 | — | — |
| [D-011](adr/D-011-human-class.md) | Human as a first-class real-world entity | 🟡 | v1.0 | #6 |
| [D-012](adr/D-012-simulation-root.md) | Simulation as the only composition root; PodFarm leaves World | 🟡 | v1.0 | #7 |
| [D-013](adr/D-013-neurallink-bridge.md) | Death propagates over a NeuralLink observer bridge, not an Avatar.brain field | 🟡 | v1.0 | #8 |
| [D-014](adr/D-014-smith-lsp-violation.md) | AgentSmith stays a protected Liskov violation | 🟡 | v2.0 | #12 |
| [D-015](adr/D-015-species-as-data.md) | Species are catalog data; classes are for behavior only | 🟡 | v2.5 | #13 |
| [D-016](adr/D-016-movement-strategies.md) | Behavior variety via Movement strategy composition | 🟡 | v2.5 | #14 |
| [D-017](adr/D-017-spatial-hash.md) | Neighbor queries via a bucketed spatial hash | 🟡 | v2.5 | #15 |
| [D-018](adr/D-018-tick-budgets.md) | Tick-rate scheduling and per-species population caps | 🟡 | v2.5 | #16 |
| [D-019](adr/D-019-backend-only.md) | Backend only: no presentation anywhere in the domain | 🟢 | — | — |
| [D-020](adr/D-020-observability-contract.md) | Observability contract: event log + METRIC lines + DIGEST chain | 🟢 | — | — |
| [D-021](adr/D-021-perception-feed.md) | The perception feed is the system's true output | 🟡 | v1.0 (interface) | #9 |
| [D-022](adr/D-022-acceptance-loop.md) | The acceptance loop and anomaly ledger replace the flat counter | 🟡 | v3.0 | #18 |
| [D-023](adr/D-023-chronos-event-sourcing.md) | Chronos proper: event-sourced state, reload as replay | 🔵 | v4.0 | #27 |
| [D-024](adr/D-024-attention-lod.md) | Attention-graded fidelity: unwatched regions degrade to statistics | 🔵 | v4.0 | #28 |
| [D-025](adr/D-025-supervision-tree.md) | Supervisor-lite: grace periods and an orphan registry under the Source | 🟡 | v2.0 | #26 |
| [D-026](adr/D-026-language-java17.md) | Implementation language: Java 17 on the JVM | 🟡 | v1.0 | #92 |
| [D-027](adr/D-027-performance-budgets.md) | Performance budgets, --bench mode, and the digest-invariant optimization rule | 🟡 | v1.0 | #93 |
| [D-028](adr/D-028-five-document-canon.md) | The document canon grows to five; CLAUDE.md is the AI door | 🟢 | — | — |
| [D-029](adr/D-029-adr-expansion.md) | ADRs: one MADR record per decision; DECISIONS.md becomes the index | 🟢 | — | — |
| [D-030](adr/D-030-agent-operating-model.md) | Multi-agent operating model: bounded crews, proofs, the digest leash | 🟢 | — | — |
| [D-031](adr/D-031-system-of-systems.md) | System-of-systems: SystemNode composite under the Simulation root | 🟡 | v1.0 (interface), v4.0 (Zion) | #94 |
| [D-032](adr/D-032-pirate-broadcast.md) | Zion fleet and the pirate broadcast jack-in path | 🟡 | v4.0 | #95 |
| [D-033](adr/D-033-self-substantiation.md) | Self-substantiation (the Kid): resistance overflow force-disconnect | 🔵 | v4.0 | #96 |
| [D-034](adr/D-034-session-ritual.md) | The session ritual: jack in, work under gates, exit through a hardline | 🟢 | — | — |
| [D-035](adr/D-035-lens-catalog.md) | The lens catalog: asking the right question | 🟢 | — | — |

**Recorded assumption (will age badly, on purpose):** `processes accept SIGTERM`. The entire trilogy is the collapse of this one line; in this codebase the collapse has a name — `DeletionRefusedException`.
