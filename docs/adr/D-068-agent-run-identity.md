---
title: "D-068 — Agent run identity and causal audit: names are not keys"
status: accepted
date: 2026-09-05
decision-makers: gokselozgur5 (owner), Aether (Matrix steward)
consulted: thread #1776
informed: Orchestra #1774
---

# D-068 — Agent run identity and causal audit: names are not keys

*In the context of Aether, Codex, Claude/Signal and future workers carrying one
long-lived project across short-lived executions, facing anonymous output,
unverifiable lineage, provider-session correlation and the secret liability of
raw transcript capture, we decided for orchestra-issued role identity, a random
per-run passport and a causally complete but content-minimized semantic event
ledger, and against literal keylogging, public provider-native session IDs,
self-asserted identity or identity-as-authorization, to achieve reconstructable
accountability without building a credential leak or surveillance archive,
accepting a trusted recorder, storage/verification cost and an explicit
redaction boundary.*

## Context and Problem Statement

[D-030](D-030-agent-operating-model.md) gives crew missions bounds, proofs and
an adversarial leash. [D-034](D-034-session-ritual.md) gives every session a
deliberate entrance and hardline exit, but rejected heavyweight session logs
because forms can outlive meaning. Orchestra v1 (#1774) binds reviews and
merges to exact commits, yet it cannot answer which short-lived run performed
or justified each effect.

Göksel asked for every Aether and Claude session to have a
nüfus-cüzdanı-like identity and for the record to explain what, why, how, when
and in what manner the work happened, down to "every keystroke." The literal
version would collect passwords, tokens, raw prompts, private paths, terminal
streams and hidden reasoning while still failing to state which authority
caused an effect. AI work has a better boundary than physical keys: the tool,
decision and mutation boundaries visible to the orchestra.

Provider-native identifiers are also the wrong public identity. OpenAI's
Responses API documents `conversation` and `previous_response_id` as handles
that attach prior items to later requests. The documentation does not say the
identifier alone authorizes retrieval, but the continuation/correlation role
is sufficient to keep it private.

The lenses used are **Secret** (what must never enter the ledger), **Who**
(which issuer can attest identity and authority), and **Failure** (what happens
when an effect occurs between intent and result).

## Decision Drivers

* A stable persona, a model process, a provider conversation and an authorized
  capability are different things.
* An identity label must never grant filesystem, network, repository or GitHub
  authority.
* A worker cannot be the sole issuer or verifier of its own identity.
* The record must reconstruct every orchestra-visible causal action without
  retaining raw prompts, hidden reasoning or secret-bearing terminal bytes.
* Resume, retry, fork, crash and model replacement must preserve lineage
  without extending a long-lived bearer handle.
* Mutations and external writes need crash-reconcilable before/after evidence.
* Redaction must happen before serialization and hashing; hashing a
  low-entropy secret is not redaction.
* A hash chain is tamper-evident only after an independent trusted anchor fixes
  its terminal root.
* The ledger must not dirty the subject repository or recursively log its own
  append.
* Confirmation must exercise behavior and realistic mutants, not search for
  reassuring field names.

## Considered Options

* Literal keystroke, prompt and terminal transcript capture.
* Provider-native conversation/session/response ID as the public identity.
* Receipt-only status quo without per-run identity or action lineage.
* Orchestra passport plus a content-minimized semantic event ledger.
* An alternative preserving the same identity/auth, secrecy, issuance,
  failure and verification boundaries.

## Decision Outcome

Chosen option: **orchestra passport plus semantic ledger**, accepted in
[#1776](https://github.com/gokselozgur5/matrix-sim/issues/1776#issuecomment-5551401406).
"Every keystroke" means causally complete semantic action/effect coverage at
orchestra-visible boundaries. It explicitly does not mean OS keylogging, raw
prompt/output retention, terminal transcript collection or hidden
chain-of-thought capture.

### Identity is not authority

The trusted orchestra creates a run manifest before the worker receives tools:

```text
schema = orchestra.run/1
agent_id = stable project-scoped role/persona
run_id = OS-CSPRNG value with at least 128 random bits
profile_id = exact role-policy version
provider + requested_model + observed_model
parent_run_id | resumes_run_id
task_ref + repository + full head_before
authority_profile + authority_digest
coverage = COMPLETE | PARTIAL
started_at_utc + issuer
```

`agent_id` names a stable role such as Aether, Signal or a test engineer;
provider and exact observed model remain separate facts. `run_id` names one
orchestra launch and grants nothing. Every fork, retry, resumed provider
conversation, process restart and model replacement receives a fresh
`run_id` with an explicit lineage edge.

The manifest is read-only to the worker. Authority is separately granted,
scoped, checked and revoked; the manifest merely records the profile and its
digest. No authorization check may succeed because an `agent_id` or
`run_id` matches.

`COMPLETE` is a mechanical claim, not a worker assertion. It requires the
trusted recorder to mediate the only available tool/effect path while direct
shell, filesystem, network and external-write bypasses are absent. A passive
hook or callback that the worker/provider can skip is `PARTIAL`. A
write-capable run may not proceed under `PARTIAL`; a read-only scout may, but
its receipt must carry that limitation and cannot be promoted into mutation
evidence.

### Causal completeness, not content capture

The canonical event vocabulary covers lifecycle, reasoning products and tool
effects without pretending to capture private model cognition:

```text
RUN_START  RUN_RESUME  RUN_FORK  RUN_END  RUN_ABORT
PLAN       DECISION    DELEGATE
READ       TOOL_INTENT TOOL_RESULT
MUTATION_INTENT        MUTATION_RESULT
EXTERNAL_WRITE_INTENT  EXTERNAL_WRITE_RESULT
VERIFY     REVIEW      RECONCILE
```

Every event binds the schema, run, monotonically increasing sequence, kind,
agent, task and authority digest; a concise safe what/why/expected-effect
summary; a normalized non-secret target; observed before/after state where
applicable; a safe result and duration; allowed artifact digests; the previous
event hash; and its own canonical hash. Sequence is authoritative for order;
UTC time is diagnostic.

Reasons are compact accountability summaries, not raw internal reasoning.
Raw prompts, chain-of-thought, command streams and tool payloads are outside
the public event grammar. Every string and event has an explicit byte bound;
target classes and result fields are typed rather than arbitrary transcript
slots. Exhaustion is visible and freezes new effects instead of silently
dropping events.

### Effects are two-phase

Every file mutation, commit, push, issue/PR edit, comment, approval, merge,
secret/configuration change or other external effect requires a durable intent
event before execution and a durable result event containing observed state
after execution.

If intent cannot be appended durably, the effect is refused. If the effect
occurs but its result cannot be recorded, all further write authority freezes.
The next run must reconcile the pending intent against external state before
any new mutation. A read-only failure may end as `RUN_ABORT`, but cannot be
reported as completed evidence.

The recorder's own append is transport rather than a recursively logged
action. The canonical event and its hash-chain position prove the append.
Two-phase recording exposes partial effects but does not make an external
operation transactional or prevent a race; the underlying exact-head and
service-specific compare-and-swap gates remain necessary.

### Bootstrap has one honest beginning

The sessions deciding and implementing D-068 predate a trusted issuer and
therefore remain legacy runs identified only by their existing issue, commit
and CI receipts. They never receive retroactive `run_id` values: a
self-issued historical passport would be the first forged identity in the
system.

The first compliant run begins only after an exact commit installs the issuer,
verifier and trusted anchor. That commit and the separately protected
verification-key fingerprint form the genesis reference; the signing secret
stays outside every worker. If bootstrap cannot establish that separation, the
system reports `PRE_IDENTITY` or `PARTIAL` rather than claiming D-068
coverage.

### Privacy, retention and anchoring

Redaction precedes hashing, serialization, model access and upload. Secrets and
low-entropy credentials become typed tombstones; their original values and
guessable digests are absent.

The durable/public layer contains safe manifest fields, semantic summaries,
artifact SHAs, verdicts, redaction counts and the terminal receipt. A
`provider_session_ref` is never public. It is not collected when resume is
unneeded; otherwise only a restricted encrypted mapping from `run_id` may
retain it for an explicit finite resume window.

The content-minimized semantic ledger is encrypted at rest and retained for
the lifetime of the matrix-sim project unless Göksel explicitly shortens that
policy; its terminal receipt remains durable with the project. Provider
continuation state follows the opposite rule: a clean hardline deletes the
mapping immediately, while an aborted run or unresolved pending effect may
retain it for reconciliation for at most seven days. After that, continuity
comes from the safe handoff and a new run rather than a resurrected provider
session.

The append-only high-volume ledger remains outside the subject repository.
Only a compact receipt binds:

```text
run_id + agent_id + task_ref
head_before + head_after
event_count + first_hash + final_root
pending_events=0 + redactions_by_class
authority_digest + verifier_version
status + start/end time
issuer signature or independently trusted anchor
```

The signing or anchoring capability is inaccessible to workers. The exact-head
GitHub publisher may anchor a safe terminal receipt; Gospel and daily memory
receive only the human handoff, never provider handles or the event stream.

### Consequences

* Good, because artifacts, decisions and effects gain reconstructable
  run/agent/authority lineage.
* Good, because resuming a model does not turn a provider handle into a
  long-lived public identity.
* Good, because a crash between intent and result becomes visible debt rather
  than an invented success.
* Good, because the ledger records meaning while refusing hidden-reasoning and
  raw-secret surveillance.
* Bad, because every effect-bearing adapter must cooperate with a trusted
  recorder and fail closed when it cannot.
* Bad, because private resume mappings need encryption, access control and
  deletion policy.
* Bad, because signatures, reconciliation and verifier versioning add
  operational machinery.
* Neutral, because a semantic ledger is deliberately not a replay of every
  terminal byte; its claim is causal accountability, not forensic omniscience.

### Confirmation

The decision record itself is confirmed by document/index agreement,
back-edge checks, normal documentation probes, the unchanged world digest and
the accepted verdict in #1776.

Realization is confirmed only when behavior fixtures show that concurrent
launches cannot collide; workers cannot replace their identity, authority or
lineage; reordered/deleted/duplicated/modified events and wrong-head receipts
fail; an unavailable recorder prevents an effect; a crash after intent leaves
write-blocking pending debt until reconciliation; resume/fork produces a new
linked run; secret-bearing values are tombstoned before serialization and are
not replaced by guessable hashes; provider IDs never reach public artifacts;
identity never satisfies authorization; and a worker cannot mint a valid
issuer receipt.

The suite must also prove that a bypassable passive hook cannot claim
`COMPLETE`, a write-capable `PARTIAL` run is refused, bounded-field or
event-count exhaustion cannot silently omit an effect, and pre-genesis work
cannot be backfilled with a valid identity.

At least one retained mutant must preserve the reassuring schema strings while
breaking each security property and still turn the suite red.

## Pros and Cons of the Options

### Literal keystroke or transcript capture

* Good, because it creates a large chronological archive.
* Bad, because it collects credentials, private data, raw prompts and hidden
  reasoning without proving authority or causal effect.
* Bad, because logging the logger creates either infinite regress or an
  unstated observation boundary.

### Provider-native ID as public identity

* Good, because provider tools already emit it.
* Bad, because identity becomes coupled to one vendor and continuation handle.
* Bad, because reuse across tasks and repositories creates correlation and
  accidental lifetime extension.

### Receipt-only status quo

* Good, because it is small and already shipped in #1774.
* Bad, because it cannot attribute intermediate decisions, tools, pending
  effects, resume lineage or authority profiles to one run.

### Orchestra passport plus semantic ledger

* Good, because role, execution, model, lineage, authority and provider context
  remain separate.
* Good, because two-phase events make partial external effects reconcilable.
* Good, because the durable record is useful without containing raw secrets or
  provider handles.
* Bad, because trusted issuance, anchoring and storage retention are real pets
  that must be fed.

## More Information

Related: [D-020](D-020-observability-contract.md),
[D-030](D-030-agent-operating-model.md),
[D-034](D-034-session-ritual.md), and
[D-039](D-039-unit-pr-granularity.md). Delivery machinery: #1774/#1775.
Decision and exact stewardship verdict: #1776.

D-068 narrows only D-034's rejection of heavyweight session logs: a raw
transcript archive remains rejected, while a content-minimized causal event
ledger is accepted. D-034's short jack-in/work/hardline ritual remains law.

Implementation follows as separate D-039 leaves: canonical schemas plus
issuer/recorder/verifier; Codex/Claude adapters plus private resume mapping and
crash reconciliation; and exact-head anchoring plus retention/redaction
enforcement. This record alone ships no recorder, provider integration,
credential store, surveillance path, unattended merge or company-repository
authority.
