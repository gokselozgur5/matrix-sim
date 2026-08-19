---
title: "D-063 — Outcomes first: the graph witnesses delivery, never directs it"
status: proposed
date: 2026-08-19
decision-makers: gokselozgur5 (owner), the resident machine (pair)
consulted: thread #1668
informed: phase tracker #1665
---

# D-063 — Outcomes first: the graph witnesses delivery, never directs it

*In the context of building an inhabited Matrix under a mature delivery discipline, facing contribution targets that can make the evidence apparatus choose work for the world, we lean toward outcome-first delivery — shipped human capability and proven world behavior above supporting evidence, delivery health and activity diagnostics — while retaining unit issues, atomic commits, the issue tree, rebase history and genuine skeptical review for their quality value, and against both contribution quotas and unmeasured craft, to achieve useful progress whose public graph is an honest consequence, accepting that an excellent day may look uneven and that outcomes demand more judgment than counts.*

## Context and Problem Statement

D-054 made 200+ daily contributions and roughly 73,000 yearly contributions part of the project's horizon. D-060 made the four GitHub contribution categories a balanced daily target, and D-061 selected rebase merge as a term that made that target reachable. Those records were explicit, measurable and honestly pursued.

They also completed a control loop the system should distrust: the profile graph stopped being a trace of delivery and became a set-point capable of originating issues, commit boundaries, pull requests and reviews. Once an output measure chooses the work that produces it, empty ceremony is not merely a risk at the edge; it is the shortest path offered by the objective. Calling each artifact “substantive” does not repair the controller if the number remains the reason the artifact exists.

The newer owner direction in #1665 keeps the professional constraints and reverses the objective. Matrix should still plan, build, ship, doubt, preserve atomic history and expose evidence. The question is whether those acts serve a capability in the world, or whether the world serves their count.

This is not a choice between rigor and freedom. It is a choice about what rigor is for.

## Decision Drivers

* The system's objective must point at inhabitants and world behavior, not at the apparatus surrounding them
* Evidence must be strong enough to refute a claim without becoming a substitute for the claim
* Every issue, commit, pull request and review needs a reason independent of its contribution value
* D-039 and D-059 have reviewability, reversibility and closure value even when no graph is measured
* Rebase preserves independently meaningful atomic commits and a linear audit trail regardless of GitHub arithmetic
* Review must distinguish genuine skeptical separation from an author rereading their own argument or collecting a badge
* The public activity graph remains useful as an operational symptom, provided it has no authority to prescribe work

## Considered Options

* Outcome-first delivery with contribution counts as diagnostics only
* A hybrid law: outcome-first work plus minimum volume or balance floors
* Retain D-054/D-060 quotas as delivery objectives

## Decision Outcome

Proposed option: “outcome-first delivery with contribution counts as diagnostics only”, because the delivery system should optimize the Matrix and use its graph to inspect side effects, never optimize the graph and ask the Matrix to justify them. Final call belongs to the owner in thread #1668.

The proposed priority stack is strict:

| Rank | Object | Question | Authority |
|---|---|---|---|
| 1 | Shipped outcome | What human capability or world behavior became real? | May originate and prioritize work after its decision gate |
| 2 | Falsifiable evidence | What causal claim was proved, replayed or refuted? | May add the smallest evidence needed by an outcome |
| 3 | Delivery health | Is work coherent, reviewable, reversible, current and unblocked? | May repair a demonstrated process risk |
| 4 | Activity diagnostic | What did commits, issues, pull requests and reviews happen to record? | May prompt investigation; may not prescribe an artifact |

The rank orders why an artifact exists, not which gate may be skipped. A rank-1 outcome without its rank-2 evidence and applicable rank-3 delivery locks does not merge; lower ranks constrain higher ones even though they cannot originate unrelated work.

An artifact is legitimate only when its author can finish this sentence without naming a contribution count: **“This exists because it changes, proves or refutes ___.”** A metric may reveal that review disappeared or that work has become too large. The resulting issue must fix that demonstrated process failure, not fill the missing graph leg.

The following existing decisions receive explicit dispositions if this proposal is accepted:

* **D-054 is superseded in part.** The 200+ daily and ~73,000 yearly contribution objective, contribution pacing law and graph-based year closure are retired. The one-year horizon, six seasons and continuous programs may remain as planning architecture only while each is connected to accepted outcomes; they create no daily volume obligation.
* **D-060 is superseded as law.** Four-quarter balance, lagging-leg deficits and their correction are no longer targets. `tools/balance.sh` may remain a historical and diagnostic reader, but no verdict from it creates urgency or work. `tools/attribution.sh` is retained and re-grounded as a provenance keeper: commits must resolve to the intended repository identity so personal and work histories do not cross, regardless of whether a profile counts them.
* **D-061 is retained and re-grounded.** Rebase remains the hardline because it preserves separately meaningful atomic commits in a linear, auditable history and avoids strategy-dependent fusion or synthetic merge commits. Contribution arithmetic is no longer its justification.
* **D-039 is retained and re-grounded.** One coherent leaf issue maps to one small pull request; commits remain atomic and locks proportional because this makes change understandable, falsifiable and reversible. Its “CP chain”, balanced-trio and volume rationale are retired.
* **D-059 is retained and re-grounded.** Native trees, exact child partitions and conjunctive parent closure remain because they expose the real shape and ownership of work. A year of volume is no longer why depth exists, and depth for activity is refused.

