---
title: "D-030 — Multi-agent operating model: bounded crews, proofs, the digest leash"
status: accepted
date: 2026-08-10
decision-makers: gokselozgur5 (owner), the resident machine (pair)
consulted: thread —
informed: phase tracker —
---

# D-030 — Multi-agent operating model: bounded crews, proofs, the digest leash

*In the context of delegating build work to AI subagents, facing the failure mode of unreviewed swarm output rotting a repo, we decided for bounded crews with proof-carrying deliverables under an adversarial review and a digest gate, and against free-form delegation, to achieve safe parallelism, accepting orchestration overhead.*

## Context and Problem Statement

The owner commissioned the model explicitly: how many agents, how supervised, how tested, what expectations, what principles. The answers became Ag1-Ag8 in PRINCIPLES.md; this record fixes them as a decision.

## Decision Drivers

* No agent output lands unread (the orchestrator reads, the human gates)
* Blast radius is a design parameter (worktrees, no push rights, no sub-agents)
* A deliverable is a diff plus a passing proof command
* Decision gates bind agents exactly as they bind humans
* Parallelism must match the host: hard ceiling min(16, cores-2); this box: 4 cores, 2 concurrent slots

## Considered Options

* Bounded crews + proofs + adversarial pass + digest gate (Ag1-Ag8)
* Free-form delegation (spawn and hope)
* No agents at all

## Decision Outcome

Chosen option: "bounded crews under the digest leash", commissioned and accepted by the owner on 2026-08-10. Full text: PRINCIPLES.md, Agent principles (Ag1-Ag8).

### Consequences

* Good, because Agent-built work arrives as reviewable, provable diffs
* Bad, because Mission framing is real work for the orchestrator (that is the job)

### Confirmation

Every agent-produced PR carries a proof block (compile + double-run digest diff + task-specific checks); worktree-only branches; a skeptic pass is logged for nontrivial claims.

## Pros and Cons of the Options

### Bounded crews + proofs + adversarial pass + digest gate (Ag1-Ag8)

* Good, because parallelism without repo rot; every claim arrives with evidence
* Good, because the human reviews outcomes, not processes
* Good, because agents inherit the house rules through the auto-loaded door (CLAUDE.md)
* Neutral, because crew sizes stay small on modest hardware (honest, not sad)
* Bad, because orchestration overhead per mission (framing, verification, review)
### Free-form delegation (spawn and hope)

* Good, because fastest apparent throughput
* Bad, because merges become archaeology; trust erodes after the first silent regression
### No agents at all

* Good, because simplest supervision
* Bad, because forfeits legitimate parallelism on independent, verifiable shards

## More Information

Related: [D-028](D-028-five-document-canon.md), [D-020](D-020-observability-contract.md). Principles: Ag1-Ag8.
