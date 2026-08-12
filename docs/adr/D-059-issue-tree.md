---
title: "D-059 — The issue tree: work branches until a leaf is one PR"
status: accepted
date: 2026-08-11
decision-makers: gokselozgur5 (owner), the resident machine (pair)
consulted: thread #358
informed: every milestone and program
---

# D-059 — The issue tree: work branches until a leaf is one PR

*In the context of a year-long project whose board crossed 140 open issues in one afternoon, facing a flat list that cannot express how work actually nests, we lean toward native sub-issue trees under a partition law — a node branches until every leaf is exactly one PR — and against flat lists or checklist-in-body decomposition, to achieve a board where the core's core is itself addressable, schedulable, and closable, accepting that parents now carry a decomposition contract they must honour.*

## Context and Problem Statement

The Architect's directive, verbatim in session (2026-08-11): *"issue'lar binary tree gibi dallanıp budaklanabilir hale gelsin — çekirdeğin çekirdeğine inip çekirdeğini issue'landırabilelim ve onun da kendi dalları oluşturulabilsin."* A flat board hides the two things a long project needs most: what a big item actually CONTAINS, and where the smallest real piece of work is. Checklists inside bodies fail because a checkbox cannot be assigned, closed by a PR, discussed in its own thread, or grown further. GitHub ships native sub-issues — parent/child hierarchy with progress — and the repo's delivery law (D-039: one build-unit issue = one PR) already defines what the leaf of such a tree must be.

## Decision Drivers

* D-039's unit-PR law needs a matching decomposition law: the leaf IS the unit
* A year of work must stay navigable — the shape of a task should be readable at a glance
* Sub-issues are native (closable, assignable, milestoned, progress-tracked) — no invented machinery
* Crews decompose in parallel; without a partition rule they overlap and leave gaps
* Depth must be earned: splitting for its own sake produces hollow rows, the one banned thing

## Considered Options

* Native sub-issue trees under a partition law (with `tools/subissue.sh` + `tools/issuetree.sh`)
* Checklists inside issue bodies
* Flat list plus naming conventions ("Part 1 of 3")

## Decision Outcome

Accepted by the owner's verdict, 2026-08-12, in session — *"hepsine agreed kanka barajı aç"* — all thirteen Season Three gates in one breath, the same word that opened Season Two; recorded in the gate thread.

Leaning: native trees. **The doctrine:**

1. **Leaf = one PR.** One mechanism, one machine-checkable done-when, one lock tier. If a "leaf" needs three commits with three distinct evidence lines, it is a parent wearing a leaf's clothes.
2. **Parent = more than one PR or more than one sitting.** A parent is a promise about leaves, never work itself.
3. **The partition law.** Children exactly cover their parent: no gaps, no overlap. Therefore **the parent's done-when is the CONJUNCTION of its children's** — and the parent says so, in a `## Decomposition` section appended below its original body (never rewritten: the first statement of a problem is history).
4. **Depth is earned.** Two is normal, three for the core's core, four only where the core genuinely has one. The test is not "can this be split" but "does splitting produce two things a PR would treat differently."
5. **Inheritance.** A child inherits its parent's milestone (or its season line where no milestone exists), names the decisions it executes or awaits, and cross-references its siblings by number.
6. **Closure flows upward.** A parent closes when its children are closed and its conjunction verifies — never before, never by fiat.

### Consequences

* Good, because the smallest real piece of work is finally addressable — the core's core gets a number, a thread, and a PR
* Good, because parallel crews can decompose different branches without collision
* Good, because a parent's progress is visible without reading a body
* Bad, because parents now owe a decomposition contract; a stale `## Decomposition` section is the same class of lie as an outdated crown (Dev4)

### Confirmation

`tools/issuetree.sh <n>` prints a depth-3+ tree with leaf/parent marks (live at merge time: #352 → #357 → #359); `tools/subissue.sh <parent> "<title>" body.md` creates and links in one motion, inheriting the milestone; every decomposed parent carries its `## Decomposition` section with the conjunction stated.

## Pros and Cons of the Options

### Native sub-issue trees

* Good, because every node is a first-class issue: assignable, closable, discussable, further divisible
* Good, because GitHub renders the hierarchy and its progress for free
* Neutral, because the API is young; the two shop tools hide its edges
* Bad, because a tree can grow deeper than the work is real — hence the earned-depth rule

### Body checklists

* Good, because zero ceremony
* Bad, because a checkbox has no thread, no PR, no children — the core's core stays unaddressable

### Flat list with naming conventions

* Good, because it works in any tracker
* Bad, because the relationship lives only in prose, and prose drifts

## More Information

Related: [D-039](D-039-unit-pr-granularity.md) (the leaf's definition), [D-054](D-054-the-year.md) (the year this board must carry), [D-000](D-000-process-constitution.md). Thread: #358. Principle: Dev13.
