# Security Hardening Backlog

**Repository:** `TaktX-engine2`
**Created:** 2026-04-19
**Last updated:** 2026-04-20
**Purpose:** Track agreed security hardening work derived from `docs/security-questionnaire-response.md`. This backlog is intentionally aligned to the current TaktX architecture rather than the original external proposal.

---

## How to use this tracker

**Status values**

- `Not started`
- `In progress`
- `Blocked`
- `Done`
- `Deferred`

**Priority values**

- `P0` — production security gap / control-plane hardening
- `P1` — important hardening / exposure reduction
- `P2` — cleanup, follow-up, or longer-term hardening

**Estimate values**

- `S` — small
- `M` — medium
- `L` — large

---

## Terminology and architecture constraints

Before implementation, keep these current-code facts in mind:

1. **Trusted roles today are `CLIENT`, `ENGINE`, and `PLATFORM`**
   - Source: `taktx-shared/src/main/java/io/taktx/dto/KeyRole.java`
   - This backlog uses **CLIENT** instead of introducing a new `WORKER` role unless a deliberate role migration is later approved.

2. **Role must come only from trusted key metadata**
   - Do **not** add or trust `X-TaktX-Producer-Role`.
   - Role must be derived from `taktx-signing-keys` and the applicable trust policy.

3. **Workers/clients legitimately write to `process-instance`**
   - Current worker/user-task responders publish response DTOs back to `process-instance`.
   - Therefore enforcement must be based on **topic + message type + trusted role**, not topic-only rules.

4. **Current sensitive topics are not named `cmd.*`, `job.*`, `timer.*`**
   - Important current topics include:
     - `process-instance`
     - `schedule-commands`
     - `message-event`
     - `topic-meta-requested`
     - `taktx-signing-keys`
     - `external-task-trigger-*`
   - Source: `taktx-shared/src/main/java/io/taktx/Topics.java`

5. **Replay identity should be unified around existing `auditId` first**
   - Do **not** introduce a competing required `messageId` header in the initial hardening phase.
   - First make `auditId` mandatory where intended and durable across restarts/nodes.

6. **Do not over-unify too early**
   - Build a shared verification core.
   - Adopt it incrementally on the highest-risk topics first.

---

## Epic summary

| Epic | Title | Priority | Goal | Status |
|---|---|---:|---|---|
| A | Message-aware security policy model | P0 | Make authorization decisions based on topic + DTO type + trusted role | Not started |
| B | Shared verification core | P0 | Extract reusable trust/signature/key verification without destabilizing all flows at once | Not started |
| C | Secure control-plane topics | P0 | Close bypass paths on `schedule-commands`, `topic-meta-requested`, and sensitive `process-instance` message types | Done |
| D | Durable replay protection | P0 | Replace per-JVM replay checks with durable replay tracking using canonical `auditId` | In progress |
| E | Topic creation hardening | P0 | Prevent arbitrary dynamic topic creation and strictly validate requested topics | Done |
| F | Trust model hardening | P0 | Enforce anchored mode in production and ensure role derives only from trusted key metadata | Done |
| G | Client message-type restrictions | P0 | Prevent `CLIENT` keys from emitting engine/platform-only command types | Done |
| H | Observability and security telemetry | P1 | Make rejections, replay attempts, and signature failures visible in logs and metrics | Not started |
| I | REST endpoint security review | P1 | Classify and guard read APIs before production exposure | Not started |
| J | Documentation and threat-model cleanup | P2 | Remove docs/code drift and document required Kafka ACL assumptions | Not started |

---

## EPIC A — Message-aware security policy model

**Priority:** `P0`
**Goal:** Security decisions must consider **topic name + message type + trusted signer role**, not raw topic string or topic class alone.

### Task A1 — Create `MessageSecurityPolicyRegistry`

| Field | Value |
|---|---|
| Status | In progress |
| Priority | P0 |
| Estimate | M |
| Dependencies | None |

**Description**

Introduce a central registry mapping `(topicName, messageClass)` to a policy object such as:

```java
class MessageSecurityPolicy {
    Set<KeyRole> allowedRoles;
    boolean requireSignature;
    boolean requireReplay;
    boolean requireJwt;
    boolean allowEngineSignatureAsJwtEquivalent;
}
```

