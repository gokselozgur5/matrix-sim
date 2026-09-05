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
raw transcript capture, we decided for orchestra-issued principal identity, a
random per-run passport and a causally complete but content-minimized semantic event
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

The trusted orchestra creates and canonically serializes a run manifest before
the worker receives tools:

```text
schema + schema_digest
principal_id = stable project-scoped crew identity
role_id = this mission's role
profile_id = exact role/tool-policy version
run_id = OS-CSPRNG value with at least 128 random bits
provider + requested_model
parent_run_id | resumes_run_id
task_ref + repository + full head_before
requested_authority_profile + requested_authority_digest
coverage_preflight = COMPLETE | PARTIAL
redaction_policy_digest + retention_policy_digest
started_at_utc + issuer_id + issuer_key_id
```

`principal_id` names an issuer-controlled crew identity such as Aether or
Signal. `role_id` names a position in this mission such as steward, skeptic or
test engineer. `profile_id` names the exact policy/tool configuration. Changing
a role or profile never creates a new principal, and relabelling either cannot
manufacture independence. Provider and model remain separate facts.

`run_id` names one orchestra launch and grants nothing. The issuer atomically
reserves each generated value with create-if-absent semantics, retries a
collision only within a bounded budget and fails closed if it cannot reserve a
fresh value. Every fork, retry, resumed provider conversation, process restart
and model replacement receives a fresh `run_id` with an explicit lineage edge.

The immutable `manifest_digest` is the event chain's genesis: every event and
terminal receipt binds it. The manifest records the requested model only.
After invocation, a trusted adapter emits `MODEL_OBSERVED` with the provider
attestation source and digest, or the explicit value `UNAVAILABLE`; a requested
alias is never silently promoted to an observed model.

The manifest is read-only to the worker. Its requested authority fields are
not a grant. A separate capability broker issues a signed, run-bound `grant_id`
with issuer, scope, target constraints, validity interval and revocation epoch;
the actual capability remains inside the mediating tool, not in worker input.
The broker emits trusted `AUTHORITY_GRANT`, `AUTHORITY_CHECK`,
`AUTHORITY_DENY` and `AUTHORITY_REVOKE` events. Every tool/effect intent must
reference an allowed check against a currently valid grant. No authorization
check may succeed because a principal, role, profile or run identifier matches.

`COMPLETE` is a mechanical claim, not a worker assertion. It requires the
trusted recorder to mediate the only available tool/effect path while direct
shell, filesystem, network and external-write bypasses are absent. A passive
hook or callback that the worker/provider can skip is `PARTIAL`. A
write-capable run may not proceed under `PARTIAL`; a read-only scout may, but
its receipt must carry that limitation and cannot be promoted into mutation
evidence. A discovered gap emits `COVERAGE_CHANGE`, irreversibly downgrades the
run and freezes writes; the terminal receipt binds final coverage and typed gap
reasons as well as the preflight manifest.

### Causal completeness, not content capture

The canonical event vocabulary covers lifecycle, asserted reasoning products,
authority and tool effects without pretending to capture private model
cognition:

```text
RUN_START  RUN_RESUME  RUN_FORK  RUN_END  RUN_ABORT
MODEL_OBSERVED         COVERAGE_CHANGE
PLAN       DECISION    DELEGATE
AUTHORITY_GRANT AUTHORITY_CHECK AUTHORITY_DENY AUTHORITY_REVOKE
READ       TOOL_INTENT TOOL_RESULT
MUTATION_INTENT        MUTATION_RESULT
EXTERNAL_WRITE_INTENT  EXTERNAL_WRITE_RESULT
VERIFY     REVIEW      INDEPENDENCE_CHECK      RECONCILE
ANCHOR_REQUEST
```

Every event binds the schema and `manifest_digest`; run, monotonically
increasing sequence, `event_id`, kind, principal, role and task; applicable
grant and authorization-check references; a concise safe
what/why/expected-effect summary plus `reason_source`; a normalized non-secret
target; observed before/after state where applicable; a safe result and
duration; allowed artifact digests; explicit causal references; the previous
event hash; and its own canonical hash. Sequence is authoritative for total
order; causal references explain why events are related; UTC time is
diagnostic.

The recorder allocates an `operation_id` when it accepts an intent. A result
must reference that exact operation and intent hash. `RECONCILE` may cross a run
boundary but must reference the original run, operation and intent hash. An
operation has at most one terminal closure; retries receive a new operation ID
and a `retry_of` edge. Missing, duplicate, cross-paired and causally forward
references are verification failures.

