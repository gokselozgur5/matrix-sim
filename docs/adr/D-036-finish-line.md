---
title: "D-036 — The finish line and the scope contract"
status: accepted
date: 2026-08-10
decision-makers: gokselozgur5 (the Architect), the resident machine (the Oracle)
consulted: —
informed: ROADMAP.md ("The Finish Line")
---

# D-036 — The finish line and the scope contract

*In the context of a project whose ideas multiply faster than its code, facing the owner's observation that reaching an end requires deciding what and how much at the start, we decided for a written finish line (the v3.0 arc) with sealed scope quantities, an explicit never-list and an epilogue clause, and against an open-ended playground, to achieve a reachable end, accepting that good ideas will be parked on principle.*

## Context and Problem Statement

Every phase had a definition of done; the project had none. The v4.0 backlog grew with every conversation (Zion, the Kid, Chronos, attention-LOD, the Analyst), and an undefined end turns a backlog into a queue that never empties. The owner pulled the brake: what is the end, and how much of each thing gets built to reach it?

## Decision Drivers

* A measurable end exists only where a command can prove it
* Scope grows silently unless quantities are sealed and "never" is written
* The inventory of crowns is a natural budget: code without a crown is scope creep by definition
* Epilogue ideas deserve a home that is not a promise

## Considered Options

* Finish line at the v3.0 arc DoD; v4.0 declared epilogue
* Finish line at v2.5 (ecosystem as the end)
* No finish line (open-ended playground)

## Decision Outcome

Chosen option: "the v3.0 arc is the end", because it is the one finish that a single command can prove. **THE END:** `--headless --ticks 6000 --seed 42` plays the full film deterministically (The One is born → "I DIDN'T" → OVERFLOW → "Peace." → REBOOT v7.0), all v1–v3 crowns are closed, the docs tell the truth, and the D-027 budgets hold. On that day the project is COMPLETE. v4.0 "Resurrections" is epilogue: played for joy if played at all, never owed. Accepted by the owner, 2026-08-10.

**Sealed quantities:** v1.0 ≈ 200 entities, the existing crown inventory is the class budget (new class = crown first, code second); v2.0 infection > 0.50; v2.5 ≥ 500 entities, 12 species suffice; v3.0 full arc < 5 s. The place graph ships as a skeleton in v1 (a few zones + phone-booth exits); cartography is epilogue.

**The never-list:** no GUI (already law, D-019), no networking or multiplayer, no external database, no machine learning. These conversations are pre-closed.

**Epilogue pocket (parked, crownless, promiseless):** the untold sixty years — voluntary reinsertion (the Cypherite arc: negative resistance in the acceptance loop), defector programs migrating to the free side, the Analyst's fear/desire acceptance strategy as a v7+ governance A/B. The owner's amendment stands: Resurrections is not on the never-list — it is the epilogue's name.

### Consequences

* Good, because "are we done" is now a command, not a mood
* Good, because parking ideas stops feeling like losing them — the pocket is canon
* Bad, because discipline must hold when the next beautiful idea arrives mid-phase

### Confirmation

ROADMAP.md opens with "The Finish Line" quoting the end command; no v4.0 item carries a commitment verb; every new class entering a phase PR has a pre-existing crown; the never-list is cited to close out-of-scope proposals.

## Pros and Cons of the Options

### The v3.0 arc as the end

* Good, because provable, near, and the story's natural curtain
* Neutral, because the epilogue remains available without being owed
* Bad, because some beloved ideas (Zion fleet, the Kid) live past the finish line by design

### v2.5 as the end

* Good, because even nearer
* Bad, because the film ends mid-act: an ecosystem with no One, no treaty, no reboot

### No finish line

* Good, because nothing is ever declined
* Bad, because nothing is ever finished — the exact failure the owner named

## More Information

Owner's framing: "we must decide what and how much at the start, to be able to reach the end." Related: [D-027](D-027-performance-budgets.md) (budgets), [D-032](D-032-pirate-broadcast.md)/[D-033](D-033-self-substantiation.md) (epilogue residents), [D-037](D-037-theory-practice-split.md) (who verifies the road to this end).