**Initial scope**

At minimum, define policies for:

- `process-instance` + `StartCommandDTO`
- `process-instance` + `AbortTriggerDTO`
- `process-instance` + `SetVariableTriggerDTO`
- `process-instance` + `ExternalTaskResponseTriggerDTO`
- `process-instance` + `UserTaskResponseTriggerDTO`
- `process-instance` + `ContinueFlowElementTriggerDTO`
- `process-instance` + `EventSignalTriggerDTO`
- `schedule-commands` + `MessageScheduleDTO`
- `topic-meta-requested` + `TopicMetaDTO`

**Acceptance criteria**

- Every protected `(topic, DTO)` combination resolves to a deterministic policy.
- Unknown `(topic, DTO)` combinations fail closed.
- Entry-command policies preserve current JWT behavior from `EngineAuthorizationService`.
- Registry behavior is unit tested.

**Notes**

- Do **not** reduce entry-command security to a role-only rule. JWT requirements remain part of the model.

### Task A2 — Enumerate current DTO/message taxonomy used on sensitive topics

| Field | Value |
|---|---|
| Status | Not started |
| Priority | P0 |
| Estimate | S |
| Dependencies | A1 |

**Description**

Document the current DTO classes and type IDs already used in polymorphic trigger deserialization.

**Acceptance criteria**

- Current message taxonomy is captured from `ProcessInstanceTriggerTypeIdResolver`.
- No task uses placeholder DTO names that do not exist in the codebase.
- Policy registry references only real current classes.

---

## EPIC B — Shared verification core

**Priority:** `P0`
**Goal:** Extract reusable verification logic without forcing a risky all-at-once refactor.

### Task B1 — Create `VerificationCore`

| Field | Value |
|---|---|
| Status | Not started |
| Priority | P0 |
| Estimate | M |
| Dependencies | A1 |

**Description**

Introduce a shared core responsible for:

- extracting signature header / key ID
- resolving the signing key from trusted state
- validating signature material
- applying the active trust policy
- deriving `KeyRole` from trusted key metadata only

Proposed output:

```java
class VerifiedMessageContext {
    SigningKeyDTO key;
    KeyRole role;
    boolean signatureValid;
    String keyId;
}
```

**Acceptance criteria**

- Role is derived exclusively from trusted key metadata.
- No producer-supplied role header is introduced.
- Clear error reasons are returned for logging/counters.
- Unit tests cover: unknown key, revoked key, bad signature, trusted key, role derivation.

### Task B2 — Define gradual-adoption seam between existing auth and new verification core

| Field | Value |
|---|---|
| Status | Not started |
| Priority | P0 |
| Estimate | S |
| Dependencies | B1 |

**Description**

Create integration points so existing logic in `EngineAuthorizationService` and deserializers can call the shared core without requiring a full rewrite.

**Acceptance criteria**

- Existing `process-instance` behavior can be preserved while internally using shared verification functions.
- Adoption plan identifies highest-risk next consumers: `schedule-commands`, `topic-meta-requested`.

---

## EPIC C — Secure control-plane topics

**Priority:** `P0`
**Goal:** Close the current bypass paths identified in the questionnaire response.

### Task C1 — Secure `schedule-commands`

| Field | Value |
|---|---|
| Status | Done |
| Priority | P0 |
| Estimate | M |
| Dependencies | A1, B1 |

**Description**

Require verification for inbound `schedule-commands` records.

**Rules**

- signature required
- trusted role must resolve to `ENGINE`
- unauthorized/unsigned schedule messages rejected before processing

**Acceptance criteria**

- Unsigned schedule messages are rejected.
- `CLIENT`-signed schedule messages are rejected.
- Rejections are logged with explicit reason.
- Existing valid engine-generated schedules continue to work.

**Implementation notes (2026-04-20)**