Reasons supplied by a worker are explicitly `actor_asserted`: their presence
is auditable, but the recorder does not certify their truth or completeness.
Policy-generated and externally observed statements use distinct source tags.
Raw prompts, chain-of-thought, command streams and tool payloads are outside
the event grammar. Every string and event has an explicit byte bound; target
classes and result fields are typed rather than arbitrary transcript slots.
Exhaustion is visible and freezes new effects instead of silently dropping
events.

A `REVIEW` binds `review_of` to exact artifact/head digests and the author run
and principal set derived from verified receipts rather than reviewer input.
Before it can satisfy D-030, a trusted
`INDEPENDENCE_CHECK` rejects the same principal, any resume/fork lineage of an
author run and identity reassignment through a changed role/profile. A disclosed
same-author review remains useful evidence but cannot satisfy the independence
lock unless the Architect records an explicit scoped waiver.

### Effects are two-phase

Every worker-requested file mutation, commit, push, issue/PR edit, comment,
approval, merge, secret/configuration change or other external effect requires
a durable intent event before execution and a durable result event containing
observed state after execution. The applicable trusted authorization check and
operation/intent references are mandatory on both.

If intent cannot be appended durably, the effect is refused. If the effect
occurs but its result cannot be recorded, all further write authority freezes.
The next run must reconcile the pending intent against external state before
any new mutation. A read-only failure may end as `RUN_ABORT`, but cannot be
reported as completed evidence.

The recorder's own append is control-plane transport rather than a recursively
logged worker action. The canonical event and its hash-chain position prove the
append. Two-phase recording exposes partial effects but does not make an
external operation transactional or prevent a race; the underlying exact-head,
idempotency and service-specific compare-and-swap gates remain necessary.

### Terminalization has an explicit notary boundary

The run records `ANCHOR_REQUEST`, then freezes and signs either a completed
receipt or an interrupted checkpoint. A completed receipt requires zero pending
operations. An aborted or crashed run may instead produce a signed checkpoint
that names its pending operation references and can never claim completion.

An independent notary inaccessible to workers verifies the frozen manifest,
chain and exact target, publishes or otherwise anchors the receipt/checkpoint,
and returns a detached signed notary attestation containing the external object
reference and observed publication state. That attestation is deliberately not
folded back into the root it anchors. The notary's request/result audit belongs
to its separate trusted control-plane log; this terminal trust boundary, like
the recorder append boundary, prevents infinite re-anchoring while still
recording the publication outcome. Verification requires both the recorder
signature and the independent notary attestation; an issuer signature alone is
not an anchor.

A later reconciliation run cites the interrupted checkpoint and original
intent, then produces its own receipt and notary attestation. The exact-head
GitHub publisher may implement the notary role only if it independently
rechecks the head and cannot access worker capabilities.

### Bootstrap has one honest beginning

The sessions deciding and implementing D-068 predate a trusted issuer and
therefore remain legacy runs identified only by their existing issue, commit
and CI receipts. They never receive retroactive `run_id` values: a
self-issued historical passport would be the first forged identity in the
system.

The first compliant run begins only after an exact commit installs the issuer,
verifier and independent notary boundary. That commit and the separately
protected issuer/notary verification-key fingerprints form the genesis
reference; signing secrets stay outside every worker. If bootstrap cannot
establish that separation, the system reports `PRE_IDENTITY` or `PARTIAL`
rather than claiming D-068 coverage.

### Privacy, retention and public receipts

Redaction precedes hashing, serialization, model access and upload. Secrets and
low-entropy credentials become typed tombstones; their original values and
guessable digests are absent.

The authoritative detailed ledger is content-minimized, encrypted under a
per-run data key, kept outside the subject repository and never made public.
Workers cannot read it directly. Access is limited to the owner and the trusted
verification/reconciliation services, and every access is independently
audited. Its default retention is 30 days after terminalization; lengthening
that period requires a new accepted decision rather than an implementation
toggle.

At expiry the per-run key is destroyed across active storage and replicas so
backups become unreadable, and the retention service emits a signed deletion
attestation. Before erasing a run with unresolved effects, the service replaces
the detailed record with a minimal signed pending-operation tombstone sufficient
to preserve the write freeze and later reconciliation without retaining
prompts, summaries or payloads. Tests must use a controlled clock and prove ACL
denial, hardline deletion, expiry, replica crypto-erasure and tombstone
preservation.

A `provider_session_ref` is never public or part of the semantic ledger. It is
not collected when resume is unneeded; otherwise only a restricted encrypted
mapping from `run_id` may retain it. A clean hardline deletes the mapping
immediately. An aborted run or unresolved pending effect may retain it for
reconciliation for at most seven days; after that, continuity comes from the
safe handoff and a new run.

