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