- `schedule-commands` producers in `TopologyProducer` now use a signed value serde, so engine-generated schedule create/cancel records carry `X-TaktX-Signature` headers.
- `MessageSigningService` now signs engine-emitted records whenever an engine signing identity is available and the public key has been published, even when runtime `signingEnabled` / `engineRequiresAuthorization` toggles are both off; this keeps internal `schedule-commands` compatible with strict consumers in default integration-test mode as well as hardened modes.
- `ScheduleCommandDeserializer` now enforces cryptographic signature verification on inbound `schedule-commands` records, including tombstones signed over an empty payload.
- `EngineAuthorizationService.authorizeScheduleCommand(...)` now derives signer trust from `taktx-signing-keys` + `KeyTrustPolicy` and enforces trusted `ENGINE` role before schedule handling.
- `ScheduleProcessor` now rejects unauthorized schedule records before bucket processing and logs topic, schedule key, signer key ID, derived role, outcome, reason, and message type.
- Relevant schedule integration coverage is green again, including `ProcessInstanceProcessorTest.testScheduledStart_R5` and `testScheduledStart_R60`.

### Task C2 — Secure `topic-meta-requested`

| Field | Value |
|---|---|
| Status | Done |
| Priority | P0 |
| Estimate | M |
| Dependencies | A1, B1, E1 |

**Description**

Apply verification before any topic-creation request is processed.

**Acceptance criteria**

- Invalid or unsigned requests are rejected before topic creation.
- Trusted role is evaluated before request handling.
- Logs include reason, topic, signer key ID, and outcome.

**Implementation notes (2026-04-20)**

- Strict structural validation now runs before topic creation handling in `DynamicTopicManager.processRequestedTopic(...)`.
- `topic-meta-requested` consumers now require `X-TaktX-Signature` and reject unsigned request records before handling.
- `EngineAuthorizationService.authorizeTopicMetaRequest(...)` now derives signer trust from `taktx-signing-keys` + `KeyTrustPolicy` and enforces trusted `CLIENT`-or-higher role before requests are accepted.
- Accepted/rejected topic requests are now logged with topic, signer key ID, derived role, outcome, and rejection reason.
- `BpmnTestEngine` now publishes signed `topic-meta-requested` records using an isolated test `CLIENT` key and waits until the engine signing-key resolver can read that key before requesting topics, so default integration tests exercise the hardened contract instead of relying on unsigned control-plane traffic.
- This path still reuses existing signing/trust plumbing rather than a dedicated shared `VerificationCore` from Epic B, but the task acceptance criteria are now met and verified by passing integration tests.

### Task C3 — Add message-type enforcement on `process-instance`

| Field | Value |
|---|---|
| Status | Done |
| Priority | P0 |
| Estimate | M |
| Dependencies | A1, B2, G1 |

**Description**

Extend `process-instance` authorization so role restrictions apply per DTO type.

**Examples**

- `CLIENT` may emit `ExternalTaskResponseTriggerDTO` and `UserTaskResponseTriggerDTO`
- `CLIENT` may not emit `StartCommandDTO`, `AbortTriggerDTO`, or `SetVariableTriggerDTO`
- engine-internal continuation/event messages remain restricted to `ENGINE` logic/policy

**Acceptance criteria**

- Message type is considered alongside topic and role.
- Disallowed `(role, DTO)` combinations are rejected before processing.
- Existing engine-internal flows continue to function.

**Implementation notes (2026-04-20)**

- `EngineAuthorizationService.authorize(...)` now evaluates signed `process-instance` commands by DTO subtype, not just entry-vs-non-entry.
- `ExternalTaskResponseTriggerDTO` and `UserTaskResponseTriggerDTO` now require trusted `CLIENT`-or-higher Ed25519 signatures when `engineRequiresAuthorization` or `signingEnabled` is active.
- Engine-internal `ContinueFlowElementTriggerDTO`, `StartFlowElementTriggerDTO`, and `EventSignalTriggerDTO` now require trusted `ENGINE`-or-higher signatures; `CLIENT` keys are rejected fail-closed.
- Unsigned non-entry `process-instance` commands are now rejected whenever either process-instance security gate is active, and `MessageSigningService` signs engine-emitted records once an engine signing identity is available and published so internal continuations remain compatible across default, auth-only, and signing-enabled modes.
- `ProcessInstanceProcessor` now ignores payload-supplied trust metadata by default and only preserves forwarded `originTrustMetadata` when the inbound command itself was verified as engine-signed.

---

## EPIC D — Durable replay protection

**Priority:** `P0`
**Goal:** Prevent replay across restarts and node changes, starting with entry commands.

