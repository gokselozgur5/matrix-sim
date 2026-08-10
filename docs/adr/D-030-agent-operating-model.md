# D-030 — Multi-agent operating model: crews, supervision, proofs

Status: 🟢 accepted · Phase gate: — · Thread: —

## Context
Parts of the build will be delegated to AI subagents; unreviewed swarm output is how repos rot.

## Decision
Accepted (owner commissioned, 2026-08-10): agents work as small bounded crews (default 3-5 concurrent, hard ceiling min(16, cores-2)) in isolated worktrees with no push rights and no sub-agents; a deliverable is a diff plus a passing proof command; nontrivial claims get an adversarial second pass; every accepted diff passes compile + double-run digest compare; a yellow decision halts an agent exactly as it halts a human; the orchestrator reads everything before it lands and the human gates merges as always. Full text: PRINCIPLES.md, Agent principles.

## Consequences
Parallelism scales only with independently verifiable shards; the human reviews outcomes instead of babysitting processes.
