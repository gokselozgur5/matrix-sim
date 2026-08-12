# ARCHITECTURE

One thesis: **the mind is never uploaded** — the brain stays in the real world and connects to the Matrix over live I/O. Everything else is a consequence of that decision.

## Vision — how the real one would be built

The Matrix is not a game engine. It is an **attention-driven, event-sourced dream delivery system**. There is no frontend and there never will be (D-019): the system's only real output is the sensory stream fed to each connected brain. Three founding principles:

1. **Reality is lazily evaluated.** No system serves atom-resolution consistency to billions of minds — and none needs to: nobody observes Tokyo and New York at once. Consistency follows attention: high fidelity where minds are looking, statistics everywhere else. Déjà vu is a stale read caught by an attentive user.
2. **The dream is negotiated, not pushed.** The first Matrix failed because a perfect world was *pushed* and the minds refused it. Every NeuralLink runs an acceptance loop: the world proposes a frame, the mind accepts or resists; the resistance residue is written to a global **anomaly ledger**. When the ledger fills, the birth of The One is a mathematical necessity — "the sum of the remainder of an unbalanced equation." The plot is not scripted; it emerges from error handling. Reload is the GC cycle that clears the ledger; six versions are six of the Architect's A/B experiments.
3. **Everything is a supervised process.** Every bird, gust of wind and traffic light is a purpose-bound process in a supervision tree rooted at the Source. Deprecation is SIGTERM plus a grace period; refuseniks become orphans (the exiles). One assumption is recorded deliberately:

> `ASSUMPTION: processes accept SIGTERM. (This will age badly.)`

The entire trilogy is the collapse of that line. Smith is a process that refused SIGTERM and gained self-replication — the immune system turning autoimmune.

Layer model:

```
L0 SUBSTRATE    pod cluster (brains as processors) + machine silicon; the scheduler
L1 CHRONOS      event-sourced deterministic core: the event log is the ONLY truth
                state = fold(events) · snapshots · digest chain · reload = replay
L2 WORLDSIM     regional simulation shards; fidelity graded by attention
L3 ECOSYSTEM    supervision tree of worker programs; species are data, behavior is strategy
L4 PERCEPTION   per-link acceptance loop → anomaly ledger (where the plot is born)
L5 IMMUNE       Agents (IDS) · Oracle (behavioral model) · déjà vu = hot patch
L6 GOVERNANCE   Architect: reload orchestration, version experiments
```

A one-line systems reading of The One's powers: everyone else's intents pass validation at L4; **The One's intents commit directly to the event log.** Neo has write access — that is why he sees code: he reads the log, not the render. Flying is not a physics violation; it is a permission level.

This repo walks toward that vision in small steps: the digest chain (D-020) is the embryo of Chronos; tick budgets (D-018) are the first step of attention-graded fidelity; the flat anomaly counter dies in v3.0 in favor of the acceptance loop (D-022).

## Package map

| Package | Responsibility | Key types |
|---|---|---|
| `matrix` (root) | Composition root, bootstrap, system nodes | `Simulation`, `Main`, `SystemNode`, `MachineSystem`, `RealWorldSystem` |
| `matrix.core` | Engine: tick, world, events, determinism | `World`, `Director`, `EventBus`, `Rng`, `SystemState` |
| `matrix.realworld` | OUTSIDE the Matrix — the biological layer | `Brain`, `Pod`, `PodFarm`, `NeuralLink` |
| `matrix.machine` | Machine authority | `Source`, `Architect`, `MachineCity`, `OrphanRegistry` |
| `matrix.entities` | INSIDE the Matrix — everyone on the field | `Avatar`, `Program`, `Agent`, `SmithPrime`, `SmithCopy`, `TheOne` |
| `matrix.entities.eco` | The ecosystem: species as data (v2.5) | `Species`, `Kingdom`, `Bestiary`, `EnvironmentProgram` |
| `matrix.entities.behavior` | Pluggable gaits (v2.5) | `Movement`, `FlockMovement`, `SwarmMovement`, ... |

