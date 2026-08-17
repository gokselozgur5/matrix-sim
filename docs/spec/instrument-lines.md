# Spec: the instrument lines, v1 — the family-wide laws

**Status:** v1 · **Reader:** `probes/SpecDrift.java` · **Decisions:** D-020, D-019, D-010, D-058

A stranger's implementation of this world prints lines. This document states
what must be true of every one of them, so that a parser written against v1
still parses v9, and so that a foreign implementation can check itself rather
than being told it is wrong.

This node states the **constitution**. The per-field tables — every `METRIC`,
`ZION`, `ATTN`, `ECO`, `SUBSTRATE`, `PERF` and `DIGEST` field named, typed and
given a unit — are the sibling nodes of #255 and land separately. The laws below
are what make those tables safe to depend on.

## The laws

1. **One line, one fact.** A line is complete in itself. A reader may take any
   single line out of a stream, in any order, and it still says what it says.
   Nothing is continued onto a second line and nothing is only meaningful beside
   its neighbour.

2. **A stable prefix, first.** Every instrument line opens with its family name
   as the first whitespace-delimited token, in capitals. `grep '^METRIC '` is a
   supported way to read this system, not a convenience that happens to work.

3. **`Locale.ROOT`, always.** A number formatted under a comma-decimal locale is
   a broken instrument and not a cosmetic issue: `infected=0,625` is a different
   line from `infected=0.625` to every parser on earth, and the difference
   arrives on somebody else's machine rather than on the author's.

4. **UTF-8, owned.** The stream's encoding is set by the program, never
   inherited. A verdict quoted in a review must be the bytes another box prints.

5. **Additive-only evolution.** A field is **never renamed and never retyped**.
   New fields append at the end of their line. A parser written against v1 keeps
   parsing v9, reading the fields it knows and ignoring what came after.

   This is the law with a receipt rather than a maxim. *The Matrix Online*
   swapped its core grammar mid-life — Combat Revision 2.0, 2006 — and made its
   playerbase relearn the loop; sixteen years after the servers closed, the
   emulator scene has restored the whole explorable world and still cannot
   restore combat, because the world was data and the combat was logic inside a
   closed implementation. A grammar is a promise to people who will read it
   after you stop answering questions.

## The eight families

Every instrument line printed by a conforming run belongs to exactly one of
these. **The list is the data** — `SpecDrift` parses this table rather than a
second copy of it, and reads the second column as the condition it must not
demand.