### Task D1 — Standardize `auditId` as canonical replay identity

| Field | Value |
|---|---|
| Status | Done |
| Priority | P0 |
| Estimate | S |
| Dependencies | None |

**Description**

Define replay semantics around existing `auditId` rather than adding a competing message ID immediately.

**Decisions to document**

- uniqueness scope (`tenant/namespace` and/or issuer/signer scope)
- retention window
- duplicate handling
- whether replay is global or topic-specific

**Acceptance criteria**

- Canonical replay identity is documented.
- Entry-command paths use one replay identity only.
- Blank/null `auditId` handling is explicit and tested.

### Task D2 — Implement durable replay store

| Field | Value |
|---|---|
| Status | Not started |
| Priority | P0 |
| Estimate | L |
| Dependencies | D1 |

**Description**

Replace or augment `NonceStore` with a durable replay store backed by Kafka Streams state.

**Suggested key shape**

Prefer scoped keying such as:

- `<tenant>:<namespace>:<auditId>`
- or `<issuer>:<auditId>`

rather than raw `auditId` alone.

**Acceptance criteria**

- Replay state survives restart.
- Replay state works correctly across reassignment/failover.
- Performance is acceptable for hot command paths.
- Unit/integration tests cover restart and duplicate scenarios.

**Implementation notes (2026-04-20)**

- Added runtime-configurable replay controls to `GlobalConfigurationDTO`: `replayProtectionMode` (`OFF`, `COMPAT`, `STRICT`) and `replayProtectionRetentionMs`.
- Added canonical replay key construction in `EngineAuthorizationService` using scoped identity `<tenant>:<namespace>:<issuer>:<auditId>`.
- Added durable replay state store `replay-protection` to the Kafka Streams topology and a new `ReplayProtectionProcessor` ahead of `ProcessInstanceProcessor`.
- JWT-bearing entry commands are now re-keyed by replay-routing hint before replay evaluation so duplicate `auditId` checks are not tied to process-instance UUID partitioning.
- `ProcessInstanceTriggerEnvelope` now carries replay-routing hints and replay-prevalidated JWT claims through the topology.
- `EngineAuthorizationService` now treats JWT validation as reusable claim validation and keeps `NonceStore` only as a fallback for direct/unit-call paths that do not pass through the durable replay topology.
- Remaining gap for full completion: explicit restart/failover restoration coverage is still pending in tests/docs.

### Task D3 — Enforce durable replay on entry commands first

| Field | Value |
|---|---|
| Status | In progress |
| Priority | P0 |
| Estimate | M |
| Dependencies | D2, A1 |

**Description**

Apply durable replay protection to:

- `StartCommandDTO`
- `AbortTriggerDTO`
- `SetVariableTriggerDTO`

**Acceptance criteria**

- Duplicate entry commands are rejected after restart as well as within a single JVM lifetime.
- Blank/noncompliant replay identity is rejected if policy requires it.
- Existing JWT-based auth semantics are preserved.

**Implementation notes (2026-04-20)**

- `OFF`, `COMPAT`, and `STRICT` runtime semantics are now implemented for JWT-bearing entry commands.
- `COMPAT` rejects duplicate non-blank `auditId` values while still allowing blank/missing `auditId`.
- `STRICT` rejects blank/missing `auditId` and duplicate `auditId` values.
- Existing ENGINE-signed entry-command behavior remains separate from JWT replay enforcement to preserve current engine-internal authorization semantics.
- Focused unit/integration coverage is now green for duplicate JWT rejection, retention expiry, strict blank-`auditId` rejection, and the existing `SecurityIntegrationTest.replayedAuditId_secondCommandRejected` path.

### Task D4 — Evaluate replay extension to timer/control topics

| Field | Value |
|---|---|
| Status | Not started |
| Priority | P1 |
| Estimate | M |
| Dependencies | C1, D2 |

**Description**

After entry commands are stable, extend replay protection to schedule/control-plane messages where it adds security value.

**Acceptance criteria**

- Decision recorded for each sensitive topic: required / not required / deferred.
- Replay-sensitive topics use the durable service, not ad hoc local caches.

---

## EPIC E — Topic creation hardening

**Priority:** `P0`
**Goal:** Prevent arbitrary topic creation while keeping current dynamic worker-topic behavior.