### Review contract

Self-review and independent review are both useful and are not interchangeable.

* The author performs an adversarial current-head pass and records it honestly as **self-review**. It checks scope, claims, evidence and stale prose, and may request changes. Repeating it for activity is ceremony.
* An **independent skeptic** did not author or edit the reviewed head, receives an explicit refutation mission, names the exact head or tree, and reports the ground swept plus any counterexample. Account identity alone proves neither independence nor quality; a same-owner `COMMENT` may carry a transparent machine review, but never masquerades as a second human approval.
* D-063 does not relax the independent-skeptic lock in D-037/Dev11. If no qualifying skeptic is available, the merge waits. A green badge, an approving tone or a contribution event cannot substitute for the report.

### Consequences

* Good, because the shortest path to success now passes through a person living differently or a world behaving more truthfully
* Good, because a documentation, probe or process change must name the outcome or risk it serves
* Good, because atomic delivery and hard review survive without needing graph arithmetic to justify them
* Good, because uneven contribution days stop being process failures by definition
* Bad, because there is no single daily score that can certify progress
* Bad, because outcome quality requires judgment; decision gates and falsifiers must prevent “impact” from becoming an unmeasured mood
* Bad, because `tools/balance.sh` and historical records will continue to display an attractive number whose reduced authority must remain explicit

### Confirmation

For this proposed documentation gate, `DocLint` must report `VERDICT DOCS_TRUE` for record/index/ROADMAP status agreement, `DocFigures` must report `VERDICT FIGURES_AGREE`, and `DocsRoster` must report no orphan. Anchored searches require `consulted: thread #1668` here plus exact D-063/#1668 rows in `docs/DECISIONS.md` and `ROADMAP.md`. `git diff --name-only origin/main...HEAD` must name exactly those three documentation files and no runtime or tool path.

If accepted, a separate atomic acceptance commit must flip the record, index and ROADMAP gate together; update living process canon in `PRINCIPLES.md`, `CLAUDE.md`, `ROADMAP.md` and the balance/attribution tool descriptions; and add the required supersession/retention links to D-054, D-060, D-061, D-039 and D-059. A repository search for `200+ daily`, `73,000`, `four quarters`, `balanced trio` and `lagging leg` may still find immutable history, but every living instruction must identify those phrases as historical or diagnostic rather than imperative.

Ongoing compliance is falsified by any issue, commit split, pull request, review, priority change or merge whose only rationale is a contribution count. The review asks the artifact's author to name the rank-1 outcome, rank-2 proof or rank-3 process risk it serves. “The graph needed it” is a refusal, not an answer.

## Pros and Cons of the Options

### Outcome-first delivery with diagnostics

* Good, because metrics can reveal process symptoms without becoming the process objective
* Good, because existing professional constraints keep their strongest non-numeric reasons
* Neutral, because contribution volume may still be high; it simply carries no authority
* Bad, because prioritization cannot be delegated to a four-number meter

### Hybrid outcome plus floors

* Good, because a low floor could expose a genuinely missing practice such as review
* Neutral, because it appears safer than removing the quota completely
* Bad, because a binding floor still originates artifacts at the boundary, recreating the same controller at a lower number
* Bad, because every exception requires deciding whether the outcome or the floor wins, so the supposed hybrid hides the real decision

### Retain contribution quotas

* Good, because the target is legible, measurable and motivating
* Good, because D-060's instrument and historical evidence already exist
* Bad, because the easiest compliant action is to manufacture more countable surfaces
* Bad, because the system can satisfy the graph while nobody in the Matrix gains a capability and no world claim becomes truer

## More Information

If accepted, supersedes the numeric objective and pacing clauses of [D-054](D-054-the-year.md) and the normative balance law in [D-060](D-060-the-balance-law.md); retains and re-grounds [D-061](D-061-merge-strategy.md), [D-039](D-039-unit-pr-granularity.md), and [D-059](D-059-issue-tree.md). Preserves [D-037](D-037-theory-practice-split.md)'s division of labor and independent skeptic lock. Related: [D-062](D-062-human-subject-contract.md), whose subject-first outcome this process must serve. Thread: #1668. Parent: #1665. Constitutional root: #1661.
