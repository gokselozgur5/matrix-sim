# DECISIONS (index)

One row per decision; the full record (context, decision, consequences) lives in its ADR file under [docs/adr/](adr/). One rule stands: a 🟡 decision is **never merged into code before being discussed together** — discussion happens in the linked thread. 🟢 accepted · ❌ rejected · 🔵 idea parked.

| ID | Decision | Status | Gate | Thread |
|---|---|---|---|---|
| [D-000](adr/D-000-process-constitution.md) | Process: docs-first main, draft PRs, five-document canon | 🟢 | — | — |
| [D-001](adr/D-001-smith-infection-decorator.md) | Smith infection: Decorator vs State vs flag | 🟡 | v2.0 | #10 |
| [D-002](adr/D-002-agent-catch-mechanics.md) | Agent catch mechanics | 🟡 | v1.0 | #2 |
| [D-003](adr/D-003-deletion-refused-exception.md) | GC refusal as DeletionRefusedException | 🟡 | v2.0 | #11 |
| [D-004](adr/D-004-field-model.md) | Field model: grid vs continuous 2D | 🟡 | v1.0 | #3 |
| [D-005](adr/D-005-world-mutation.md) | World mutation: pending queues vs snapshot | 🟡 | v1.0 | #4 |
| [D-006](adr/D-006-arc-tuning.md) | Arc tuning constants | 🟡 | v3.0 | #17 |
| [D-007](adr/D-007-terminal-ui.md) | Terminal ANSI UI | ❌ | — | — |
| [D-008](adr/D-008-processor-mode.md) | Processor-mode mechanics | 🔵 | v4.0 | #19 |
| [D-009](adr/D-009-build-tooling.md) | Build: plain javac vs Gradle | 🟡 | v1.0 | #5 |
| [D-010](adr/D-010-determinism.md) | Determinism: seeded Rng only | 🟢 | — | — |
| [D-011](adr/D-011-human-class.md) | A real Human class in realworld | 🟡 | v1.0 | #6 |
| [D-012](adr/D-012-simulation-root.md) | Simulation root; PodFarm out of World | 🟡 | v1.0 | #7 |
| [D-013](adr/D-013-neurallink-bridge.md) | NeuralLink observer bridge replaces Avatar.brain | 🟡 | v1.0 | #8 |
| [D-014](adr/D-014-smith-lsp-violation.md) | AgentSmith as a documented LSP violation | 🟡 | v2.0 | #12 |
| [D-015](adr/D-015-species-as-data.md) | Species are data, never classes | 🟡 | v2.5 | #13 |
| [D-016](adr/D-016-movement-strategies.md) | Behavior = Movement strategy composition | 🟡 | v2.5 | #14 |
| [D-017](adr/D-017-spatial-hash.md) | Spatial hash grid for neighbor queries | 🟡 | v2.5 | #15 |
| [D-018](adr/D-018-tick-budgets.md) | Tick-rate scheduling and population caps | 🟡 | v2.5 | #16 |
| [D-019](adr/D-019-backend-only.md) | Backend only — no presentation in the domain | 🟢 | — | — |
| [D-020](adr/D-020-observability-contract.md) | Observability: event log + METRIC + DIGEST chain | 🟢 | — | — |
| [D-021](adr/D-021-perception-feed.md) | Perception feed as the true output | 🟡 | v1.0 (interface) | #9 |
| [D-022](adr/D-022-acceptance-loop.md) | Acceptance loop and the anomaly ledger | 🟡 | v3.0 | #18 |
| [D-023](adr/D-023-chronos-event-sourcing.md) | Chronos proper: event-sourced state, reload = replay | 🔵 | v4.0 | #27 |
| [D-024](adr/D-024-attention-lod.md) | Attention-graded fidelity | 🔵 | v4.0 | #28 |
| [D-025](adr/D-025-supervision-tree.md) | Supervision tree and the deletion protocol | 🟡 | v2.0 | #26 |
| [D-026](adr/D-026-language-java17.md) | Implementation language: Java 17 | 🟡 | v1.0 | #92 |
| [D-027](adr/D-027-performance-budgets.md) | Performance budgets, --bench, digest-invariant optimization | 🟡 | v1.0 | #93 |
| [D-028](adr/D-028-five-document-canon.md) | The doc canon grows to five; CLAUDE.md as the AI door | 🟢 | — | — |
| [D-029](adr/D-029-adr-expansion.md) | ADR files: one record per decision, DECISIONS.md becomes the index | 🟢 | — | — |
| [D-030](adr/D-030-agent-operating-model.md) | Multi-agent operating model: crews, supervision, proofs | 🟢 | — | — |

**Recorded assumption (will age badly, on purpose):** `processes accept SIGTERM`. The entire trilogy is the collapse of this one line; in this codebase the collapse has a name — `DeletionRefusedException`.