### Task E1 — Implement `RequestedTopicValidator`

| Field | Value |
|---|---|
| Status | Done |
| Priority | P0 |
| Estimate | M |
| Dependencies | None |

**Description**

Validate all inbound `topic-meta-requested` messages.

**Rules**

- must start with local `<tenant>.<namespace>.`
- must match current allowed dynamic topic pattern:
  - `<tenant>.<namespace>.external-task-trigger-*`
- reject all other topic classes and free-form names

**Acceptance criteria**

- arbitrary topic names are rejected
- non-local prefix requests are rejected
- only valid external-task trigger topics are accepted
- all rejections are logged with reason

**Implementation notes (2026-04-19)**

- Implemented in `taktx-engine/src/main/java/io/taktx/engine/topicmanagement/RequestedTopicValidator.java`.
- Validation now rejects non-local prefixes, fixed-topic/free-form requests, blank `external-task-trigger-` suffixes, and record-key / `TopicMetaDTO.topicName` mismatches.

### Task E2 — Harden `DynamicTopicManager` integration

| Field | Value |
|---|---|
| Status | Done |
| Priority | P0 |
| Estimate | M |
| Dependencies | E1, C2 |

**Description**

Integrate strict request validation into topic creation flow.

**Acceptance criteria**

- engine cannot create invalid requested topics
- invalid requests do not trigger fallback sanitization
- logs show accepted/rejected topic requests with key ID / role / topic / result

**Implementation notes (2026-04-20)**

- `DynamicTopicManager` now validates requested topics before caching or broker creation.
- `TopicBootstrapper` no longer routes managed fixed topics back through `topic-meta-requested`; managed topics are seeded directly via `registerManagedTopic(...)`.
- Dynamic topic creation now uses a create-first, `TopicExistsException`-tolerant path so concurrent engine starts/races are treated idempotently.
- When a requested topic already exists, the manager now resolves broker metadata before publishing to `topic-meta-actual`, instead of echoing the requested DTO as actual state.
- Topic request logging/verification details now live primarily under `C2`; broader verification-core reuse remains future work under Epic B, but E2's validation/integration acceptance criteria are now met.

### Task E3 — Add focused tests for dynamic topic hardening

| Field | Value |
|---|---|
| Status | Done |
| Priority | P0 |
| Estimate | S |
| Dependencies | E2 |

**Acceptance criteria**

- tests cover valid worker topic request
- tests cover wrong tenant/namespace
- tests cover attempt to create `taktx-signing-keys`, `schedule-commands`, `process-instance`, or other fixed topics via request path

**Implementation notes (2026-04-19)**

- Added focused coverage in `RequestedTopicValidatorTest` and `DynamicTopicManagerTest`.
- Tests cover accepted worker-topic requests, wrong-tenant/non-local rejections, fixed-topic rejection, key/payload mismatch rejection, concurrent create races, and broker-create failure handling.

---

## EPIC F — Trust model hardening

**Priority:** `P0`
**Goal:** Remove insecure production trust posture and tighten key trust expectations.

### Task F1 — Enforce anchored mode in production mode

| Field | Value |
|---|---|
| Status | Done |
| Priority | P0 |
| Estimate | S |
| Dependencies | None |

**Description**

Add a production-mode switch, e.g. `taktx.security.production-mode=true`, that fails startup if anchored trust requirements are not met.

**Startup must fail if**

- production mode is enabled and no root platform public key is configured
- production mode is enabled but open/community trust remains effectively active

**Acceptance criteria**

- engine cannot start in insecure community/open mode when production mode is enabled
- failure mode is explicit and documented

**Implementation notes (2026-04-19)**

- Added `taktx.security.production-mode` / `TAKTX_SECURITY_PRODUCTION_MODE`; default remains `false` for local/community use.
- `KeyTrustPolicyProducer` now fails startup when production mode is enabled but `TAKTX_PLATFORM_PUBLIC_KEY` is missing, instead of silently selecting `OpenKeyTrustPolicy`.
- `EngineSigningIdentitySourceProducer` now fails startup in production mode when the engine still uses `generated` signing identity or lacks `TAKTX_ENGINE_KEY_REGISTRATION_SIGNATURE`.
- `docs/security.md` now documents the fail-closed startup requirements for anchored production deployments.

