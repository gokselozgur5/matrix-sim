# DECISIONS (ADR-lite)

One table, one rule: a 🟡 decision is **never merged into code before being discussed together**. A closed decision becomes 🟢 with a one-line rationale next to it. 🔵 is an idea whose time hasn't come.

| ID | Decision | Status | Summary / open question |
|---|---|---|---|
| D-000 | Process: main is docs-first, every phase a draft PR, four-document policy | 🟢 | This repo's constitution; established at the owner's request. |
| D-001 | Smith infection = **Decorator** (`SmithCopy` keeps the original inside) | 🟡 | The restore guarantee lives in the type system. Alternatives: State pattern, an `infected` flag. v2.0 gate. |
| D-002 | Agent catch: 90% replug / 10% terminate (→ pod flush) | 🟡 | Tune the ratio, or a different mechanic altogether? v1.0 gate. |
| D-003 | GC refusal = `DeletionRefusedException` (exception-as-control-flow) | 🟡 | True to the lore but a classic anti-pattern. Deliberate sin or refactor? v2.0 gate. |
| D-004 | Field: 72×20 grid + Chebyshev adjacency | 🟡 | Terminal-native and simple. Alternative: continuous 2D coordinates. v1.0 gate. |
| D-005 | World mutation: pending add/remove queues | 🟡 | Safe during iteration. Alternative: immutable snapshot / double buffer. v1.0 gate. |
| D-006 | Tuning: overflow 62%, Smith fork +350 ticks, peace 900 ticks | 🟡 | Numbers live in `Config`; tuned together by arc feel. v3.0 gate. |
| D-007 | Interface: terminal ANSI + stdin line commands | 🟡 | SSH-friendly, zero dependencies. Alternative: Swing/JavaFX (can be pushed to v4.0). v1.0 gate. |
| D-008 | Processor-mode mechanics: node loss = compute loss → the Matrix drops "fps" | 🔵 | Turns the battery-vs-processor theory from flavor into mechanics. v4.0 idea. |
| D-009 | Build: plain `javac`, zero dependencies | 🟡 | Reopened when a Gradle/JUnit need arises (linked to the v4.0 CI idea). |
| D-010 | Determinism: seeded `Rng`; bare `Random`/`System.time` banned | 🟢 | Same seed → same film; replay and tests build on this. Reopened on objection. |