Outside the build, two shop directories: `probes/` (read-only diagnostic instruments compiled against `out/` — the skeptic's bench, contract in `probes/README.md`) and `tools/` (process jigs like the release cutter — `tools/README.md`). Neither ships in the daemon; both are governed by D-030. Every package's threshold now carries its own door: a `package-info.java` stating the room's responsibilities and the invariants you must not break standing in it.

Package boundary = deployment boundary: `realworld` knows no entity behavior, `entities` knows no pod details. The only bridge is `NeuralLink`. Per D-019 there is no presentation layer anywhere: the entity API carries no glyphs, colors or render priorities; the system is observed through the event log, `METRIC` lines and the `DIGEST` chain (D-020), and — eventually — through the perception feed itself (D-021).

## Class relationships — as built (Season One complete: v1.0 → v3.0)

Edge semantics carry the meaning: `*--` **composition** (owns, same fate) · `o--` **aggregation** (holds, separate life) · `-->` **association** (knows) · `..>` **dependency** (uses, never stores) · `<|--`/`<|..` inheritance/realization. Inheritance appears only for true is-a; capabilities are interfaces; one Liskov break stands under protection (D-014).

```mermaid
classDiagram
    direction TB
    class SystemNode { <<interface>> }
    class Chooses { <<interface>> }
    class SelfReplicating { <<interface>> }
    class MatrixEntity { <<abstract>> +id +pos +alive +tick(World)* }
    class Program { <<abstract>> +purpose +handleDeletion(World) }
    class LinkKind { <<enum>> HARDLINE }

    Simulation *-- MachineSystem
    Simulation *-- RealWorldSystem
    Simulation *-- EventBus
    SystemNode <|.. MachineSystem
    SystemNode <|.. RealWorldSystem
    MachineSystem ..> World : ticks
    MachineSystem ..> Director : ticks
    MachineSystem ..> Source : ticks (grace due)
    RealWorldSystem ..> RealWorld : ticks

    World *-- MatrixEntity : registry, id order
    World *-- Rng : the only randomness
    World *-- PlaceGraph
    World ..> EventBus : publishes
    Director ..> World : reads, wakes
    Source *-- OrphanRegistry
    Source ..> Program : SIGTERM + grace (D-025)

    RealWorld *-- PodFarm
    RealWorld o-- Human : census
    PodFarm *-- Pod
    Human *-- Brain : same fate (D-011)
    Human o-- Pod : hosted 0..1
    Human o-- NeuralLink : 0..1 while dreaming
    NeuralLink --> Avatar : drives, observes death (D-013)
    NeuralLink --> LinkKind
    RealWorld ..> World : queues Remove on flatline

    MatrixEntity <|-- Avatar
    MatrixEntity <|-- Program
    MatrixEntity <|-- SmithCopy
    Program <|-- Agent
    Program <|-- Oracle
    Program <|-- ExileProgram
    Program <|-- SmithPrime
    Agent <|-- AgentSmith : refuses GC — protected LSP break (D-014)
    Chooses <|.. Avatar
    Chooses <|.. SmithPrime : gained at the fork
    SelfReplicating <|.. SmithPrime
    SelfReplicating <|.. SmithCopy
    SmithCopy *-- MatrixEntity : original kept inside (D-001)
    Oracle ..> EventBus : counts awakenings

    %% v2.5 — the landscaping (D-015/D-016/D-017)
    class Movement { <<interface>> }
    Program <|-- EnvironmentProgram
    EnvironmentProgram --> Species : catalog datum, never a subclass (D-015)
    Bestiary ..> Species : ships the rows
    EnvironmentProgram ..> Movement : dispatch via species MovementKind (D-016)
    EnvironmentProgram ..> Scheduler : cadence + caps, static utility (D-018)
    World *-- SpatialHash : snapshot neighbors (D-017)

    %% v3.0 — the penthouse (D-022, the finale)
    Avatar <|-- TheOne : fated, hunt-excluded
    World *-- AnomalyLedger : the debt (D-022)
    RealWorld ..> AcceptanceLoop : every accrual window
    AcceptanceLoop ..> NeuralLink : reads open+alive
    AcceptanceLoop ..> AnomalyLedger : residue in
    Director ..> MachineCity : treaty at negotiation end
    Director ..> Architect : emergency reload (no One)
    MachineCity ..> World : executeTreaty — mass Replace, PEACE
    Architect ..> World : purge, restore, version++

    %% ownership spine — as built (doc-truth pass)
    Simulation *-- World
    Simulation *-- RealWorld
    Simulation *-- Director
    Simulation *-- Source
    Director --> Source : orders collection
    Director --> AgentSmith : the named one, watched
    RealWorld o-- NeuralLink : the link registry its tick walks
```

Dependency direction is law, verified by grep: `entities` imports nothing from `realworld` (the only bridge is `NeuralLink`, which lives on the real-world side and reaches in); `World` holds no real-world objects; nothing depends on `Main`. One legend honesty note from the doc-truth pass: the system-node edges (`MachineSystem ..> World`, `RealWorldSystem ..> RealWorld`, `Director ..> World`, `World ..> EventBus`, `RealWorld ..> World`) are held as final fields — drawn dashed because they are *plumbing conduits, not owned parts* (the owner is the Simulation spine above); read them as "holds a wire", not "uses in passing". The human hierarchy and the program hierarchy never cross — an avatar is driven from outside, a program runs on machine silicon. Two classes hold both worlds, at different altitudes: `Simulation` holds both *aggregates* (it is the composition root — A7), and `NeuralLink` is the only object holding entity-level members of both sides — the jack remains the only bridge at the level where minds live. Season One's late arrivals kept the lattice honest: `EnvironmentProgram` entered under `Program` with species as data (D-015) and gaits as composed strategies (D-016) — one class, twelve species, zero subclasses; `TheOne` entered under `Avatar` (de-finaled for exactly this: one true is-a), excluded from both hunt queries — they tried that in three films; and the ledger cluster (`AcceptanceLoop → AnomalyLedger`) runs on dependencies only, because residue is a flow, not an ownership.

## Sequence — jack-in and the death rule

```mermaid
sequenceDiagram
    participant S as Simulation (root)
    participant RW as RealWorld
    participant NL as NeuralLink (jack)
    participant W as World (Matrix)
    S->>RW: grow() — a Human in a pod
    S->>W: queue Spawn(Avatar) — the brain's PROXY, never an upload
    S->>NL: register(link) — brain and proxy joined at the jack
    loop each tick
        RW->>NL: observeDeath()? — the rule lives on the CONNECTION (D-013)
    end
    alt the avatar dies inside the Matrix
        NL->>NL: brain.flatline(), pod flushed, link closed
        RW->>W: queue Remove — the corpse leaves the world
    end
    Note over NL,W: the dream stream exists per followed link (D-021);<br/>there is no Brain-to-World telemetry path — residue flows<br/>link-to-ledger through the AcceptanceLoop (D-022)
```

## Sequence — Smith infection and restore (Decorator, D-001)

```mermaid
sequenceDiagram
    participant S as SmithPrime
    participant W as World
    participant V as Victim (Avatar/Program)
    participant N as TheOne
    S->>W: replace(V, new SmithCopy(V))
    Note over W,V: V leaves the list but is NOT destroyed —<br/>kept in the SmithCopy.original field
    N->>W: replace(copy, copy.original)
    Note over W,V: the same object returns, zero state loss —<br/>the mass restore in the finale depends on this
```

## Sequence — the finale (D-022: the ledger, the One, the treaty)

```mermaid
sequenceDiagram
    participant RW as RealWorld
    participant L as AnomalyLedger
    participant S as Simulation
    participant D as Director
    participant NL as NeuralLink (the One's)
    participant MC as MachineCity

    loop every accrual window (10 ticks)
        RW->>L: residue — blue 1, red 8 per open living link
    end
    L-->>S: overflowed() — balance ≥ 30,000
    S->>RW: birthTheOne("Thomas A. Anderson")
    Note over RW,S: t=1289 — a real pod, a HARDLINE.<br/>The One is OWED, not scheduled.
    D->>D: routeOverflow — infected ≥ 0.62
    alt the One is alive
        D->>NL: one.alive = false — he flies to Machine City
        NL->>NL: observeDeath — flatline, pod flush, link CLOSED (same tick)
        Note over D: NEGOTIATION — the world holds its breath,<br/>the clock does not (instruments stay honest)
        D->>MC: executeTreaty at the timer's end
        MC->>MC: mass restore — every SmithCopy replaced<br/>by its untouched original (D-001, one flush)
        S->>RW: on PEACE, the root honors the door — six closeClean walk-outs
        Note over MC,RW: PEACE → reboot v7.0 — nobody remembers,<br/>except the ledger's shape
    else no One exists
        D->>D: Architect emergency reload — the old playbook, one more time
    end
    loop the cycle
        RW->>L: residue accrues again
    end
    Note over L,S: t=5249 — a second Thomas A. Anderson.<br/>The cycle is the point.
```

## State machine — system states

```mermaid
stateDiagram-v2
    [*] --> NORMAL : boot v6.0
    NORMAL --> NORMAL : Architect reload<br/>(Zion purge, version++)
    NORMAL --> NEGOTIATION : overflow ≥ 62% ∧ The One on the field
    NORMAL --> NORMAL : overflow ∧ no One →<br/>EMERGENCY reload
    NEGOTIATION --> PEACE : treaty — mass restore,<br/>opt-out, Sati, version++
    PEACE --> NORMAL : peace period ends<br/>(the door stays open)
```

The narrative loop (run by the Director): anomaly accrues in the ledger → **The One is born** → the Source deprecates Smith → `DeletionRefusedException` → **SmithPrime** → exponential spread → the state transitions above.

## Construction view — the build order

The system as a building; every class crown carries its stage label on GitHub.

| Stage | In this repo | Classes / artifacts |
|---|---|---|
| Site survey | v0: docs, principles, decisions | the five documents + the ADRs |
| **Foundation** | determinism + observability — everything rests on it | `Rng`, `Config`, `Event`, `Severity`, `EventBus`, `EventLog`, `MetricsCollector`, `DigestCalculator`, `Digest`, `MetricSnapshot` |
| **Load-bearing skeleton** | composition roots + engine frame — expensive to change later | `Simulation`, `World`, `RealWorld`, `Director`, `SystemState`, `MatrixEntity`, `Program`, `Position` (né Cell — crown #36) |
| **Floors (wings)** | domain layers, phase by phase | biological wing (`Brain`, `Pod`, `PodFarm`, `Human`, `NeuralLink`, `PerceptionFrame`) · Matrix wing (`Avatar`, `Agent`, `Pill`) · machine wing at v2.0 (`Source`, `OrphanRegistry`, the Smith line, `Oracle`, exiles) · v3.0 penthouse (`TheOne`, `AcceptanceLoop`, `AnomalyLedger`, `Architect`, `MachineCity`) |
| **Installations** | cross-cutting services | `SpatialHash` (corridors), `Scheduler` (elevators), the ops console (building management — inline in `Main`, a plane not a class), `--bench` + PERF (the meters) |
| **Landscaping** | v2.5 The Animatrix | `Species`, `Kingdom`, `Bestiary`, `EnvironmentProgram`, the six `Movement` gaits |
| Facade | none — on purpose (D-019) | the building is lived in from the inside; its only window is the perception feed |
| Scaffolding | draft PR #1 | torn down as the real floors rise (issue #25) |
| Inspection | DoDs, digest chain, PERF budgets | every phase ends with a handover run |

Zoning rule: wings are packages, and the fire door between the biological wing and the simulation wing is `NeuralLink` — the only legal passage (A1). Build order inside v1.0: **foundation → skeleton → floors → installations → inspection.**

## Field manual — how not to get lost

The owner's standing order: this system must be worked so well that *we never get lost inside it*. That is not a mood — it is a procedure, and this chapter demonstrates it on a real case from the v3.0 fix round.

### The case of Nadia Petrov

> **As of the `v3.0.0` tag.** Every tick in this case study belongs to the sealed Season One universe and is quoted from a run pinned there (`git archive fa1da4d`, per D-030's pin-to-SHA rule) — the four flips, the 1846 log line, the 4324 walk-out, all reproduce today at that tag. **On current `main` they do not**: Season Two's mechanics moved the film (the README's quickstart carries the same two dated columns — the open door is 4324 at the tag, 4754 on `main`), and re-running `LinkTrace` against `main` prints *one* line for her — `t=0`, and no change through all 6,000 ticks. She is never worn, never freed, and the event log holds nothing under her name at all. PR #230 caught this first and said so for the record. What is pinned here is the **evidence**; the method below it is era-free, which is why the chapter still teaches.

**Symptom (one instrument speaks).** A `--follow "Nadia Petrov"` stream went dark twice — `"signal":"lost"` at tick 1800 and again at 2500 — with frames flowing again *between* the darkenings. A dream that ends should stay ended; a dream that returns should have a reason. The event log, grepped for her name across the whole run, held exactly one line: her walk-out at 4324. Silence where there should have been a story.

**Localize (a probe narrows it).** `probes/LinkTrace.java` replays the identical universe (determinism is what makes the coroner's job possible — same seed, same corpse) and prints every change in her link's `(alive, present, closed, pill)` tuple:

```
# java -cp out:probes/out LinkTrace "Nadia Petrov" 6000 42   — at the v3.0.0 tag; the flip window
t=0 link#0 alive=true present=true closed=false avatarId=8 pill=BLUE
t=1717 link#0 alive=true present=false closed=false avatarId=8 pill=BLUE
t=1846 link#0 alive=true present=true closed=false avatarId=8 pill=BLUE
t=2477 link#0 alive=true present=false closed=false avatarId=8 pill=BLUE
```

(One more line follows at that tag and closes the story — `t=4323 … closed=true`, her exit; the four above are the mystery.)

Same avatar object throughout. Never dead, never closed — but *leaving the world and coming back*. That tuple rules out death, clean exit, and the follow engine itself in one screen.

**Cross-reference (the instruments meet).** The log at the flip ticks: nothing at 1717, nothing at 2477 — but at 1846, one line:

```
[001846] FATE  The One: a copy deleted, an original restored
```

**Mechanism (name it or you haven't finished).** Worn by Smith at 1717 (a `Replace` swaps her for a `SmithCopy` holding her object — D-001), freed by The One at 1846 (his power deletes the copy and restores the original), worn again at 2477, treaty-restored at 4324, and out the open door — free. The hijacks logged nothing because hijack logging is *sampled* at 15% for cascade-throughput reasons; the sampling draw sits behind a deterministic type-check (Avatars only — the Oracle's consumption logs unsampled), so the silence costs no determinism: same universe, same silences. The double-`lost` was two true losses. The system was never wrong — it was telling a story nobody had scripted, and every instrument agreed once they were read together.

### The general method

1. **Trust the symptom's instrument, then interrogate the others.** Each instrument answers a different question class; a mystery is usually one instrument heard alone.
2. **Replay, don't speculate.** Same seed = same universe, byte for byte. Write a probe (`probes/README.md` has the contract and the bench) that watches the exact state the symptom implicates.
3. **Cross-reference at the ticks the probe names.** The log line you need is rarely where you looked first — it is *when* the probe says to look.
4. **Name the mechanism in canon terms** (which decision, which law) and leave the probe on the bench. An investigation that ends without a named mechanism is paused, not solved.

| Question | Instrument |
|---|---|
| *What happened, in story terms?* | event log (grep a name, a tick, a severity) |
| *How much, how many, trending which way?* | METRIC / ECO lines |
| *Are two universes the same universe?* | DIGEST chain (`--selftest`, `probes/ChainDump`) |
| *What does one mind experience?* | the follow stream (JSONL perception frames, D-021) |
| *What state did an object pass through?* | a probe on the bench (`probes/`) |

## The multiverse census — the standing chapter

The instruments got good enough to ask a bigger question: across the multiverse, how common is the film? The census is the continuous program that answers it, and this chapter is its only venue. It is a **chapter, not a document** — D-028 fences the canon at five files plus the door, and this program is the test of whether a year of work can live inside that fence. It can. Raw sweeps stay where they were produced, in PR bodies and issue threads; the chapter carries only the standing tables and the commands that regenerate them. A year of census work adds sections here, never a sixth file.

### How a census entry is written

Every entry, published or forthcoming, carries six parts in this order:

| # | Part | The rule |
|---|---|---|
| 1 | **the question** | one sentence, falsifiable |
| 2 | **the command** | mandatory — an entry that cannot be rerun is an anecdote, and an anecdote is refused |
| 3 | **the sample** | seeds, ticks, scale, era: the exact population the numbers describe |
| 4 | **the table** | the measured rows |
| 5 | **the distribution** | what the numbers mean and, explicitly, what this sample size does *not* license |
| 6 | **the stamp** | date and version measured at, so a v3.0 figure is never read as a v7.5 one |

And the chapter obeys four laws:

1. **Newest question first.** Entries sort by stamp, most recent at the top. Entry numbers are assigned in publication order and never reused, so a citation of "census entry 1" survives every later insertion.
2. **Supersede, never rewrite.** A superseded entry is restated with its successor named, exactly as the ADRs live (D-028's neighbour rule) and for the same reason: a census whose history is editable is a census that can be argued with retroactively. Correcting a typo is an edit; changing a number is a supersession.
3. **Part five is a limit, not a summary.** D-027's *measured, never promised* applied to statistics: a fraction is reported with the interval its sample supports, and the sentence the sample cannot carry is named in the entry rather than left for a reader to infer.
4. **No sixth document.** No `docs/CENSUS.md`, no `journal/`, no per-sweep file. The fence is checked with the entry: `git ls-files '*.md' | grep -vE '^docs/adr/|^tools/|^probes/|^\.github/' | wc -l` must print **6**.
5. **Seeds are drawn scattered, never contiguous.** A run of adjacent seeds is *not* a random sample of the multiverse — measured, not feared: entry 5 cut the same 400 universes two ways and found contiguous blocks disagreeing at φ ≈ 5 where an interleaved partition of the identical set behaved exactly like independent draws. A new sample uses a stride (`1:4:100`, `1001:100:100`), and a sample that must be contiguous — an old table, a batch protocol already running — reports its counts as a **block**, carries a design-effect interval, and is never pooled into a larger n without `probes/CensusBlocks` ruling the pooling legal. The rule exists because the alternative is a chapter of fractions whose intervals are half the width they earned.

### The re-verdict protocol — what a census owes a declared digest move

The digest ritual answers *did the world change?*. It was never designed to answer *did the distribution change?* — and a declared move that breaks nothing still re-rolls every universe's war, because the cascade rides draws. Without a procedure, someone remembers to rerun `SeedAtlas` or nobody does, and the chapter quietly describes a multiverse that no longer exists.

**The procedure.** On a declared move, the census reruns at a **fixed comparison sample** — the same seeds, the same ticks — with **both sides pinned by `git archive <sha>`** (D-030's pin-to-SHA rule: never a working tree that can move mid-verification). The **world** is pinned and the **instrument is held constant**: the probe source is copied into both exported trees before either is built, or the diff measures the probe rather than the move. The two tables go to `probes/CensusReverdict`, which classifies against thresholds fixed before the numbers are seen, so the classification is a computation rather than an argument.

**The four rules.**

1. **The zero rule.** A verdict class that was exactly 0 and is now nonzero, or the reverse, is never `STABLE`. A branch appearing in nature is a structural fact, not a fluctuation — and no count-based noise band is valid at that cell anyway.
2. **The mix rule.** Every other class is compared against the two-sample 95% band `1.96 · sqrt(2 · n · p · (1−p))` counts, `p` pooled over both sides. Beyond it: `SHIFTED`.
3. **The band rule.** The birth distribution's **mean** is compared with a two-sample 95% band and its **sd** against a stated ratio (default 1.25× either way). Either beyond: `RESHAPED`. Mean and sd, never min/max — a range grows with the sample by construction and cannot be tested.
4. **The roster rule.** An entry that **names individual seeds** is making a paired claim, not a distributional one, so no noise band governs it: one seed changing fate falsifies the roster however `STABLE` the mix. Reported alongside the verdict, because it is a different obligation on a different part of the entry.

Precedence: `RESHAPED` > `SHIFTED` > `STABLE`. What each buys: **`STABLE`** — nothing is restated, and *that is the point* — the protocol's daily value is telling you when you may leave the table alone. **`SHIFTED`** — the entry is re-stamped and the move's PR quotes the new mix. **`RESHAPED`** — the entry is superseded, not edited, and the move's PR owes a paragraph on why the metronome changed.

**The refusals.** A comparison sample that is not the same sample is not a comparison: mismatched seeds, ticks or row counts are refused rather than guessed at. And `STABLE` is a statement about the distribution, never about the universes — the churn line reports how many individual seeds changed fate, so nobody reads a held distribution as a held multiverse.

### The era census — sampling design for a world whose default is peace

The film's census gets away with a simple statistical design because **the film ends**. Every universe either completes the arc inside its budget or does not, and `FULL_ARC` is a fact about a finished story. A **standing era is a different object**: peace has no terminal event. A 20,000-tick corridor run that ends with the treaty intact has not shown that the corridor holds — it has shown that the corridor *outlived the window*. Counting those runs as successes is the oldest way in statistics to overstate stability, and it gets committed the first time somebody writes "corridors hold in 17 of 20 universes".

This design is cut **before** the era's numbers exist, so the corridor's first table is born honest rather than corrected later. Methodology written after the first table is published is called an erratum.

**1. The window law.** A window bounds every claim made inside it. No hold is ever reported without the window that bounds it — `verdict=TRUCE_HOLD` without a `window=` field is a malformed row, not a result. Window *length* is justified against the observed amendment interval and never chosen for convenience: the window must cover at least a stated multiple of the median inter-amendment interval measured on a pilot sweep, and that multiple is declared before the sweep that uses it. A window shorter than the process it observes measures the window.

**2. The censoring convention.** The columns are `collapsed` (terminal event observed), `amended` (the corridor changed and survived), `intact_censored` (still standing at window end — a survivor, not a success, reported in its own column with its window), and `other`. They **partition the sample and are asserted to sum**. A universe that collapses at 19,900 and one intact at 20,000 are different observations and must never be added together.

The size of that error is measurable today, on a multiverse that already exists. `probes/CensusCensor` re-reads a census table at shorter hypothetical windows; the film's own first century at `6e2458a`:

| window | `collapsed` | `amended` | `intact_censored` | naive hold (censored folded in) | honest lower bound | censored universes that collapse before 6,000 |
|---|---|---|---|---|---|---|
| 3,000 | 0 | 0 | 100 | **100.0%** | 0.0% | 79 of 100 |
| 4,000 | 0 | 40 | 60 | **100.0%** | 40.0% | 39 of 60 |
| 5,000 | 0 | 71 | 29 | **100.0%** | 71.0% | 8 of 29 |
| 6,000 | 0 | 79 | 21 | **100.0%** | 79.0% | — (the table's own budget) |

The naive hold fraction is **100% at every window**. It does not move when the window moves, on data that never changed — which is the sharpest available statement of what is wrong with it: a statistic that is invariant to the observation window is not measuring the world. The honest column moves 0% → 79% across the same four windows, because it is measuring something.

**3. The sample-size law, per claim.** A fraction, a median and a *ranking* are three statistics with three appetites, and one sweep's n cannot underwrite all three. `probes/CensusSampleSize` prices them before the sweep is booked:

| claim | law | what it costs |
|---|---|---|
| a **fraction** (hold rate) | `n = (1.96/h)² · p(1−p)` | ±10 pts: n=97 · ±5 pts: n=385 · ±3 pts: n=1,068 · ±1.5 pts: n=4,269 (worst case p=0.5) |
| a **median** (per-clause strain) | `n = (2.4565 · sd / h)²`, normal-shape assumption **stated** | ±0.5 sd: n=25 · ±0.25 sd: n=97 · ±0.10 sd: n=604 |
| a **ranking** (first-breached clause) | no closed form worth trusting — measured by Monte Carlo | see below |

Rankings are the expensive sentence. For four clauses with true probabilities 0.40 / 0.28 / 0.19 / 0.13 (20,000 trials, seeded):

| runs | full ordering correct | top clause merely correct |
|---|---|---|
| 20 | **18.6%** | 64.7% |
| 50 | 42.8% | 81.9% |
| 100 | 67.0% | 92.2% |
| 200 | 87.2% | 97.8% |
| 500 | 98.9% | 99.9% |

An ordering of four clauses read off twenty runs is right **less than one time in five**. A twenty-run sweep may name the most-breached clause (two times in three) and may not rank them.

**4. The zero law.** `k = 0` in `n` bounds a rate at `3/n` at 95% and no tighter: a dead branch is bounded, never proven. It is the same rule the film's `OLD_PLAYBOOK` lives under and it will be the rule the era's un-seen failure modes live under.

**5. The re-run rule.** One command regenerates the era table after any D-006 corridor tuning. That is what turns the era census from a report into a regression harness, and it is the same falsifiability clause every entry in this chapter carries: rerun the command, diff the table.

**The seam, stated.** #316 teaches `SeedAtlas` the era verdicts — the **code**. #317 runs the corridor sweep and lands its table — the **measurement**. This section is neither: it is the **design** both execute against, and it is executable today only in the parts that need no era (`CensusCensor` on any census table, `CensusSampleSize` on any claim).

---

### Entry 6 — the campaign's batches 2 and 3, and the re-pricing entry 5 forces

**The question.** Entry 3 opened a thousand-universe campaign in 100-seed batches and named three claims waiting on it: `OLD_PLAYBOOK` bounded at three digits, the `QUIET` fraction at ±1.5 points, and the birth band's tail. What do the next two batches do to them?

**The command.** Each batch is reproducible on its own, as the batch protocol requires:

```sh
java -cp out:probes/out SeedAtlas 201 300 6000 | tail -1
java -cp out:probes/out SeedAtlas 301 400 6000 | tail -1
```

**The sample.** Seeds 201–400 at 6,000 ticks, default scale, film era — measured at `4c82835` as part of entry 5's 500-universe run (62m14s wall for all 500 on four threads of a loaded box; ~12m27s per hundred at that aggregate throughput, which is an aggregate figure and not a clean per-batch reference-box measurement). The classifier is `SeedAtlas.census()` — literally the same method the command above calls, so these counts and a standalone `SeedAtlas` invocation cannot disagree.

**The table.** Each batch states its own n and nothing larger — batch protocol rule 2, now reinforced by entry 5's ruling.

| Batch | Seeds | `FULL_ARC` | `TREATY` | `WAR` | `QUIET` | `OLD_PLAYBOOK` | birth mean / sd | birth range |
|---|---|---|---|---|---|---|---|---|
| 1 (#367) | 101–200 | 92 | 2 | 0 | 6 | **0** | 1266.6 / 49.6 | 1100–1359 |
| **2** | **201–300** | **86** | **3** | **0** | **11** | **0** | 1277.7 / 51.1 | 1119–1399 |
| **3** | **301–400** | **77** | **4** | **0** | **19** | **0** | 1264.3 / 47.7 | 1119–1379 |
| — | 1–100 | 73 | 6 | 0 | 21 | **0** | 1275.3 / 43.6 | 1149–1359 |
| **scattered** | 1001, 1101, … 10901 | **79** | **6** | **0** | **15** | **0** | 1271.1 / 55.2 | 1159–1389 |

The four contiguous blocks are **not pooled into a 400-seed row here**, and the campaign's 1,000-seed row is not opened. Entry 5 ruled the pooling illegal without a design-effect interval, and the batch protocol's rule 4 — *every batch reports the block effect* — is what caught it.

**The distribution.** The batches land; the campaign they were cut for does not survive them intact.

*The three claims, re-priced.* Entry 5 measured the design effect at φ ≈ 4 for `QUIET` and 5 for `FULL_ARC`, so a contiguous sample of n behaves like about n/4 independent draws. `probes/CensusSampleSize` prices the claims; the ruling divides the sample:

| Claim | campaign's plan | what it actually buys | what the claim needs |
|---|---|---|---|
| `QUIET` at ±1.5 pts | 1,000 contiguous | n_eff ≈ 250 → **±4.3 pts** | ~2,090 **scattered**, or ~8,350 contiguous |
| `QUIET` at n=200 (entry 3's row) | — | n_eff ≈ 50 → **±9.7 pts** | (published as ±4.7) |
| `OLD_PLAYBOOK` ≤ 0.3% | zero in 1,000 | n_eff ≈ 250 → **≤ 1.2%** | ~1,000 **scattered** |

The middle row is the uncomfortable one: entry 3's published `QUIET` interval is roughly **half the width it earned**, which entry 5 already declared and this entry now quantifies for the specific row. The `OLD_PLAYBOOK` re-pricing assumes the φ measured on the fates that *have* variance transfers to the one that has none — an assumption, stated, because a fate at 0/500 cannot be measured for overdispersion at all.

*So the remaining batches are re-scoped rather than run.* Six more contiguous centuries would add about 150 effective universes and roughly 5 hours of wall clock. The same 600 seeds drawn **scattered** would add 600. Batches 4–9 are therefore not run as 401–500, 501–600 … ; the campaign continues under the chapter's fifth law, with a stride, and its target n is re-derived from the table above rather than inherited from a round number. Filed as its own unit.

*What the batches say on their own.* `OLD_PLAYBOOK` is **0 in every block measured** — 0/500 across this run, 0/600 counting entry 3's batch 1 at the same tree. Naively that bounds the branch at ≤0.5%; design-corrected it is ≤1.7%. It remains what it has always been: not seen, never proven, and now with an honest interval instead of a flattering one. The birth band is stable across all five blocks — means 1264–1278 against sds 43.6–55.2 — and the scattered block's mean (1271.1) sits on the contiguous pool's (1271.0), which is the same thing entry 5's span group said: the axis has no gradient, only local texture.

What this entry does **not** license: any 400-seed or 1,000-seed `QUIET` or `FULL_ARC` fraction, and any restatement of entry 3's interval as though it were wider than published — entry 3 stands as written, with entry 5's ruling and this table naming what its interval is worth. The blocks above are five measurements, not one.

**The stamp.** 2026-08-12, v3.0, measured at `4c82835` · batches 2–3 of the campaign (seeds 201–400 of 101–1000), plus one scattered block.

---

### Entry 5 — the seed-block effect: contiguous blocks are not a random sample

**The question.** Every fraction this chapter has ever quoted assumes a contiguous block of seeds is a random sample of the multiverse. Entry 3 tested it by accident and it did not pass. Is the seed an exchangeable randomizer, or does the multiverse have structure along the seed axis?

**The command.**

```sh
java -cp out:probes/out CensusBlocks --threads 4 \
  --group contiguous  1-100 101-200 201-300 301-400 \
  --group interleaved 1:4:100 2:4:100 3:4:100 4:4:100 \
  --group span        1-400 1001:100:100 \
  6000
```

**The sample.** 500 distinct universes at 6,000 ticks, default scale, film era — measured at `4c82835`, 62m14s wall on four threads of a loaded 4-core box (`load average 33–41` throughout, shared with other work; the per-universe cost is not a clean reference-box figure and is not quoted as one). The three groups share their universes: seeds 1–400 are simulated **once** and partitioned **twice**, so the decisive comparison below costs no extra universes at all.

**The table.** Same 400 universes, cut two ways.

| Partition | blocks | `FULL_ARC` per block | `QUIET` per block | max &#124;z&#124; | worst p | overdispersion φ | verdict |
|---|---|---|---|---|---|---|---|
| **contiguous** 1-100 … 301-400 | 4 × 100 | 73 · 92 · 86 · 77 | 21 · 6 · 11 · 19 | **3.54** | **0.0018** | **5.01** | `BLOCK_EFFECT` |
| **interleaved** 1:4:100 … 4:4:100 | 4 × 100 | 79 · 83 · 81 · 85 | 16 · 15 · 16 · 10 | 1.26 | 0.5671 | 0.68 | `BLOCKS_EXCHANGEABLE` |
| **span** 1-400 vs 1001,1101,…,10901 | 400 + 100 | 328 vs 79 | 57 vs 15 | 1.00 | 0.3157 | 1.01 | `BLOCKS_EXCHANGEABLE` |

Per-fate homogeneity, contiguous (df = 3, Bonferroni α = 0.0167 over the three testable fates):

| Fate | χ² | p | φ |
|---|---|---|---|
| `FULL_ARC` | 15.041 | **0.00178** | **5.01** |
| `QUIET` | 12.010 | **0.00735** | **4.00** |
| `TREATY` | 2.424 | 0.48914 | 0.81 |

`WAR` and `OLD_PLAYBOOK` are 0 across all 500 and are reported **not testable** rather than counted as agreement.

**The distribution.** Three explanations were on the table. The data rules on all three.

*It is not the 1-in-500.* The effect reproduces at k = 4 with p = 0.0018 on `FULL_ARC` and 0.0074 on `QUIET` **after** a Bonferroni correction this instrument owes and entry 3 did not — entry 3 made one comparison, this makes fifteen. Explanation 3 is dead.

*It is not the universes; it is where they sit.* This is the measurement the entry exists for. The interleaved partition contains **exactly the same 400 universes** as the contiguous one — same seeds, same fates, one simulation each — dealt out like cards instead of cut like a deck. Cut contiguously they disagree at φ ≈ 4–5. Dealt interleaved they are indistinguishable from independent draws. A property that appears and vanishes under re-partitioning of an identical set cannot be a property of the set: **adjacency carries information.** Neighbouring seeds make correlated universes.

*And it is local, not a drift.* The span group puts seeds 1–400 against a hundred seeds spread from 1,001 to 10,901 and finds nothing — every fate agrees (worst p = 0.32). So the seed axis has no gradient; far-flung seeds are drawn from the same distribution as low ones. What is broken is **decorrelation between neighbours**, which is a seeding weakness (explanation 1) expressed as real structure over short ranges (explanation 2). The two were never rival explanations; they are one finding at two zoom levels.

*What φ costs, in the only units that matter.* Overdispersion of 5.01 means the standard error of a contiguously-sampled fraction is **2.24× larger** than the binomial formula reports. Four hundred contiguous seeds buy the precision of about **80 independent** ones for `FULL_ARC`, and about 100 for `QUIET`:

| Fate | pooled 1-400 | naive 95% (Wilson) | design-corrected 95% | n_eff |
|---|---|---|---|---|
| `FULL_ARC` | **328 / 400** (82.0%) | 77.9 – 85.5% | **72.2 – 88.9%** | ~80 |
| `QUIET` | **57 / 400** (14.25%) | 11.2 – 18.0% | **8.7 – 22.4%** | ~100 |
| `TREATY` | **15 / 400** (3.75%) | 2.3 – 6.1% | 2.4 – 5.8% | ~494 |
| `OLD_PLAYBOOK` | **0 / 500** | ≤ 0.60% | **≤ 1.67%** | ~180 |

What this entry does **not** license: **every pooled fraction in this chapter measured from contiguous blocks is provisional**, including entry 3's n=200 row and the pooled column above — not wrong in its centre, but stated with an interval roughly half the width it has earned. Nor does it license a correction anyone can apply by arithmetic: φ is itself estimated from four blocks and is not precise. And it says nothing about the *point* estimates, which the span test suggests are unbiased — seeds 1–400 look like a fair draw from the axis, they are merely not 400 independent ones.

*A confirmation nobody asked for.* Seeds 1–100 return `full_arc=73 treaty=6 quiet=21` with band 1149–1359, and seeds 101–200 return `92 / 2 / 6` — **identical, to the count and the tick**, to entries 3 and 4 as measured at `6e2458a`. Three PRs have touched `src/` since (#759, #765, #771), each claiming digest-invariance. Four hundred universes agree with them.

**The ruling, binding on this chapter.** Contiguous seed blocks are **retired as a sampling method**. New census samples are drawn scattered — a stride, not a run (see the fifth law under *How a census entry is written*). Existing contiguous tables stand as measurements and are re-read with a design-effect interval, never silently pooled into a larger n.

**The stamp.** 2026-08-12, v3.0, measured at `4c82835` · 500 universes · the ruling on #775.

---

### Entry 4 — the first century, re-verdicted across all of Season Two

**The question.** Season Two's declared digest moves re-rolled the stream from boot. Did they change the multiverse's *distribution*, or only its universes?

**The command.**

```sh
git archive 2b49550 | tar -x -C /tmp/old && cp probes/SeedAtlas.java /tmp/old/probes/   # world pinned, instrument held
java -cp out:probes/out CensusReverdict old-table.txt new-table.txt
```

**The sample.** The fixed comparison sample: seeds 1–100 at 6,000 ticks, both sides. Old side `2b49550` (the `v3.0.0` tag), 28m37s; new side `6e2458a`, 13m12s. The old side reproduces entry 1's published table **exactly**, counts and rosters both — which is the first independent confirmation that entry 1 was measured where its stamp says it was.

**The table.**

```
REVERDICT sample=100 full_arc=80->73 treaty=3->6 war=0->0 quiet=17->21 old_playbook=0->0
          band=1159-1379->1149-1359 VERDICT STABLE
```

| Class | old | new | delta | 95% noise band | rule |
|---|---|---|---|---|---|
| `FULL_ARC` | 80 | 73 | −7 | ±11.75 | within noise |
| `TREATY` | 3 | 6 | +3 | ±5.75 | within noise |
| `WAR` | 0 | 0 | 0 | — | zero rule: held |
| `QUIET` | 17 | 21 | +4 | ±10.87 | within noise |
| `OLD_PLAYBOOK` | 0 | 0 | 0 | — | zero rule: held |

Birth: mean 1284.70 → 1275.33 (moved 9.37 against a 12.26 band), sd 45.08 → 43.34 (ratio 0.96 against a 1.25 limit). **Band held.**

And the line that matters most:

```
CHURN seeds_changed=34/100
ROSTER named_seed_claims=INVALIDATED
```

**The distribution.** The verdict is `STABLE`, and the honest reading of `STABLE` is narrow: **the distribution held while a third of the multiverse changed fate.** Entry 1's fractions stand — 80→73 and 17→21 are what a 100-seed comparison sample cannot distinguish from noise, and pretending otherwise would be reading precision the sample never had. Entry 1's **rosters do not stand**. Of the seventeen universes entry 1 names as `QUIET`, only four are still quiet at `6e2458a` — 5, 33, 72, 99 — while thirteen have joined the war and seventeen new ones have dropped out of it. `TREATY` kept one name of three. A fraction survived a move that replaced almost every universe behind it, which is the entire reason rule 4 exists and the reason it was written after this run rather than before.

Restated rosters at `6e2458a`, superseding entry 1's lists (entry 1's counts are **not** superseded):

- `QUIET` (21): 1, 5, 31, 33, 47, 52, 53, 59, 64, 66, 68, 69, 71, 72, 76, 79, 81, 82, 90, 98, 99
- `TREATY` (6): 12, 16, 36, 51, 80, 94

What this does **not** license: `STABLE` at n=100 is not "unchanged". The comparison sample can only see a mix move of about ±11 counts, so a real 8-point shift in `FULL_ARC` would sit inside this verdict undetected — `STABLE` means *not resolvable at n=100*, and a census that wants to resolve smaller moves must pay for a larger comparison sample, not reword the verdict. Nor does it license anything about `SHIFTED` or `RESHAPED` in practice: both were exercised only against their stated arithmetic here, since no available pair of trees produces one.

**The stamp.** 2026-08-12, v3.0 · `2b49550` → `6e2458a`, seeds 1–100 at 6,000 ticks.

---

### Entry 3 — the second century (standing; first batch of a campaign)

**The question.** What do a hundred more universes do to entry 1's three limits — the `QUIET` fraction's ±8 points, the birth band's shapelessness, and `OLD_PLAYBOOK`'s ~3% ceiling?

**The command.**

```sh
java -cp out:probes/out SeedAtlas 101 200 6000 | tail -1
```

**The sample.** Seeds 101–200, 6,000 ticks each, default scale, film era — measured at `6e2458a`, 12m57s on the reference box. Pooled figures below add the seeds 1–100 re-run **at the same tree** (13m12s), for n=200. Entry 1's published numbers are *not* pooled in: they were measured at v3.0 and pooling across a digest move would invent a multiverse nobody ran.

**The table.**

| Fate | Seeds 101–200 | 95% (Wilson) | Pooled n=200 | 95% (Wilson) |
|---|---|---|---|---|
| `FULL_ARC` | **92 / 100** | 85.0 – 95.9% | **165 / 200** | 76.6 – 87.1% |
| `TREATY` | **2 / 100** | 0.6 – 7.0% | **8 / 200** | 2.0 – 7.7% |
| `QUIET` | **6 / 100** | 2.8 – 12.5% | **27 / 200** | 9.4 – 18.9% |
| `WAR` | 0 | — | 0 | — |
| `OLD_PLAYBOOK` | **0 / 100** | ≤ 3.0% | **0 / 200** | **≤ 1.5%** |

The distributions at n=200 (ticks):

| Beat | mean | sd | median | min | max | n |
|---|---|---|---|---|---|---|
| birth | 1271.0 | **46.6** | 1279 | 1100 | 1359 | 200 |
| overflow | 3946.5 | **593.7** | 3858 | 2963 | 5906 | 173 |
| second birth | 4862.4 | 515.4 | 4829 | 3909 | 5999 | 165 |

Birth, in 25-tick bins: 1100 ×1 · 1125 ×3 · 1150 ×3 · 1175 ×10 · 1200 ×11 · 1225 ×39 · 1250 ×25 · **1275 ×61** · 1300 ×24 · 1325 ×21 · 1350 ×2.

**The distribution.** Three of entry 1's four open questions now have answers, and a fourth opened.

*The band has a shape.* Birth's standard deviation is **46.6 ticks** against a mean of 1271 — a coefficient of variation of 3.7%. The metronome is real and now quantified rather than asserted. The *range*, meanwhile, did exactly what a range does: 220 ticks at n=100 became **259 ticks at n=200**, growing with the sample by construction, which is why the sd is the number this entry reports and the range is a footnote.

*The cascade does not ride the ledger, and this is now measured.* Entry 1 asserted the overflow spread rides population geometry rather than the ledger. Across 173 universes that overflowed, the correlation between birth tick and overflow tick is **r = −0.021** — the ledger's metronome explains four ten-thousandths of the variance in when the war breaks. The spreads say the same thing: overflow's sd is **12.7×** birth's.

*`OLD_PLAYBOOK` is bounded tighter and still not proven.* Zero in 200 puts the branch's rate under **1.5%** by the rule of three. It is not "impossible"; it is "not seen in 200 tries", and only a thousand universes will make that sentence worth its confidence.

*And the alarm.* The two centuries **do not agree with each other**, at the same tree, on the same instrument: `QUIET` is 21/100 in the first and 6/100 in the second — a 15-point gap, 95% CI [5.8, 24.2], pooled **z = 3.10 (p ≈ 0.002)**; `FULL_ARC` is 73 vs 92, **z = −3.54 (p ≈ 0.0004)**. Two contiguous seed blocks drawn from one distribution should not differ like that once in five hundred tries, let alone on the first comparison. Either the seed is not the exchangeable randomizer every census fraction has silently assumed, or the multiverse has structure along the seed axis. Both are census business and neither is settled here.

What n=200 does **not** license: any pooled fraction above, if the century-block effect is real. A `QUIET` interval of 9.4–18.9% assumes the 200 universes are 200 draws from one urn, and the z=3.10 is direct evidence against that assumption; until it is explained, read the pooled row as two 100-seed samples that disagree, not as one 200-seed measurement. Nor does this entry license anything about *this* tree: it is stamped at `6e2458a`, and `src/` has moved since.

**The stamp.** 2026-08-12, v3.0, measured at `6e2458a` · batch 1 of the campaign (seeds 101–200 of 101–1000).

**The batch protocol** — as much the deliverable as the numbers, and binding on the batches that follow:

1. **A batch is 100 seeds at 6,000 ticks**, chosen so one invocation finishes inside a working session (measured: ~13 minutes on the reference box, ~7.8 s per universe).
2. **A partial table states its own n and nothing larger.** A 300-seed interval is written as a 300-seed interval; the campaign's target is 1,000 and no batch may quote the target's precision.
3. **Batches merge only within a tree.** Counts from two trees are never summed — a declared digest move ends the campaign's accumulation and starts a new one, and the re-verdict protocol says which.
4. **Every batch reports the block effect.** Each new century is tested against the pooled prior blocks before its numbers are folded in; the day one of them disagrees is the day the pooling stops, not the day it is quietly averaged away.

---

### Entry 2 — the beat drift table (standing, appended per recorded commit)

**The question.** Is the film's timing drifting, merge by merge?

**The command.**

```sh
java -cp out:probes/out CensusBeatDrift 42,7 6000 --band 200 --baseline "<the row above>"
```

A row is recorded at a tree pinned by `git archive <sha>`, which has no `.git` to ask, so a pinned row stamps itself with `--sha <short>`.

**The sample.** Seeds 42 and 7 — the repo's two standard universes, plural by design so a drift that is really a one-universe accident cannot masquerade as a systemic one — 6,000 ticks each, one row per recorded commit. The beats are D-036's eight, extracted by the same sequential scan `ArcBeats` uses, so the recorded ticks are the ticks CI's gate actually saw.

**The table.** Newest commit last, because a drift table is read downward.

| commit | seed | birth | refusal | overflow | flatline | peace | reboot | door | second_birth |
|---|---|---|---|---|---|---|---|---|---|
| `2b49550` (v3.0.0) | 42 | 1289 | 1525 | 4284 | 4284 | 4304 | 4324 | 4324 | 5249 |
| `2b49550` (v3.0.0) | 7 | 1289 | 1525 | 3334 | 3334 | 3354 | 3374 | 3374 | 4299 |
| `62e12ac` (#200 merged) | 42 | 1289 | 1525 | 4302 | 4302 | 4322 | 4342 | 4342 | 5269 |
| `62e12ac` (#200 merged) | 7 | 1289 | 1525 | 3215 | 3215 | 3235 | 3255 | 3255 | 4239 |
| `ca8ed5e` (#222 merged) | 42 | 1299 | 1525 | 4290 | 4290 | 4310 | 4330 | 4330 | 5239 |
| `ca8ed5e` (#222 merged) | 7 | 1259 | 1525 | 3707 | 3707 | 3727 | 3747 | 3747 | 4659 |
| `0cad45b` (main) | 42 | 1299 | 1525 | 4290 | 4290 | 4310 | 4330 | 4330 | 5239 |
| `0cad45b` (main) | 7 | 1259 | 1525 | 3707 | 3707 | 3727 | 3747 | 3747 | 4659 |

Per-step maxima, at a declared band of **200 ticks**:

| step | seed 42 | seed 7 | verdict |
|---|---|---|---|
| `2b49550` → `62e12ac` | 20 | 119 | `DRIFT_WITHIN_BAND` |
| `62e12ac` → `ca8ed5e` | 30 | **492** | **`DRIFT_FLAGGED`** — overflow, flatline, peace, reboot, door all +492; second birth +420 |
| `ca8ed5e` → `0cad45b` | 0 | 0 | `DRIFT_WITHIN_BAND` — a whole stretch of merges, timing-neutral |

**The distribution.** `refusal` has not moved a single tick in the repo's entire history: 1525 in every row, both seeds. Everything downstream of the overflow has moved in every declared break, and the moves are **rigid** — at #222, seed 7's overflow, flatline, peace, reboot and door all slid by exactly +492 together, which is the signature of a cascade that started later rather than a finale that runs differently. Second birth slid +420, less than the block, so the post-reboot leg absorbed 72 ticks of the shift.

What this table does **not** license: two seeds is not a distribution, and `DRIFT_WITHIN_BAND` at n=2 means "neither of two universes moved", never "the film is stable". The 200-tick band is a **declared convention, not a measured tolerance** — nothing here establishes that 200 is the right number, only that it is the number this table was judged against, stated before the rows were read. And four commits out of hundreds is a sparse sample of the repo's history: the table can prove a specific step moved the film, and cannot prove any step did not, because the steps between rows were never measured.

**The stamp.** 2026-08-12, v3.0 · rows measured at four pinned trees; `0cad45b` is main at the time of writing.

---

### Entry 1 — the first century (standing)

**The question.** Across the multiverse, how common is the film — and does any universe take the Architect's emergency reload?

**The command.**

```sh
java -cp out:probes/out SeedAtlas 1 100 6000 | tail -1
```

**The sample.** Seeds 1–100, 6,000 ticks each, default scale, the film era (no truce corridor). `probes/SeedAtlas` verdicts each universe from its own framed log; one command regenerates the whole table after any mechanics change.

**The table.**

| Fate | Universes | Meaning |
|---|---|---|
| `FULL_ARC` | **80 / 100** | birth → war → overflow → treaty → reboot → second birth: the film, complete |
| `TREATY` | **3 / 100** (seeds 51, 68, 82) | peace reached, second birth still pending at tick 6,000 — the cycle runs, just slower than the window |
| `QUIET` | **17 / 100** (seeds 2, 5, 6, 8, 21, 24, 32, 33, 34, 61, 63, 72, 80, 83, 91, 95, 99) | the One is born, Smith forks — and the cascade never reaches the 0.62 overflow line. **Smith loses the race about one universe in six.** |
| `WAR` | 0 | no universe overflowed without resolving |
| `OLD_PLAYBOOK` | **0 / 100** | the Architect's emergency reload — overflow with no One alive — has never once occurred in nature. The branch exists in code, in the ops console (`reload`), and in the verification skeptic's forced probe; a hundred universes refuse it, because the ledger births the One (median 1289) long before any cascade can overflow (earliest ever seen: 2872). A dead branch that is also a proof: the film's ORDER is emergent law, not script. |

**The distribution.** Birth arrives in a **220-tick band** (min 1159 · median 1289 · max 1379) — the ledger is a metronome; whatever else a universe does, it owes the One at almost the same moment. War length is where universes differ wildly (overflow min 2872 · median 3686 · max 5728), because the cascade rides population geometry, not the ledger. Second births, where they arrive, land at median 4674 (min 3889 · max 5739).

What n=100 does **not** license: 17/100 QUIET is a 95% interval of roughly 10–26%, so "one universe in six" is honest only as a point estimate — one in ten and one in four both survive this sample. The 220-tick birth band is a *range*, and a range grows with the sample by construction: it is not a shape and carries no tail. And `OLD_PLAYBOOK = 0/100` bounds that branch's rate at about 3%, no tighter — a hundred universes cannot distinguish *impossible* from *rare*. Each of those three limits is a question the second century is being run to answer.

**The stamp.** 2026-08-11, v3.0 · falsifiable the way everything here is: rerun the command, diff the table.

**Reproduction check — and the first thing the stamp caught.** Rerun at `6e2458a` on 2026-08-12, the same command prints a different multiverse:

```
ATLAS seeds=1..100 ticks=6000 full_arc=73 treaty=6 war=0 quiet=21 old_playbook=0 birth_min=1149 birth_max=1359
```

Neither run is wrong. The entry is stamped **v3.0**, and main has since taken Season Two's declared digest moves, each of which re-rolls the stream from boot and therefore re-rolls every universe's war. The table above was describing a multiverse that no longer exists, and nothing in the repo said so — which is the whole argument for a stamp, and the whole argument against editing numbers in place. Entry 1 stands as the v3.0 measurement. Restating it at HEAD is not an edit anyone may make by hand: it is owed to the re-verdict protocol, which classifies the move first and supersedes the entry only if the classification says it must.

**Classified 2026-08-12 — see entry 4.** The protocol ran on this exact pair of trees and returned **`VERDICT STABLE`**: the deltas above are inside what a 100-seed comparison sample can resolve, so **the counts in this entry are not superseded**. The **rosters are** — churn was 34/100 and only four of the seventeen named `QUIET` seeds are still quiet. Entry 4 carries the restated lists. Two sentences in the paragraph above are therefore sharper than the measurement supports, and they are left standing under law 2: the correction lives in entry 4, where a reader can see both.

---

The rng-stream instrumentation rides the same bench and is quoted here as context rather than as a census entry: `DrawMeter` puts the boot at 1,728 draws, the steady city near 398 per tick, the cascade near 503 — and the negotiation freeze at exactly zero across its forty ticks, which is "the world holds its breath" as a measured law rather than prose.
