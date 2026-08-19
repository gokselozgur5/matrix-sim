# DECISIONS (index)

One row per decision; the full MADR record (Y-statement, context, drivers, options with pros and cons, outcome, confirmation) lives under [docs/adr/](adr/) — next decision starts from [adr/TEMPLATE.md](adr/TEMPLATE.md). One rule stands: a 🟡 decision is **never merged into code before being discussed together** — discussion happens in the linked thread. Records are immutable: changed minds supersede, they do not rewrite. 🟢 accepted · 🟡 proposed · ❌ rejected · 🔵 parked idea.

<!-- figure: ls docs/adr/D-*.md | wc -l == 63 -->
<!-- figure: grep -c '^| .D-' docs/DECISIONS.md == 63 -->

| ID | Decision | Status | Gate | Thread |
|---|---|---|---|---|
| [D-000](adr/D-000-process-constitution.md) | Process constitution: docs-first main, draft PRs, joint decision gates | 🟢 | — | — |
| [D-001](adr/D-001-smith-infection-decorator.md) | Smith infection mechanics: Decorator vs State vs flag | 🟢 | v2.0 | #10 |
| [D-002](adr/D-002-agent-catch-mechanics.md) | Agent catch mechanics: replug ratio and lethality | 🟢 | v1.0 | #2 |
| [D-003](adr/D-003-deletion-refused-exception.md) | GC refusal as a thrown DeletionRefusedException | 🟢 | v2.0 | #11 |
| [D-004](adr/D-004-field-model.md) | Field model: fixed-point city coordinates + a place graph | 🟢 | v1.0 | #3 |
| [D-005](adr/D-005-world-mutation.md) | World mutation: pending queues vs snapshot vs double buffer | 🟢 | v1.0 | #4 |
| [D-006](adr/D-006-arc-tuning.md) | Arc tuning constants live in Config and are tuned by METRIC feel | 🟢 | v3.0 | #17 |
| [D-007](adr/D-007-terminal-ui.md) | Terminal ANSI UI for watching the simulation | ❌ | — | — |
| [D-008](adr/D-008-processor-mode.md) | Processor-mode mechanics: pods as the Matrix's compute substrate | 🟢 | v5.0 | #19 |
| [D-009](adr/D-009-build-tooling.md) | Build tooling: plain javac until CI forces the question | 🟢 | v1.0 | #5 |
| [D-010](adr/D-010-determinism.md) | Determinism: one seeded Rng, no wall clock, no unordered iteration | 🟢 | — | — |
| [D-011](adr/D-011-human-class.md) | Human as a first-class real-world entity | 🟢 | v1.0 | #6 |
| [D-012](adr/D-012-simulation-root.md) | Simulation as the only composition root; PodFarm leaves World | 🟢 | v1.0 | #7 |
| [D-013](adr/D-013-neurallink-bridge.md) | Death propagates over a NeuralLink observer bridge, not an Avatar.brain field | 🟢 | v1.0 | #8 |
| [D-014](adr/D-014-smith-lsp-violation.md) | AgentSmith stays a protected Liskov violation | 🟢 | v2.0 | #12 |
| [D-015](adr/D-015-species-as-data.md) | Species are catalog data; classes are for behavior only | 🟢 | v2.5 | #13 |
| [D-016](adr/D-016-movement-strategies.md) | Behavior variety via Movement strategy composition | 🟢 | v2.5 | #14 |
| [D-017](adr/D-017-spatial-hash.md) | Neighbor queries via a bucketed spatial hash | 🟢 | v2.5 | #15 |
| [D-018](adr/D-018-tick-budgets.md) | Tick-rate scheduling and per-species population caps | 🟢 | v2.5 | #16 |
| [D-019](adr/D-019-backend-only.md) | Backend only: no presentation anywhere in the domain | 🟢 | — | — |
| [D-020](adr/D-020-observability-contract.md) | Observability contract: event log + METRIC lines + DIGEST chain | 🟢 | — | — |
| [D-021](adr/D-021-perception-feed.md) | The perception feed is the system's true output | 🟢 | v1.0 (interface) | #9 |
| [D-022](adr/D-022-acceptance-loop.md) | The acceptance loop and anomaly ledger replace the flat counter | 🟢 | v3.0 | #18 |
| [D-023](adr/D-023-chronos-event-sourcing.md) | Chronos proper: event-sourced state, reload as replay | 🟢 | v4.5 | #27 |
| [D-024](adr/D-024-attention-lod.md) | Attention-graded fidelity: unwatched regions degrade to statistics | 🟢 | v5.0 | #28 |
| [D-025](adr/D-025-supervision-tree.md) | Supervisor-lite: grace periods and an orphan registry under the Source | 🟢 | v2.0 | #26 |
| [D-026](adr/D-026-language-java17.md) | Implementation language: Java 17 on the JVM | 🟢 | v1.0 | #92 |
| [D-027](adr/D-027-performance-budgets.md) | Performance budgets, --bench mode, and the digest-invariant optimization rule | 🟢 | v1.0 | #93 |
| [D-028](adr/D-028-five-document-canon.md) | The document canon grows to five; CLAUDE.md is the AI door | 🟢 | — | — |
| [D-029](adr/D-029-adr-expansion.md) | ADRs: one MADR record per decision; DECISIONS.md becomes the index | 🟢 | — | — |
| [D-030](adr/D-030-agent-operating-model.md) | Multi-agent operating model: bounded crews, proofs, the digest leash | 🟢 | — | — |
| [D-031](adr/D-031-system-of-systems.md) | System-of-systems: SystemNode composite under the Simulation root | 🟢 | v1.0 (interface), v4.0 (Zion) | #94 |
| [D-032](adr/D-032-pirate-broadcast.md) | Zion fleet and the pirate broadcast jack-in path | 🟢 | v4.0 | #95 |
| [D-033](adr/D-033-self-substantiation.md) | Self-substantiation (the Kid): resistance overflow force-disconnect | 🟢 | v4.0 | #96 |
| [D-034](adr/D-034-session-ritual.md) | The session ritual: jack in, work under gates, exit through a hardline | 🟢 | — | — |
| [D-035](adr/D-035-lens-catalog.md) | The lens catalog: asking the right question | 🟢 | — | — |
| [D-036](adr/D-036-finish-line.md) | The finish line and the scope contract | 🟢 | — | — |
| [D-037](adr/D-037-theory-practice-split.md) | Division of labor: the Architect holds theory, the Oracle holds practice | 🟢 | — | — |
| [D-038](adr/D-038-season-two.md) | Season Two: the epilogue graduates, the project goes public | 🟢 | — | — |
| [D-039](adr/D-039-unit-pr-granularity.md) | Delivery granularity: unit PRs, proportional locks | 🟢 | — | — |
| [D-040](adr/D-040-ci-and-junit.md) | CI as a runner now, JUnit deferred with a named trigger | 🟢 | v4.5 | #137 |
| [D-041](adr/D-041-season-three-character-layer.md) | Season Three: the character layer atop the sealed physics | 🟢 | v6.0 | #211 |
| [D-042](adr/D-042-stat-system.md) | Stats: one contest grammar, four family vocabularies | 🟢 | v6.0 | #212 |
| [D-043](adr/D-043-named-cast.md) | The named cast — and the Architect and Oracle in-world | 🟢 | v6.0 | #213 |
| [D-044](adr/D-044-crew-as-programs.md) | The crew enters the world as program society | 🟢 | v6.0 | #214 |
| [D-045](adr/D-045-bonds-and-the-kiss.md) | Bonds, and the Room 303 clause — love as bookkeeping | 🟢 | v6.5 | #215 |
| [D-046](adr/D-046-cypher-protocol.md) | The Cypher protocol: the door's inward direction | 🟢 | v6.5 | #216 |
| [D-047](adr/D-047-dream-reader.md) | The dream reader: a teleprinter for one mind's day | 🟢 | v6.0 | #217 |
| [D-048](adr/D-048-districts-with-identity.md) | Districts with identity: the city's quarters mean something | 🟢 | v7.0 | #223 |
| [D-049](adr/D-049-truce-regime.md) | The Truce as a regime: the untold sixty years, playable | 🟢 | v7.5 | #224 |
| [D-050](adr/D-050-live-events.md) | Live events as signed chronos entries: authored history | 🟢 | v6.0 | #225 |
| [D-051](adr/D-051-allegiance-influence.md) | Allegiance as data; the influence ledger as political weather | 🟢 | v6.5 | #227 |
| [D-052](adr/D-052-missions.md) | Missions: template rows, one executor, a dream with a plot | 🟢 | v7.0 | #228 |
| [D-053](adr/D-053-favor-economy.md) | The favor economy: orphan insurance, conservation by construction | 🟢 | v7.0 | #229 |
| [D-054](adr/D-054-the-year.md) | The Year: six seasons, five programs, scale to 365 | 🟢 | all | #232 |
| [D-058](adr/D-058-spec-shelf.md) | The spec shelf: docs/spec/ becomes a sanctioned document kind | 🟢 | all | #252 |
| [D-059](adr/D-059-issue-tree.md) | The issue tree: work branches until a leaf is one PR | 🟢 | all | #358 |
| [D-060](adr/D-060-the-balance-law.md) | The balance law: four quarters, and the meter that proves it | 🟢 | all | #781 |
| [D-061](adr/D-061-merge-strategy.md) | The merge strategy is a term of the balance law: rebase, and the meter reads the button | 🟢 | all | #911 |
| [D-062](adr/D-062-human-subject-contract.md) | The human is the subject: causal agency before character mechanics | 🟢 | Human Foundation | #1662 |
| [D-063](adr/D-063-outcome-first-delivery.md) | Outcomes first: the graph witnesses delivery, never directs it | 🟢 | Human Foundation | #1668 |
| [D-064](adr/D-064-reciprocal-debt.md) | Reciprocal debt: resistance belongs to the mind; breach belongs to the actor | 🟢 | Human Foundation | #1670 |
| [D-065](adr/D-065-inhabited-finish-line.md) | The film is the specimen; inhabited causality is the finish line | 🟢 | Human Foundation | #1671 |