### Task F2 — Ensure role derivation comes only from trusted key metadata

| Field | Value |
|---|---|
| Status | Done |
| Priority | P0 |
| Estimate | S |
| Dependencies | B1 |

**Acceptance criteria**

- role is never accepted from payload/header/application claim shortcuts
- all role checks use trusted key metadata after trust validation
- tests cover mismatched/untrusted key scenarios

**Implementation notes (2026-04-19)**

- `EngineAuthorizationService` already derived Ed25519 signer authority from trusted `taktx-signing-keys` metadata plus `KeyTrustPolicy`, not from headers or payload fields.
- `PublicKeyProvider.getKey(...)` now applies the same trust model to JWT `kid` resolution: the key must resolve from `taktx-signing-keys`, remain non-revoked, be trusted for `PLATFORM`, and use RSA before JWT verification proceeds.
- JWT issuer trust is therefore now rooted in trusted key metadata as well; a random published `CLIENT` key or mismatched algorithm can no longer satisfy the JWT key lookup path.

### Task F3 — Clarify and enforce signing-key topic expectations

| Field | Value |
|---|---|
| Status | Done |
| Priority | P0 |
| Estimate | M |
| Dependencies | F1 |

**Description**

Replace the earlier “TRUST_ADMIN-only writes” concept with a model that fits current TaktX:

- all keys must be root-countersigned in anchored mode
- Kafka ACLs still restrict who may write `taktx-signing-keys`
- community mode is explicitly documented as insecure for production use

**Acceptance criteria**

- production guidance clearly states anchored mode requirement
- root-countersignature expectations are enforced/documented consistently
- community mode is clearly flagged as development-only / insecure for production

**Implementation notes (2026-04-19)**

- `KeyTrustPolicyProducer` startup logs now explicitly describe community mode as Kafka-ACL-dependent and unsuitable for production, while anchored mode logs call out both root countersignature requirements and the continuing need to restrict `taktx-signing-keys` writers.
- `MessageSigningService` and `TaktXClient.publishSigningKey(...)` now log whether published signing keys are countersigned (`anchored-ready`) or unsigned (`community-only`), so operators can immediately spot when a key will be rejected by anchored engines.
- `docs/security.md`, `README.md`, `SECURITY.md`, and `docker/signing/README.md` now consistently describe the current model: anchored mode for production, Kafka ACLs still required for `taktx-signing-keys`, and community mode as development/local-use only.

---

## EPIC G — Client message-type restrictions

**Priority:** `P0`
**Goal:** Prevent `CLIENT` keys from escalating into engine/platform-only command types.

### Task G1 — Enforce `CLIENT` message-type restrictions on `process-instance`

| Field | Value |
|---|---|
| Status | Done |
| Priority | P0 |
| Estimate | M |
| Dependencies | A1, C3 |

**Description**

Restrict what `CLIENT`-trusted messages may emit to `process-instance`.

**Initial allowlist to evaluate/implement**

- `ExternalTaskResponseTriggerDTO`
- `UserTaskResponseTriggerDTO`

**Initial denylist**

- `StartCommandDTO`
- `AbortTriggerDTO`
- `SetVariableTriggerDTO`
- engine continuation/internal-only messages unless explicitly policy-approved

**Acceptance criteria**

- `CLIENT` cannot emit engine/platform-only process-instance messages
- valid worker/user-task response flows still work
- role/message mismatches are rejected and logged

**Implementation notes (2026-04-19)**

- The initial allowlist is now enforced in `EngineAuthorizationService`: `CLIENT` keys may emit worker/user-task response DTOs, but not engine-only continuation/start/event commands.
- Direct `CLIENT` attempts to publish engine-only `process-instance` DTOs are rejected before processing with explicit role/message mismatch errors.
- Worker-signed payloads can no longer smuggle forged `currentTrustMetadata` / `originTrustMetadata`; downstream updates retain the verified worker signer unless a later engine-signed internal hop legitimately carries origin forward.

### Task G2 — Evaluate future per-client/per-worker scoping metadata

| Field | Value |
|---|---|
| Status | Deferred |
| Priority | P1 |
| Estimate | M |
| Dependencies | G1 |