The durable project record contains the safe public manifest, terminal
receipt/notary attestation, selected owner-approved handoff/decision summaries,
and existing issue/ADR/PR/commit/CI artifacts. Their digests bind the public
record without publishing the private event stream. Gospel and daily memory
receive only the human handoff, never provider handles or event data.

The compact frozen receipt binds:

```text
receipt_schema + verifier_version
manifest_digest + run_id + principal_id + role_id + task_ref
head_before + head_after + approved_handoff_digest
event_count + first_hash + final_root
coverage_final + coverage_gap_codes
model_observation_event_hash
pending_operation_count + pending_operation_refs
authority_event_count + active_grants=0
redactions_by_class + redaction_policy_digest + retention_policy_digest
status + start/end time + recorder_key_id + recorder_signature
```

`active_grants=0` and `pending_operation_count=0` are completion requirements,
not values an interrupted checkpoint may invent. The detached notary
attestation binds the exact receipt digest, notary key, anchor reference and
observed result.

### Consequences

* Good, because artifacts, decisions and effects gain reconstructable
  run/principal/role/authority lineage.
* Good, because resuming a model does not turn a provider handle into a
  long-lived public identity.
* Good, because a crash between intent and result becomes visible debt rather
  than an invented success.
* Good, because the ledger records meaning while refusing hidden-reasoning and
  raw-secret surveillance.
* Bad, because every effect-bearing adapter must cooperate with a trusted
  recorder and fail closed when it cannot.
* Bad, because private ledgers and resume mappings need encryption, access
  control, crypto-erasure and independently audited deletion.
* Bad, because capability brokerage, signatures, notary anchoring,
  reconciliation and verifier versioning add operational machinery.
* Neutral, because a semantic ledger is deliberately not a replay of every
  terminal byte; its claim is causal accountability, not forensic omniscience.

### Confirmation

The decision record itself is confirmed by document/index agreement, targeted
new-edge checks that add no back-edge debt, normal documentation probes, the
unchanged world digest and the accepted verdict in #1776. This does not call
the inherited decision web complete.

Realization is confirmed only when behavior fixtures show that the issuer's
atomic reservation survives concurrent launches and an injected random-value
collision; workers cannot replace their manifest, principal, role, profile,
authority, coverage or lineage; requested and provider-attested model values
remain distinguishable; and changing any manifest field invalidates the chain
genesis and receipt.

Fixtures must also show that expired, revoked, wrong-run and never-issued grants
are denied; every tool/effect intent has a trusted authorization decision;
identical concurrent operations cannot cross-pair results; duplicate or missing
closures fail; and cross-run reconciliation must cite the original run,
operation and intent hash. Reordered, deleted, duplicated or modified events
and wrong-head receipts must fail.

An unavailable recorder must prevent an effect. A crash after intent must leave
write-blocking pending debt, produce at most an honestly pending checkpoint and
require reconciliation before writes resume. Receipt anchoring must terminate
at the detached notary boundary rather than changing the root it anchors, and
neither a recorder signature without a notary attestation nor an attestation
for another receipt may verify.

Resume/fork must produce a new linked run. A role/profile relabel or author
lineage must fail the independent-review predicate. Secret-bearing values must
be tombstoned before serialization and not replaced by guessable hashes;
provider IDs must never reach public artifacts; identity must never satisfy
authorization; and a worker must not mint a valid issuer or notary statement.

The suite must prove that a bypassable passive hook cannot claim `COMPLETE`, a
write-capable `PARTIAL` run is refused, a later coverage gap freezes writes,
bounded-field or event-count exhaustion cannot silently omit an effect, and
pre-genesis work cannot be backfilled with a valid identity. Controlled-clock
tests must enforce provider-reference deletion, the detailed-ledger TTL, ACLs,
replica crypto-erasure and minimal pending tombstones.

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

* Good, because principal, role, profile, execution, model, lineage, authority
  and provider context remain separate.
* Good, because two-phase events make partial external effects reconcilable.
* Good, because the durable record is useful without containing raw secrets or
  provider handles.
* Bad, because trusted issuance, capability brokerage, notary anchoring and
  expiring private storage are real pets that must be fed.

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
issuer/recorder/verifier and capability broker; Codex/Claude adapters plus
confinement, private resume mapping and crash reconciliation; and exact-head
notary anchoring plus retention/redaction/crypto-erasure enforcement. This
record alone ships no recorder, provider integration, credential store,
surveillance path, unattended merge or company-repository authority.