D-063 keeps its yellow proposal title above as the stable historical identifier. Its accepted-title alias and current law are **“Quality admits; value leads; activity breaks ties”**: the graph never admits or outranks work, but may choose between comparably valuable ready units after value and dependencies leave a genuine tie.

**The D-055–D-057 gap.** No record was ever written for D-055, D-056 or D-057, and outside this paragraph one line in the repository names them — `grep -rn 'D-05[567]' . --exclude=DECISIONS.md` returns `probes/DocLint.java`, whose javadoc lists the three numbers to say which gap it is checking for, so the only citation of the missing numbers belongs to the probe that enforces this paragraph; nothing depends on them as decisions. The gap is a numbering artifact, not three lost or superseded decisions: numbers were being claimed ahead of their records while Season Three's dossiers were still landing, and `git log --diff-filter=A --date=iso -- docs/adr/` shows the process records arriving out of numeric order — D-059 at 2026-08-11 15:20, D-060 at 2026-08-12 04:18, D-058 at 2026-08-12 04:22 — with 055, 056 and 057 skipped and never written. The three numbers stay unissued. A D-number is a citation key used by issue threads, commit subjects and cross-record links, so reusing one would make an old citation ambiguous — D-061 took the next number and D-062 took the one after it, and this gap is closed by an explanation, never by renumbering.

**Recorded assumption (will age badly, on purpose):** `processes accept SIGTERM`. The entire trilogy is the collapse of this one line; in this codebase the collapse has a name — `DeletionRefusedException`.