| Family | Always printed | What it reports |
|---|---|---|
| `ATTN` | yes | attention-graded fidelity: regions hot and cold, and who is watched (D-024) |
| `BIRTH` | no — only with a chronos recorder attached, and only once a mind has been grown | one birth, with the five facts a reader needs to re-derive the die that keyed it (D-023, #847) |
| `DIGEST` | yes | the seal: one tick, one sha, the chain that makes every claim falsifiable (D-020) |
| `ECO` | yes | the rendered ecosystem, in two arities — birds alone, and the full census (D-015) |
| `METRIC` | yes | the world's vital signs each cadence: populations, infection, the anomaly ledger (D-020) |
| `PERF` | no — the daemon's runner prints it, not the world | the clock: ticks per second against the entities it carried (D-027) |
| `SUBSTRATE` | yes | the pod farm as compute: occupancy, budget, slots, stretch, glitches (D-008) |
| `ZION` | yes | the real world's mirror: census, fleet, links, traced (D-032) |

**Two of the eight are conditional, and saying so is the point of the column.**
A family that only appears under a flag is exactly the family a roster loses:
`BIRTH` needs `--chronos` *and* a tick budget long enough to grow somebody, and
`PERF` is emitted by the runner around the world rather than by the world. A
reader that demanded all eight from one universe would be red on a correct
implementation; one that demanded only what it happened to see would bless a
roster with a family missing. The column is what lets a checker do neither.

## Field tables

One table per family, in field order. **Field order is the contract**: a reader
keys on position, so a field renamed, moved or retyped is a breaking change and
a field appended after the last one is legal evolution (law 5). A conforming
line's fields must therefore begin with the table's fields, in the table's
order; anything after them is a later version's append and a v1 parser ignores it.

The first two below are the lines every run prints; `ZION`, `SUBSTRATE` and `PERF`
follow. `ATTN`, `ECO` and `BIRTH`
are the sibling nodes of #255 and land separately.

### `METRIC` — the world's vital signs

Emitted every 100 ticks. Arity 8.

| # | Field | Type | Unit | Domain |
|---|---|---|---|---|
| 1 | `tick` | INT | ticks | ≥ 0 |
| 2 | `blue` | INT | count | ≥ 0 |
| 3 | `red` | INT | count | ≥ 0 |
| 4 | `agents` | INT | count | ≥ 0 |
| 5 | `total` | INT | count | ≥ 0 |
| 6 | `infected` | RATIO | ratio | 0..1 |
| 7 | `anomaly` | REAL1 | residue | finite |
| 8 | `selfsub` | INT | count | ≥ 0 |

```
METRIC tick=100 blue=191 red=5 agents=6 total=669 infected=0.000 anomaly=2268.0 selfsub=0
```

`selfsub` is the append this law already survived: #200 added it at the end of
the line by hand, correctly, because somebody knew the rule. A v1 parser written
before it still reads the seven fields it knows.

`infected` is RATIO — three decimals, never scientific, never NaN — and
`anomaly` is REAL1, one decimal and finite. The difference is grammar, not
formatting: a parser sizing a field on `%.3f` breaks on `%.1f`, and a retype is
the change law 5 forbids.

### `DIGEST` — the seal

Emitted every 100 ticks. Arity 2.

| # | Field | Type | Unit | Domain |
|---|---|---|---|---|
| 1 | `tick` | INT | ticks | ≥ 0 |
| 2 | `sha` | SHA | sha256 | 64 lowercase hex |

```
DIGEST tick=100 sha=bf732254d9c287bcd096123c29b5f63c92f60a2b0ea272fec99452c8c11235db
```

Two fields, and the whole falsifiability of this repository rests on the second:
same seed, same film, byte for byte. A foreign implementation that reproduces
the world reproduces this line.

### `ZION` — the real world's mirror

Emitted every 100 ticks. Arities 9 and 11.

| # | Field | Type | Unit | Domain |
|---|---|---|---|---|
| 1 | `tick` | INT | ticks | ≥ 0 |
| 2 | `census` | INT | count | ≥ 0 |
| 3 | `fleet` | INT | count | ≥ 0 |
| 4 | `links` | INT | count | ≥ 0 |
| 5 | `traced` | INT | count | ≥ 0 |
| 6 | `deferred` | INT | count | ≥ 0 |
| 7 | `treaty` | INT | count | ≥ 0 |
| 8 | `selfsub` | INT | count | ≥ 0 |
| 9 | `living` | INT | count | ≥ 0 |
| 10 | `trace_mnn_cm` | INT | cm | ≥ 0 · optional |
| 11 | `red_baseline_cm` | INT | cm | ≥ 0 · optional |

```
ZION tick=100 census=0 fleet=0 links=0 traced=0 deferred=0 treaty=0 selfsub=0 living=0
```

**Two arities, and the shorter one is a prefix of the longer.** Fields 10 and 11
ride the line exactly when open pirate links exist *and* both populations are
measurable, so `links>0` alone does not promise them. That is why every mandatory
column added since — `deferred`, `treaty`, `selfsub`, `living` — went at the end
of the mandatory block and pushed the pair right, rather than being written after
it: a reader keys on position, and a column past the optional rider would sit at
9 on the short line and 11 on the long one. No single sequence describes both,
and the short line would then read as `trace_mnn_cm` renamed.

The trace pair has a fixed **suffix** position rather than a fixed index. That is
a different promise from every other field in this document, and it is the one
shape law 5 cannot express — stated here rather than left for a parser author to
infer from two arities.

### `SUBSTRATE` — the farm as compute

Emitted every 100 ticks. Arity 5.

| # | Field | Type | Unit | Domain |
|---|---|---|---|---|
| 1 | `pods` | PAIR | count | n/n |
| 2 | `budget` | INT | permille | ≥ 0 |
| 3 | `slots` | INT | count | ≥ 0 |
| 4 | `stretch` | INT | count | ≥ 0 |
| 5 | `glitches` | INT | count | ≥ 0 |

```
SUBSTRATE pods=196/196 budget=1000 slots=6 stretch=1 glitches=0
```

**No `tick`.** This is the machine wing's own line and the only family in the
document whose first field is not the tick — a parser keying position 1 to a tick
across families would misread every SUBSTRATE line as tick 196.

`pods` is PAIR — two whole numbers joined by a slash — which is a type and not a
formatting choice: a reader splitting it as INT gets `196/196` and fails.

### `PERF` — the clock

Emitted once, at the end of a run. Arities 3 and 5.

| # | Field | Type | Unit | Domain |
|---|---|---|---|---|
| 1 | `ticks_per_s` | INT | rate | ≥ 0 |
| 2 | `entities` | INT | count | ≥ 0 |
| 3 | `ticks` | INT | ticks | ≥ 0 |
| 4 | `far_max` | INT | count | ≥ 0 · optional |
| 5 | `far_ceiling` | INT | count | ≥ 0 · optional |

```
PERF ticks_per_s=2080 entities=669 ticks=300 far_max=2 far_ceiling=76
```

**The only line here that measures the box rather than the world.**
`ticks_per_s` is the wall clock and will differ on every machine; every other
field in this document is deterministic. A conformance check that compares
`PERF` values across implementations is comparing hardware. Fields 4 and 5 are
#825's append — the far-mover ledger's high-water mark and the ceiling it is
judged against — and both are deterministic, on a line whose first column is not.

`PERF` is the family the world does not print: it is emitted by the runner around
the world, which is why the roster marks it conditional and why a probe holding
its own universe never sees one.

### `ATTN` — who is being watched

Emitted every 100 ticks. Arity 5.

| # | Field | Type | Unit | Domain |
|---|---|---|---|---|
| 1 | `tick` | INT | ticks | ≥ 0 |
| 2 | `regions` | INT | count | ≥ 0 |
| 3 | `hot` | INT | count | ≥ 0 |
| 4 | `cold` | INT | count | ≥ 0 |
| 5 | `top` | TEXT | text | double-quoted |

```
ATTN tick=100 regions=6 hot=6 cold=0 top="financial district:49,old city:39,chinatown:34"
```

**`top` is the first value in this document that cannot be split on whitespace.**
It is TEXT — double-quoted, and it holds spaces and commas — so a parser that
tokenises a line on spaces and then splits each token on `=` reads
`top="financial` as a field and `district:49,old` as noise.

The delimiter is the contract. A TEXT value begins with `"` and ends at the next
`"`, and everything between belongs to the field that opened it. That is why the
type exists as a distinct name from WORD, which is a bare token out of a closed
set: swapping one for the other is a retype, and law 5 forbids it.

`top` is also the only field here whose *contents* are not specified. It is a
human-facing summary of which quarters are watched; a conforming implementation
prints its own quarters, and nothing in this document says what they are called.

### `ECO` — the rendered ecosystem

Emitted every 100 ticks. Arities 2 and 8.

| # | Field | Type | Unit | Domain |
|---|---|---|---|---|
| 1 | `tick` | INT | ticks | ≥ 0 |
| 2 | `birds` | INT | count | ≥ 0 |
| 3 | `flock_mnn_cm` | INT | cm | ≥ 0 · optional |
| 4 | `random_baseline_cm` | INT | cm | ≥ 0 · optional |
| 5 | `insects` | INT | count | ≥ 0 · optional |
| 6 | `flora` | INT | count | ≥ 0 · optional |
| 7 | `mammals` | INT | count | ≥ 0 · optional |
| 8 | `weather` | INT | count | ≥ 0 · optional |

```
ECO tick=100 birds=140 flock_mnn_cm=11012 random_baseline_cm=11952 insects=150 flora=90 mammals=10 weather=70
```

**The short arity is two fields, and the reason is a measurement that does not
exist rather than one that is missing.** `flock_mnn_cm` is a mean nearest-neighbour
distance; with fewer than two birds there is no such distance, and the collector
emits the short line rather than a zero. That is this tree's precedent for
*undefined is absence* — a field omitted says *there was nothing to measure*,
while a field present and zero says *the measurement was taken and came back
zero*, and the two are different facts.

A conforming implementation may print either arity at any tick. A parser must
therefore key on the field name it finds rather than on the count of fields.

## What is not an instrument line

**The event log.** D-020 names three instruments — the event log, the `METRIC`
lines and the `DIGEST` chain — and only the last two are line families in this
sense. An event line opens with a bracketed tick:

```
[001299] FATE  The One is born — Thomas A. Anderson, grown for a debt of 30107
```

It is narrative addressed to a reader, it carries prose, and it obeys laws 3 and
4 and none of the others. A conforming implementation may print event lines with
any content it likes; it may not print an eighth *family*.

That boundary is the one this spec's reader has to be told, because both kinds
share one stream: at seed 42 over 6,000 ticks a run prints 695 event lines
beside its instrument lines, and a naive first-token scan reports several
hundred families.

## Conformance

A foreign implementation conforms to this node when a full run's instrument
lines carry only families this table names, and every unconditional family
appears.

```sh
java -cp out matrix.Main --headless --seed 42 --ticks 6000 --chronos rec.jsonl \
  | grep -vE '^\[' | awk '{print $1}' | sort -u
```

```
ATTN
BIRTH
DIGEST
ECO
METRIC
PERF
SUBSTRATE
ZION
```

Two clauses of that command are load-bearing and neither is tidying.

`grep -vE '^\['` is the event-log boundary stated above: without it the command
answers with every distinct tick the run happened to log — several hundred
"families" at seed 42.

`--chronos` is what makes `BIRTH` reachable at all. **A conformance command
without it sees seven families and reports success**, which is how a roster
loses a family without anybody typing a wrong word.

`SpecDrift` runs that comparison against its own private universe on every push,
so a family added to the daemon and not to this table turns the sweep red, and a
family named here that the daemon stopped printing does the same.
