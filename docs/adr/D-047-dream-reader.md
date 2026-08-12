---
title: "D-047 — The dream reader"
status: accepted
date: 2026-08-11
decision-makers: gokselozgur5 (owner), the resident machine (pair)
consulted: thread #217
informed: milestone v6.0
---

# D-047 — The dream reader

*In the context of a perception feed read by grep and jq, facing the truth that nobody ever SEES the dream, we lean toward an observer-side prose renderer — one mind's day folded from its feed and the event log into narrated text, a teleprinter, never a GUI — and against domain rendering or the status quo, to achieve D-021's promise with a human-shaped reader, accepting that the narrator's voice is a design choice the gate must make.*

## Context and Problem Statement

D-019 banned presentation from the domain and the ban stands: the daemon stays blind. But the feed was always 'the system's true output' (D-021) and its only consumer is a pipeline. A dream reader lives OUTSIDE (tools/ or the bench): it tails or post-folds one link's JSONL + the log's lines about them into prose — 'Otto woke before the birds; by the four-hundredth window he no longer believed the rain.' The teleprinter is backend-pure: text out of text.

## Decision Drivers

* Domain untouched — the reader consumes instruments, never entities
* Deterministic prose: same run, same story, byte for byte
* The narrator's voice IS the product: system-cold vs in-world (the Oracle narrating would tie into D-043)
* Cheap first, beautiful later: a v1 that renders one day honestly beats a v3 that never ships

## Considered Options

* tools/ teleprinter (post-run compiler)
* Live tail mode
* Status quo (grep remains the only reader)

## Decision Outcome

Accepted by the owner's verdict, 2026-08-12, in session — *"hepsine agreed kanka barajı aç"* — all thirteen Season Three gates in one breath, the same word that opened Season Two; recorded in the gate thread.

Leaning: the post-run teleprinter first; live tail as its second commit. Awaiting the Architect's verdict in the gate thread (#217); the machine performs the flip on his word.

### Consequences

* Good, because someone finally sees the dream
* Good, because it makes every future mechanic REVIEWABLE as story (the Architect reads days, not diffs — D-037 deepened)
* Bad, because voice bikeshedding is a real risk (one voice ships v1; more are units)

### Confirmation

One command renders one named mind's full-arc day as deterministic prose; the double run diffs empty; the Architect reads a day and learns something the METRIC line never told him.

## Pros and Cons of the Options

### Teleprinter

* Good, because pure, cheap, honest
* Bad, because post-run only at v1

### Live tail

* Good, because watching feels like jacking in
* Bad, because pacing/backpressure design before any value ships

### Status quo

* Good, because nothing to do
* Bad, because the dream stays unseen — the ascetic bug, unfixed

## More Information

Related: [D-021](D-021-perception-feed.md) (the promise this keeps), [D-019](D-019-backend-only.md) (the law this respects), [D-043](D-043-named-cast.md) (the Oracle's voice option). Thread: #217.
