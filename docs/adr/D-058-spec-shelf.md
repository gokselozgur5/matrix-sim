---
title: "D-058 — The spec shelf: docs/spec/ becomes a sanctioned document kind"
status: accepted
date: 2026-08-12
decision-makers: gokselozgur5 (owner), the resident machine (pair)
consulted: thread #252
informed: the Spec program, milestone-wide
---

# D-058 — The spec shelf: docs/spec/ becomes a sanctioned document kind

*In the context of a world whose rules live only inside one Java implementation, facing the emulator scene's fifteen-year lesson that data survives and closed math dies, we lean toward a sanctioned `docs/spec/` shelf holding portable, implementation-independent specifications with conformance vectors, and against burying the rules in code comments or inflating the five-document canon, to achieve a world that outlives any single implementation — including ours — accepting one more sanctioned document kind and the drift risk that comes with it.*

## Context and Problem Statement

The archaeology dig into The Matrix Online (thread #212) produced one finding that outweighed all the mechanics it catalogued: sixteen years after the servers closed, the emulator scene has restored the entire explorable world — every door, chat, trade, faction, cosmetic — and still states that **combat is not implemented**. What survived was data and protocol. What died was the contest math, because it existed only as logic inside one closed implementation.

This repository is currently in exactly that position. The digest frame grammar, the chronos record kinds, the instrument line families, the arc beat needles, and the fate derivation all exist as Java. A stranger with the repository could rebuild the world; a stranger without it could rebuild nothing, and even we could not verify a second implementation.

The obstacle was constitutional, not technical: D-028 sealed the canon at five documents and D-029 fenced `docs/adr/`. A new directory of documents needs its own record — which is this one.

## Decision Drivers

* Data survives; closed math dies (the MxO lesson, evidenced by a live emulator scene)
* The five-document canon must not inflate — canon is prose about the project, specs are portable descriptions of its rules
* A spec without conformance vectors is a wish; vectors make it falsifiable
* Specs must be kept honest by machine, or they become the second lie (drift)
* The cross-platform proof (macOS ≡ Debian ≡ Ubuntu) already implies a portable definition — it should be written down

## Considered Options

* A sanctioned `docs/spec/` shelf with a drift probe
* Specs as expanded javadoc inside the source
* No specs — the implementation is the specification

## Decision Outcome

Chosen: **the shelf**, accepted by the owner's verdict in session, 2026-08-12 (thread #252): *"olur."*

The kind is defined narrowly so it cannot swallow the canon: a spec describes a RULE OF THE WORLD in implementation-independent terms (byte grammars, record kinds, derivations, line families), carries test vectors a foreign implementation can verify itself against, and never carries narrative, rationale, or history — those belong to the five and to the ADRs. `SpecDrift` (#260) checks the shelf against the running implementation, so a stale spec fails a build rather than misleading a reader.

### Consequences

* Good, because the world becomes reproducible without this codebase — a Python implementation could derive Trinity's sheet identically
* Good, because the conformance kit turns the cross-platform claim into something a stranger can check
* Good, because it forces the rules to be stated cleanly, which is its own review
* Bad, because a fourth document kind is a fourth thing to keep true; the drift probe is not optional, it is the price of admission

### Confirmation

`docs/spec/` exists with at least the digest frame grammar and a conformance vector file; `SpecDrift` runs in CI and fails on a deliberately altered spec; the README's documentation policy names the kind and its narrow definition.

## Pros and Cons of the Options

### The sanctioned shelf

* Good, because portability is a property of the written rule, not of the code that happens to run it
* Good, because vectors make every spec falsifiable
* Bad, because it needs a drift guard, and guards need maintenance

### Expanded javadoc

* Good, because zero new structure
* Bad, because it is the exact failure mode the MxO dig documented: the rule dies with the implementation it lives inside

### No specs

* Good, because nothing to maintain
* Bad, because the day this repository stops building, the world stops being reconstructible — and we have written a year-long plan for a world we intend to outlive us

## More Information

Related: [D-028](D-028-five-document-canon.md) (the canon this does not join), [D-029](D-029-adr-expansion.md) (the fence this parallels), [D-020](D-020-observability-contract.md) (the grammars the shelf first records), [D-023](D-023-chronos-event-sourcing.md), [D-054](D-054-the-year.md) (the Spec program). Thread: #252.