**Description**

Optional later hardening:

- bind client keys to allowed topic patterns and/or worker categories
- further reduce lateral movement after key compromise

**Acceptance criteria**

- design decision recorded: implement / reject / defer
- if implemented, restrictions are rooted in trusted key metadata, not unsafely supplied headers

---

## EPIC H — Observability and security telemetry

**Priority:** `P1`
**Goal:** Make security-relevant failures visible and measurable.

### Task H1 — Add structured security logs

| Field | Value |
|---|---|
| Status | Not started |
| Priority | P1 |
| Estimate | S |
| Dependencies | B1, C1, C2, C3 |

**Log events**

- rejected messages
- invalid signatures
- replay attempts
- topic creation accepted/rejected
- key-trust failures

**Acceptance criteria**

- logs are consistent across protected topics
- logs include enough context for incident response without leaking secrets

### Task H2 — Add rejection/security counters

| Field | Value |
|---|---|
| Status | Not started |
| Priority | P1 |
| Estimate | S |
| Dependencies | H1 |

**Suggested metrics**

- `taktx.security.rejected.messages`
- `taktx.security.invalid.signatures`
- `taktx.security.replay.attempts`
- `taktx.security.topic.requests.rejected`

**Acceptance criteria**

- counters are exported through existing Micrometer setup
- metrics are incremented from real rejection paths, not only logs

---

## EPIC I — REST endpoint security review

**Priority:** `P1`
**Goal:** Prevent accidental production exposure of read/query endpoints.

### Task I1 — Audit REST endpoint exposure

| Field | Value |
|---|---|
| Status | Not started |
| Priority | P1 |
| Estimate | S |
| Dependencies | None |

**Actions**

Classify all REST endpoints as:

- public
- internal
- secured
- disabled in production

**Acceptance criteria**

- endpoint inventory exists in docs
- each endpoint has an intended exposure classification

### Task I2 — Add explicit auth guards or production exposure controls

| Field | Value |
|---|---|
| Status | Not started |
| Priority | P1 |
| Estimate | S |
| Dependencies | I1 |

**Acceptance criteria**

- no unintentionally unsecured endpoints remain in production mode
- either application-level guards or documented gateway restrictions are in place

---

## EPIC J — Documentation and threat-model cleanup

**Priority:** `P2`
**Goal:** Remove ambiguity between docs and code, and document deployment assumptions clearly.

### Task J1 — Fix docs/code drift

| Field | Value |
|---|---|
| Status | Not started |
| Priority | P2 |
| Estimate | S |
| Dependencies | D1, F1 |

**Examples to reconcile**

- `auditId` requirements vs current replay behavior
- `GlobalConfigurationDTO` contents vs `docs/security.md`
- production/community/anchored mode language

### Task J2 — Publish explicit security threat model

| Field | Value |
|---|---|
| Status | Not started |
| Priority | P2 |
| Estimate | M |
| Dependencies | A1, F3 |

**Threat model should document**

- what is enforced in engine code
- what still depends on Kafka ACLs
- what anchored mode guarantees
- what community mode does **not** guarantee
- which topics are security-critical

---

## Recommended rollout order

1. **Epic E** — Topic creation hardening
2. **Epic A** — Message-aware policy model
3. **Epic B** — Shared verification core
4. **Epic C** — Secure control-plane topics
5. **Epic D** — Durable replay protection
6. **Epic F** — Trust model hardening
7. **Epic G** — Client message-type restrictions
8. **Epic H** — Observability and metrics
9. **Epic I** — REST endpoint review
10. **Epic J** — Documentation / threat model cleanup

---

## System-level acceptance targets

The system is materially improved when the following are true:

- arbitrary topic creation via `topic-meta-requested` is impossible
- `schedule-commands` rejects unsigned or non-`ENGINE` messages
- `process-instance` authorization considers topic + DTO type + trusted role
- `CLIENT` keys cannot emit entry commands or engine-internal-only message types
- replay protection for entry commands survives restart/failover
- production deployments cannot silently run in insecure community mode
- key role comes only from trusted key metadata
- rejection/security events are visible in logs and metrics

---

## Related documents

- `docs/security-questionnaire-response.md`
- `docs/security.md`
- `SECURITY.md`
