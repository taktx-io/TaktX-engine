# TaktX — Architecture: RBAC, Signing, Validation & Licensing

> **Living document** — update this file when new insights are reached. Do not create new per-topic documents.

**Last updated:** May 21, 2026  
**Status:** Beta release — all planned phases complete. Remaining gaps documented in §9.

**Companion docs:**
- `docs/TASK-COMPLETION-AUTH-IMPLEMENTATION-PLAN.md`
- `docs/TASK-COMPLETION-AUTH-ENGINE-DESIGN.md`

---

## Table of Contents

1. [System Overview](#1-system-overview)
2. [RBAC — Permission Model](#2-rbac--permission-model)
3. [RBAC — Implementation Status](#3-rbac--implementation-status)
4. [Message Signing & Validation](#4-message-signing--validation)
5. [Signing & Validation — Implementation Status](#5-signing--validation--implementation-status)
6. [License Schema](#6-license-schema)
7. [License Distribution](#7-license-distribution)
8. [License — Implementation Status](#8-license--implementation-status)
9. [Open Items & Gaps](#9-open-items--gaps)
10. [Future Roadmap](#10-future-roadmap)

---

## 1. System Overview

The full chain of trust flows from an authenticated caller to the engine:

```
Caller (browser or service)
  │  OIDC JWT
  ▼
Platform Service (BFF)
  │  Validates OIDC JWT
  │  Checks RBAC process permissions
  │  Mints RS256 authorization token (5-min TTL)
  ▼
Ingester (HTTP proxy + Kafka bridge)
  │  Validates RS256 authorization token
  │  Forwards token as X-TaktX-Authorization Kafka header via TaktXClient
  ▼
Engine (Kafka Streams)
  │  Validates RS256 token (optional, feature-flagged)
  │  Checks auditId nonce (replay protection)
  │  Executes command
  │
  └─ emits instance-update with X-TaktX-Signature header (Ed25519, optional)
       ▼
    Ingester
      Verifies Ed25519 signature against engine's published public key
```

### Component responsibilities

| Component | Auth role |
|---|---|
| **Platform Service** | Identity validation (OIDC bearer token), RBAC enforcement, RS256 token minting, RSA key management, license management |
| **Ingester** | RS256 token validation, TaktXClient call site (forwards token), Ed25519 signature verification, Kafka bridge |
| **TaktXClient** | Carries signed token as Kafka header — no validation, no RBAC logic |
| **Engine** | Optional RS256 token validation (final trust boundary), command execution, Ed25519 event signing |

---

## 2. RBAC — Permission Model

### 2.1 Action hierarchy

The current security model keeps permission scope at **process-definition + version** level, while
allowing multiple operational actions on that scope.

**Existing chain (implemented today):**

```
MODIFY ⊃ CANCEL ⊃ START ⊃ VIEW
```

**Planned additions (post-beta, same scope model):**

```
USER_TASK_COMPLETE ⊃ VIEW
EXTERNAL_TASK_COMPLETE ⊃ VIEW
```

These two completion permissions are intentionally **separate** from `START` and `CANCEL`. They do
not imply each other, and they are not currently planned to be implied by `MODIFY`.

`MODIFY` remains reserved for future variable mutation semantics; a later naming cleanup may rename
it to better reflect that purpose, but that is outside the scope of the task-completion work.

### 2.2 Permission rule structure

```
Role → Permission Rule → { ScopeGroup, TargetGroup, Actions }

ScopeGroup: named set of { namespace } entries (wildcard = all namespaces)
TargetGroup: named set of { processDefinitionId, versionPattern } entries
```

**Version pattern syntax** (implemented in `VersionPatternMatcher.java`):

| Pattern | Meaning |
|---|---|
| `*` | All versions |
| `5` | Exact version 5 |
| `5+` | Version 5 and above |
| `3-7` | Versions 3 through 7 inclusive |

### 2.3 IdP role mapping

`role_name` is always the literal string from the JWT claim. No IdP-specific IDs are stored. Switching identity providers requires no permission data migration.

Claim path is configurable via `QUARKUS_OIDC_ROLES_ROLE_CLAIM_PATH` (backend) and `OIDC_ROLES_CLAIM_PATH` (frontend).

OIDC principal handling is intentionally **provider-agnostic**:
- Human users and service accounts are both treated as authenticated OIDC bearer-token principals
- Permission resolution is driven by roles/claims exposed through `SecurityIdentity`, not by a
  human-only identity model
- Keycloak remains the reference IdP during development, but no design assumption should depend on
  Keycloak-specific subject formats beyond configurable claim mapping

### 2.4 Authorization token (RS256 JWT)

The Platform Service mints a short-lived JWT for every write operation. This token travels from Platform Service → Ingester (HTTP header) → TaktXClient → Engine (Kafka header).

**JWT claims:**

| Claim | Type | Description |
|---|---|---|
| `sub` | string | OIDC subject of the authenticated caller (human user or service account) |
| `iss` | string | Always `taktx-platform-service` |
| `iat` | epoch seconds | Issued-at |
| `exp` | epoch seconds | Always `iat + 300` (5 minutes) |
| `action` | string | `START`, `CANCEL`, `VIEW`, `MODIFY`, `USER_TASK_COMPLETE`, `EXTERNAL_TASK_COMPLETE` |
| `namespaceId` | UUID | Namespace the action is scoped to |
| `processDefinitionId` | string | Exact definition ID, or `*` for read tokens |
| `version` | integer | Concrete version (≥1), or `-1` for read tokens |
| `auditId` | UUID | Unique per token — carried in all execution events |

**Key rules:**
- "Latest" (`-1`) is **never** in a write token — the BFF resolves the concrete latest version before signing
- CANCEL tokens bind to the resolved `processDefinitionId + version` of the instance — no wildcards
- USER_TASK_COMPLETE and EXTERNAL_TASK_COMPLETE tokens follow the same rule: they bind to the
  resolved owning `processDefinitionId + version` of the active task, not to a wildcard
- Initial task-completion scope remains aligned with the existing model: **definition + version**,
  not individual flow-node binding
- Read tokens use `processDefinitionId = *` and `version = -1`

### 2.5 Scope boundaries (what TaktX does NOT do)

- No deny rules — ALLOW-only model; scope narrowing via namespace/version patterns is the restriction mechanism
- No task-instance or assignee-level permissions — task completion actions remain scoped at process-definition + version level
- No prefix wildcards (`order*`) — only literal IDs and `*`
- MODIFY action is defined in the data model and hierarchy as a forward declaration — no enforcement code until variable mutation is built

### 2.6 Comparison with competitors

| Feature | Camunda 7 | Camunda 8 (8.6+) | Flowable | **TaktX** |
|---|---|---|---|---|
| Per-process-definition permission | ✅ | ✅ | Partial | ✅ |
| Version dimension | ❌ | ❌ | ❌ | ✅ version patterns |
| Permission hierarchy | ❌ | ❌ | ❌ | ✅ MODIFY⊃CANCEL⊃START⊃VIEW |
| Reusable scope groups | ❌ | ❌ | ❌ | ✅ |
| Reusable target groups | ❌ | ❌ | ❌ | ✅ |
| Engine-level signed command | ❌ DB auth | ❌ OAuth2 only | ❌ | ✅ (see §4) |
| Deny rules | ✅ | Partial | ❌ | ❌ by design |
| Task-level permissions | ✅ | ✅ | Partial | ❌ out of scope |

**Unique to TaktX:** Version patterns, reusable scope/target groups, engine-level signed command propagation.

---

## 3. RBAC — Implementation Status

### ✅ Complete

| Area | What is done |
|---|---|
| **Platform Service** | All HTTP endpoints enforce permissions; CANCEL wildcard fixed (resolves concrete `processDefinitionId + version`); START latest (-1) resolved to concrete version; VIEW enforcement on all read proxies; filter-based batch cancel with `canPerformFilterBatchCancel`; `GET /api/users/me/process-permissions` endpoint; namespace dropdown scoped to user's process permissions; `GET /api/namespaces` returns permission-scoped list with `PublicNamespaceDTO` (no `ingesterUrl` leak); `POST /api/authorization/check` roles taken from `SecurityIdentity` only (role-enumeration closed) |
| **Frontend** | `useProcessPermissions(namespaceId)` hook with full version pattern matching; definition + version dropdowns VIEW-filtered; Start modal resolved to permitted versions; per-row Cancel button gated by `canCancelInstance(defId, version)`; filter-based batch cancel gated by `canFilterBatchCancel`; namespace dropdown shows permitted namespaces only |
| **Ingester** | All GET endpoints and `POST /verify` require read token (`X-TaktX-Authorization`); single cancel, batch cancel, cancel-by-filter, count-by-filter all require CANCEL token; `AuthorizationTokenValidator` validates on every endpoint |
| **Zero Trust reads** | BFF generates namespace-scoped read token (`action=VIEW, processDefinitionId=*, version=-1`) and passes to every proxied GET call; ingester validates |
| **Token forwarding (C1, C2)** | `authToken` passed to `taktClient.startProcess()` and `taktClient.abortElementInstance()` — zero-trust chain complete end-to-end |
| **WebSocket auth (C5)** | BFF issues short-lived read token via `GET /api/runway/ws-token`; frontend fetches token before WS open; `ProcessEventWebSocket.onOpen()` validates token, closes with 1008 on failure |

### ⚠️ Partially complete

| Area | Gap |
|---|---|
| **MODIFY** | Defined in data model and hierarchy; no enforcement or UI until variable mutation feature ships |
| **Task completion authorization** | Platform Service now exposes BFF completion endpoints for user tasks and external tasks, resolves ownership for a selected flow-node instance via ingester lookup, enforces `USER_TASK_COMPLETE` / `EXTERNAL_TASK_COMPLETE`, and mints completion JWTs. The current ingester branch still uses a temporary custom Kafka publisher for task-completion triggers; the intended next step is engine-team delivery plus a fresh generated client release, after which the custom publisher should be replaced by supported client calls. Keep this work on a dedicated branch until matching engine support exists. See companion docs. |
| **`processInstanceId` binding on CANCEL tokens** | CANCEL tokens bind to `processDefinitionId + version` but not to a specific `processInstanceId`. A captured CANCEL token could be used against a different instance within its 5-minute TTL. Nonce check prevents exact replay but not cross-instance use. See §9.1 |

---

## 4. Message Signing & Validation

### 4.1 Why both RS256 (command) and Ed25519 (event) signing?

The two mechanisms secure opposite directions of the message bus:

| Direction | Mechanism | What it proves |
|---|---|---|
| Platform Service → Engine (commands) | **RS256 JWT** in `X-TaktX-Authorization` header | A real caller's permission was checked by the Platform Service at issuance time |
| Engine → Ingester (events) | **Ed25519 signature** in `X-TaktX-Signature` header | The event came from a known engine instance and was not tampered with |

**RS256 for commands** because the Platform Service already has an RSA key pair for OIDC integration, RS256 is the standard for authorization JWTs, and the JWT carries structured claims (action, namespace, processDefinitionId, version, auditId) that the engine can verify against the command payload. This same command model is the planned extension path for user-task and external-task completion authorization.

**Ed25519 for events** because Ed25519 is extremely fast (~50,000 ops/sec per core), uses compact 64-byte signatures, and is the right choice for high-volume event signing where no structured claims are needed — just proof of origin.

### 4.2 RS256 command authorization — public key distribution

The Platform Service has no Kafka connection. Key distribution uses a push model:

```
Platform Service
  └─ exposes GET /api/public-key (Base64 DER, unauthenticated)
       │
       │  Platform Service pushes key to each namespace's ingester at startup
       │  via POST /internal/signing-keys/platform
       ▼
Ingester (SigningKeyResource)
  ├─ Updates in-memory AuthorizationTokenValidator immediately
  └─ Calls TaktXClient.publishSigningKey() →
       publishes to <namespace>.taktx-signing-keys (compacted topic)
            │
            ▼
       Engine
         Reads taktx-signing-keys KTable at startup
         Uses key to validate X-TaktX-Authorization headers
```

**Root of trust (updated March 2026):** The active authorization path resolves JWT public keys
from `taktx-signing-keys` by JWT `kid`. `TAKTX_PLATFORM_PUBLIC_KEY` has been removed from
engine configuration — it is no longer used as a runtime fallback.

**Key rotation best practice:**
1. Publish the new RSA public key to `taktx-signing-keys` (via `PlatformKeyPublisher`).
2. Wait for propagation across all engine KTable instances (~one poll cycle).
3. Begin issuing JWTs signed with the new key (new `kid` in header).
4. Revoke/remove the old key entry from `taktx-signing-keys`.

Both old and new keys are accepted simultaneously while both entries are present in the topic,
so there is no hard cutover and no token rejection during the rotation window.

### 4.3 Ed25519 event signing — key distribution

Each engine instance generates an Ed25519 key pair at startup. The public key is published to `<namespace>.taktx-signing-keys` (same compacted topic, keyed by `keyId`).

```
Engine startup:
  Generates/loads Ed25519 key pair
  Publishes SigningKeyDTO to <namespace>.taktx-signing-keys

Ingester:
  Consumes <namespace>.taktx-signing-keys → maintains Map<keyId, PublicKey>
  On each instance-update record: extracts X-TaktX-Signature header,
  calls Ed25519Service.verify(payloadBytes, sigBytes, publicKey)
```

### 4.4 Replay protection (auditId nonce)

Every RS256 authorization token carries a unique `auditId` UUID. The engine maintains an in-memory Caffeine cache (10-minute TTL, 100K max entries). If the same `auditId` arrives on a second Kafka message, the command is rejected. This prevents replay attacks even if a valid token is captured in transit.

### 4.5 Engine-internal commands (unified token model)

Engine-originated commands (timer starts, message correlations, call activities) use the **same JWT format** as Platform Service tokens but with a different issuer:

```
iss: "taktx-engine:<engineInstanceId>"
```

The consumer reads `iss` first, looks up the correct key from the signing-keys KTable, then validates using the same `AuthorizationTokenValidator` code path. One format, two issuers, one validation path.

| Command source | `iss` | Validation key |
|---|---|---|
| Caller via ingester | `taktx-platform-service` | Platform Service RSA public key |
| Engine internal | `taktx-engine:<id>` | Engine Ed25519 public key (from KTable) |
| Attacker (no key) | Any | Key lookup fails → rejected |
| Attacker (forged platform token) | `taktx-platform-service` | Signature fails → rejected |

### 4.6 Threat model

| Threat | RS256 command | Ed25519 event | Kafka ACLs |
|---|---|---|---|
| External attacker, no Kafka access | — | — | ✅ blocked |
| External attacker with stolen Kafka credentials | ✅ no valid token | ✅ signature fails | — |
| Compromised ingester | ✅ cannot forge Platform Service token | — | — |
| Kafka admin / insider | ✅ no Platform Service private key | ✅ no engine private key | ⚠️ procedural |
| Replay of captured valid token | ✅ auditId nonce | — | — |
| Fake event injection into instance-update | — | ✅ signature fails | ✅ topic ACLs |
| Misconfigured ACLs (common in dev) | ✅ code-level control | ✅ code-level control | — |
| Bare engine (no Console) | N/A (feature off) | N/A (feature off) | — |

**Kafka ACLs and signing are complementary, not alternatives.** Both should be present in production. Signing provides defence-in-depth when ACLs fail or are absent.

### 4.7 Namespace security policy model

The long-term direction is to move TaktX security from independent runtime flags to an explicit
namespace-level security policy with participant capability validation.

**Default posture remains lightweight:** if no explicit secured policy is activated, the effective
mode is `COMMUNITY_OPEN`. Existing standalone/community deployments should remain runnable without
trust anchors, mandatory signing, or mandatory authorization bootstrap.

That includes a bare engine deployment with no Console/control-plane integration: absence of policy
distribution must not make the engine unusable. The default remains sensible `COMMUNITY_OPEN`
behavior.

### 4.7.0 Terminology used in this section

| Term | Meaning |
|---|---|
| **Canonical policy identity** | The exact identity of the namespace policy, consisting of at least `policyVersion` plus canonical `policyHash` / digest of the effective policy content. |
| **Desired policy identity** | The requested policy identity for a namespace, consisting of `desiredPolicyVersion` + `desiredPolicyHash`. |
| **Active policy identity** | The currently authoritative policy identity for a namespace, consisting of `activePolicyVersion` + `activePolicyHash`. |
| **Desired policy** | The operator-requested policy for the namespace. |
| **Active policy** | The currently authoritative policy for protected runtime behavior. |
| **Activation authority** | The single component allowed to move a policy from `REQUESTED` / `VALIDATING` to `ACTIVE`. |
| **`REQUESTED`** | A policy change has been submitted but is not yet authoritative for protected data-plane behavior. |
| **`VALIDATING`** | Required participants are converging on and being checked against the requested policy. |
| **`ACTIVE`** | The policy has passed validation and is now authoritative for protected data-plane behavior. |
| **Participant `READY`** | The participant has observed the exact active canonical policy identity and can satisfy the role-relevant requirements of that policy. |
| **Participant `NOT READY`** | The participant cannot satisfy the active policy, has drifted, or cannot prove convergence on the active canonical policy identity. |
| **Control-plane traffic** | Policy/config/key/status/security-event and similar traffic needed for convergence, observability, and recovery. |
| **Protected data-plane traffic** | Policy-governed BPMN/runtime message flows whose handling depends on the active security posture. |

**Bootstrap env vars (3 — engine and ingester):**

```properties
KAFKA_BOOTSTRAP_SERVERS     # Kafka broker address(es) — must be set before connection
TAKTX_ENGINE_TENANT_ID      # First segment of Kafka topic prefix
TAKTX_ENGINE_NAMESPACE      # Second segment of Kafka topic prefix
```

### 4.7.1 Current implementation in this repo

Today, this repo still implements namespace security as a set of per-namespace runtime fields
delivered through `taktx-configuration` (key `"config"`). This is the currently verified behavior,
not the target end-state.

The platform service stores these per namespace and pushes them to the engine via the ingester
(`POST /internal/config` → `TaktXClient.publishGlobalConfig(GlobalConfigurationDTO)`).
All default to `false` / safe values so no existing deployment is affected on upgrade.

Where control-plane publication/consumption semantics are part of the official runtime model, they
should be exposed through `TaktXClient` rather than reimplemented with bespoke Kafka publishers in
each repo.

| Field | Type | Default | Runtime effect |
|---|---|---|---|
| `signingEnabled` | boolean | `false` | Ed25519 event signing on engine; Ed25519 verification on ingester |
| `engineRequiresAuthorization` | boolean | `false` | Engine rejects existing externally authorized commands unless they carry a valid RS256 authorization token |
| `engineRequiresExternalTaskAuthorization` | boolean | `false` | Engine requires valid RS256 JWT authorization for external task completion |
| `engineRequiresUserTaskAuthorization` | boolean | `false` | Engine requires valid RS256 JWT authorization for user task completion |
| `rbacEnabled` | boolean | `false` | **Reserved — forward-declared; currently no-op on engine** |
| `trustedKeyIds` | `List<String>` | `[]` | JWT `kid` allow-list for RS256 validation. Currently fail-open (any kid in `taktx-signing-keys` accepted). Included for forward-compatibility. |

### 4.7.2 Planned explicit policy model

The planned replacement for the current flag model is a namespace-owned security policy with an
explicit effective security mode:

```java
enum SecurityMode {
  COMMUNITY_OPEN,
  COMMUNITY_SECURED,
  ANCHORED_SECURED,
  MISCONFIGURED_SECURITY
}
```

The canonical namespace policy should include at least:

- desired `mode`
- explicit signing requirements
- explicit authorization requirements
- explicit trust-anchor requirement
- `desiredPolicyVersion`
- `desiredPolicyHash`
- `activePolicyVersion`
- `activePolicyHash`
- `activationState`

`policyHash` is required because `policyVersion` alone is not sufficient to prove that participants
are operating against the exact same policy content, and desired-vs-active identity must remain
separate while a policy is only `REQUESTED` / `VALIDATING`.

For the first slice, `policyHash` is defined as the SHA-256 digest of the canonical requested
effective policy content only. It excludes activation-state wrappers, timestamps, publisher
identity, and unrelated metadata.

Canonicalization contract:

- UTF-8 encoding
- deterministic field order
- explicit booleans always present
- omit null/unknown fields
- lowercase enum serialization
- stable nested-object ordering
- SHA-256 digest
- lowercase hexadecimal output

All participants must use the same canonicalization algorithm implementation or a
compatibility-certified equivalent.

### 4.7.3 Canonical policy convergence and activation

For the first slice of the redesigned model:

- there is exactly one canonical policy identity per namespace
- required participants must converge on the same canonical policy identity
  (`policyVersion` + `policyHash`) before a stricter policy becomes active
- Platform Service is the sole activation authority for the first slice
- participant self-report is observability only and does not establish trust
- runtime checks remain authoritative for actual enforcement

Participants may report readiness but may never independently transition a policy to `ACTIVE`.

For the first slice, required participants for activation should be interpreted narrowly:

- engine nodes assigned to the namespace
- ingesters assigned to the namespace
- required control-plane participants

Ephemeral clients, workers, and transient job handlers should be validated at use time rather than
blocking activation.

The redesigned model must also distinguish between:

- **control-plane traffic** — policy/config/key/status/security-event distribution used for
  convergence and observability
- **protected data-plane traffic** — policy-governed BPMN/runtime messages whose handling depends on
  the active security posture

Approved control-plane topics:

- `<tenant>.<namespace>.taktx-security-policy`
- `<tenant>.<namespace>.taktx-participant-status`
- `<tenant>.<namespace>.taktx-security-events`

These are control-plane topics only. They are not BPMN/runtime protected data-plane topics and do
not participate in normal DLQ semantics.

Control-plane traffic must remain available while a policy is `REQUESTED` or `VALIDATING` so the
system can converge.

This does **not** mean arbitrary producers may mutate authoritative desired state. The architecture
must distinguish between control-plane availability/consumption and authoritative control-plane
mutation of policy/configuration/trust material.

Protected data-plane behavior must remain governed by the previously active policy until the new
policy becomes `ACTIVE`. If there is no previously active secured policy, the default remains
`COMMUNITY_OPEN`.

If a requested policy cannot be satisfied by all required participants:

- activation is rejected
- the previous active policy remains in force
- the blocking participants and mismatch reasons are surfaced explicitly
- a security/configuration event is emitted

If a participant later drifts from the active canonical policy or fails policy-required runtime
checks, that participant becomes `NOT READY`, protected work fails closed, and a security incident is
emitted.

Participants therefore must not publish, consume, or process protected data-plane/BPMN runtime
messages for a requested policy merely because they have observed it. `ACTIVE` + `READY` is required
for protected data-plane participation under that policy. In implementation terms, this means they
must not process, acknowledge as successfully handled, commit/mark successful, or publish derived
protected data-plane output unless `READY` for the exact active identity.

Authoritative control-plane mutation must remain restricted to trusted writer paths. Broker-side
authorization is the baseline; in secured modes, integrity/authentication of authoritative
control-plane messages is also required as the shared client/runtime contract matures.

Authoritative policy consumers must ignore policy messages from unauthorized principals even if
broker ACLs are misconfigured.

### 4.7.4 TaktXClient control-plane support notes

Where the redesigned namespace-policy model requires official control-plane publication or
consumption semantics, those operations should be added to `TaktXClient` (or the official shared
runtime client surface) and then used by Console/ingester/runtime code.

This is a design requirement, not a statement that all such methods already exist today.

The goal is to avoid duplicating bespoke Kafka publishers/consumers across repos for the same
authoritative policy/configuration semantics. Existing supported methods such as
`publishGlobalConfig()`, `publishLicense()`, and signing-key publication should be treated as the
precedent for extending the shared client rather than rolling parallel ad-hoc publication paths.

### 4.7.5 First-slice implementation rules now fixed

The following rules are now fixed for implementation:

- namespace-local control-plane topic naming is approved
- Platform Service is the sole activation authority
- canonical hash rules are fixed as described above
- participant incarnation / TTL semantics are required via `participantInstanceId`, `startedAt`,
  `lastSeenAt`, and `statusExpiresAt`
- expired status must not participate in activation readiness decisions
- mismatch reasons should contain machine code, human-readable message, and optional structured
  metadata
- first-slice verification vocabulary is `UNVERIFIED_STATUS`, `LOCALLY_VERIFIED_STATUS`
- first-slice effective-state vocabulary is `READY`, `NOT_READY`, `MISMATCH`, `STALE`
- migration posture is parallel coexistence: explicit `ACTIVE` namespace policy overrides legacy
  config; absent `ACTIVE` policy preserves current/default `COMMUNITY_OPEN`
- break-glass downgrade requires privileged actor, explicit reason, audit/security event, visible
  transition state, and high-severity classification
- a policy stuck in `VALIDATING` beyond the configured timeout must fail activation, emit an event,
  preserve the previous `ACTIVE` policy, and never partially activate

---

## 5. Signing & Validation — Implementation Status

### ✅ Complete (engine team scope — March 2026)

| Component | Status |
|---|---|
| `AuthorizationTokenValidator` in `taktx-shared` | ✅ |
| `Ed25519Service`, `SigningKeyGenerator`, `SigningKeyDTO` in `taktx-shared` | ✅ |
| `TokenClaims`, `AuthorizationTokenException`, `PublicKeySource` in `taktx-shared` | ✅ |
| `GlobalConfigurationDTO`, `ConfigurationEventDTO` in `taktx-shared` | ✅ |
| `LicenseDTO` in `taktx-shared` (fields present, enforcement deferred) | ✅ |
| `EngineAuthorizationService` — RS256 validation per command | ✅ |
| `MessageSigningService` — Ed25519 signing per event | ✅ |
| `NonceStore` — Caffeine auditId cache | ✅ |
| `PublicKeyProvider` / key lookup path — resolves JWT validation keys from `taktx-signing-keys` by `kid` | ✅ |
| `taktx-signing-keys` + `taktx-configuration` global KTables in engine | ✅ |
| `taktClient.startProcess(defId, version, vars, jwtToken)` overload | ✅ |
| `taktClient.abortElementInstance(instanceId, elementPath, jwtToken)` overload | ✅ |
| Integration tests (`SecurityIntegrationTest` — 5 scenarios against Redpanda) | ✅ |
| Task-completion JWT authorization (`USER_TASK_COMPLETE`, `EXTERNAL_TASK_COMPLETE`) | ❌ planned — see `docs/TASK-COMPLETION-AUTH-ENGINE-DESIGN.md` |
| Explicit namespace security policy (`SecurityMode`, canonical policy identity, convergence rules) | ❌ planned — see `docs/SECURITY-POLICY-ENGINE-REQUIREMENTS.md` |

### ✅ Complete (Console team scope)

| Component | Status |
|---|---|
| `PlatformKeyPublisher` — pushes RSA public key to all ingesters at startup, on namespace create/update, on reconnect | ✅ |
| `SigningKeyResource` (`POST /internal/signing-keys/platform`) — receives key, updates in-memory validator, publishes to `taktx-signing-keys` via TaktXClient | ✅ |
| `AuthorizationTokenValidator` (ingester) — validates all write and read tokens | ✅ |
| **C1** — `taktClient.startProcess(defId, version, vars, authToken)` wired in `DefinitionResource` | ✅ |
| **C2** — `taktClient.abortElementInstance(instanceId, path, authToken)` wired in `InstanceResource` (×3 call sites) | ✅ |
| **C3/C4** — Ed25519 verification: `InstanceUpdateJsonDeserializer` with `shouldValidateSignature=true`; controlled by `taktx.security.signing.enabled` | ✅ |
| **C5** — WebSocket token auth: BFF `GET /api/runway/ws-token`, frontend fetches before WS open, `ProcessEventWebSocket.onOpen()` validates | ✅ |
| Platform Service task-completion BFF contract — `POST /api/runway/usertasks/complete` and `POST /api/runway/externaltasks/complete` resolve ownership for the selected flow-node instance via ingester lookup, enforce process permission, and mint `USER_TASK_COMPLETE` / `EXTERNAL_TASK_COMPLETE` JWTs | ✅ |
| Explicit namespace security policy UX / API model (`SecurityMode`, policy version, mismatch posture, incidents) | ❌ planned — see `docs/SECURITY-POLICY-IMPLEMENTATION-PLAN.md` |

### ❌ Not yet done (post-beta)

| # | What is needed |
|---|---|
| **Task completion JWT authorization** | Finish the ingester/runtime side of the flow: selected-flow-node lookup hardening, completion forwarding with JWT propagation, and engine claim enforcement while keeping the current Platform Service BFF/JWT chain intact. |
| **Key rotation** | `X-TaktX-Rotation-Proof` mechanism designed (see §4.2) but not implemented. Required before production key management. |
| **`processInstanceId` CANCEL binding** | Add `processInstanceId` claim to CANCEL tokens to prevent cross-instance token use within TTL window |

---

## 6. License Schema

### 6.1 Design principles

- `licenseType` is a **display label only** — no enforcement code branches on tier name
- All enforcement is **flag-based** — each flag is independently checkable
- Unknown flags **fail closed** — `LicenseVerifier.getBoolean()` returns `false` for any missing key
- Forward-declared flags cost nothing to add now and prevent breaking schema changes later
- `deploymentModel` determines which track of license types applies

### 6.2 Current schema (as implemented in `001-schema.sql` and `LicenseService.java`)

| Field | Type | Seed value | Enforced? |
|---|---|---|---|
| `licenseType` | STRING | `COMMUNITY` | Display only |
| `customerName` | STRING | `TaktX - Community Edition` | — |
| `customerEmail` | STRING | `info@taktx.io` | — |
| `expiryDate` | DATE | `2026-12-31` | ✅ `isLicenseValid()` |
| `partitionBudget` | INT | `60` | ✅ `enforcePartitionBudget()` — enforced in engine via KTable |
| `maxNamespaces` | INT | `1` | ✅ `enforceNamespaceLimit()` |
| `maxWorkers` | INT | `10` | ✅ enforced in engine via KTable |
| `customPermissions` | BOOL | `false` | ✅ all permission write mutations |
| `eventSigning` | BOOL | `false` | ✅ ingester startup check vs `TAKTX_SIGNING_ENABLED` |
| `runwayStorageTier` | STRING | `inmemory` | Forward-declared |
| `deploymentModel` | STRING | `SELF_MANAGED` | Forward-declared |

### 6.3 Target schema (implemented)

**Changes from original design:**
- `maxClusters` → **removed**
- `maxNamespacesPerCluster` → **renamed** to `maxNamespaces`
- `customRbac` + `customProcessPermissions` → **collapsed** to `customPermissions`
- `runwayPersistentHistory` → **replaced** by `runwayStorageTier` (enum string: `inmemory` | `persisted` | `persisted-scalable`)
- `intelEnabled` / `studioEnabled` → **removed** (too coarse; future Intel/Studio modules will use their own fine-grained flags when built)

**Full current schema:**

| Field | Type | FREE | STANDARD | PROFESSIONAL | ENTERPRISE | Notes |
|---|---|---|---|---|---|---|
| `licenseType` | STRING | `COMMUNITY` | `STANDARD` | `PROFESSIONAL` | `ENTERPRISE` | Display only |
| `deploymentModel` | STRING | `SELF_MANAGED` | `SELF_MANAGED` | `SELF_MANAGED` | `SELF_MANAGED` | `SELF_MANAGED` \| `SAAS` |
| `maxNamespaces` | INT | 1 | 5 | unlimited | unlimited | null = unlimited |
| `partitionBudget` | INT | 60 | 240 | 960 | null | Total partition budget across all topics (engine fixed + changelog + workers). null = unlimited. Enforced in engine. |
| `maxWorkers` | INT | 10 | 50 | 200 | null | Per namespace; enforced in engine via `taktx-configuration` KTable |
| `customPermissions` | BOOL | false | true | true | true | Gates RBAC + process permission write mutations |
| `eventSigning` | BOOL | false | false | true | true | Gates Ed25519 signing + ingester verification |
| `runwayStorageTier` | STRING | `inmemory` | `persisted` | `persisted` | `persisted-scalable` | Forward-declared; enforced when persistent ingester ships |

**SaaS license types** (parallel track, `deploymentModel = SAAS`):

| Field | SAAS_STARTER | SAAS_GROWTH | SAAS_SCALE | SAAS_ENTERPRISE |
|---|---|---|---|---|
| `maxNamespaces` | 1 | 5 | 20 | null |
| `partitionBudget` | 60 | 240 | 960 | null |
| `maxWorkers` | 10 | 50 | 200 | null |
| `customPermissions` | false | true | true | true |
| `eventSigning` | false | false | true | true |
| `runwayStorageTier` | `inmemory` | `persisted` | `persisted` | `persisted-scalable` |
| `kafkaHosting` | SHARED | SHARED | SHARED | DEDICATED |

### 6.4 Partition budget

`partitionBudget` is a **total partition count** for the entire namespace — covering all engine topics, Kafka Streams changelog topics, and all worker topics combined. This replaces the old per-topic `maxKafkaPartitions` model.

**Advantages over per-topic:**
- Workers can have a different partition count than internal engine topics (performance tuning)
- The budget naturally limits the number of active workers — no separate `maxWorkers` engine enforcement needed for basic containment
- Simpler to reason about broker capacity for SaaS tenants

**Topic overhead per namespace — 30 fixed topics** (engine team confirmed, no repartition topics):

| Category | Count | Topics |
|---|---|---|
| Explicit topics (`Topics` enum) | 14 | `topic-meta-requested`, `topic-meta-actual`, `taktx-configuration`, `taktx-signing-keys`, `xml-by-process-definition-id`, `process-definition-activation`, `message-event`, `schedule-commands`, `instance-update`, `process-instance`, `definitions`, `signals`, `usertasks`, `usertasks-response` |
| Changelog topics (local `Stores`) | 16 | `schedules-minute/hourly/daily/weekly/yearly`, `process-instance`, `flownode-instance`, `process-instance-definition`, `definition-count-by-id`, `xml-by-process-definition-id`, `definition-message-subscription`, `correlation-message-subscription`, `variables`, `version-by-hash`, `instance-signal-subscriptions`, `definition-signal-subscriptions` |

Global stores (`TOPIC_META_REQUESTED`, `TOPIC_META_ACTUAL`, `GLOBAL_CONFIGURATION`, `SIGNING_KEYS`, `GLOBAL_PROCESS_DEFINITION`) are backed directly by their source compacted topics — no extra changelog. No repartition topics.

**Budget allocation example (COMMUNITY, budget=60, 1 partition per engine topic):**
```
Fixed overhead:    30 topics × 1 partition = 30 partitions consumed
Remaining budget:  60 − 30 = 30 partitions available for worker topics
Max workers at 1 partition each: 30
Max workers at 3 partitions each: 10
```

**Tier budgets and SaaS broker sizing:**

| Tier | Budget | Fixed overhead | Worker headroom (1p each) | Tenants on 90k-partition broker |
|---|---|---|---|---|
| COMMUNITY | 60 | 30 | 30 workers | ~1,500 |
| STANDARD | 240 | 30 | 210 workers | ~375 |
| PROFESSIONAL | 960 | 30 | 930 workers | ~93 |
| ENTERPRISE | unlimited | 30 | unlimited | dedicated |

A managed Kafka cluster (e.g. AWS MSK `kafka.m5.large`, 3 brokers) supports ~90,000 partitions comfortably.

### 6.5 Flag dependency: `customPermissions` and `eventSigning`

`customPermissions` without `eventSigning` **is valid** — the RS256 JWT (Keycloak → Platform Service → Engine) is unconditional transport security infrastructure, not a license-gated feature. The JWT travels the chain regardless of license. What `eventSigning` gates is specifically the **Ed25519 signing of engine-emitted events** and the corresponding ingester verification. These are distinct:

- **RS256 command authorization** → always active when authorization is enabled (`TAKTX_SECURITY_AUTHORIZATION_ENABLED=true`) — infrastructure concern
- **Ed25519 event signing** → `eventSigning` license flag — premium feature proving event authenticity to consumers

---

## 7. License Distribution

### 7.1 Implemented flow

```
License upload (POST /api/license/upload)  — or Platform Service startup (@PostConstruct)
  ↓
LicenseService.uploadLicense() / loadActiveLicense()
  ├─ Verifies License3j signature
  ├─ Persists to Postgres (deactivates previous)
  ├─ Updates in-memory cache
  └─ LicensePublisher.publishToAllNamespaces()
       └─ For each namespace:
            POST <ingesterUrl>/internal/license  { licenseText: string }
              ���
            LicenseResource (ingester)
              ├─ InMemoryLicenseHolder.update()   — caches parsed flags locally
              └─ TaktXClient.publishLicense()
                   └─ Publishes to <namespace>.taktx-configuration (key="license", compacted)
                        ↓
                   Engine GLOBAL_CONFIGURATION KTable
                   Parses LicenseDTO, updates in-memory limits (partitionBudget, maxWorkers)
                   No restart required

Namespace create/update — or Platform Service startup (@PostConstruct, @Priority(20))
  ↓
NamespaceConfigPublisher.publishToAllNamespaces() / publishToNamespace()
  └─ For each namespace:
       POST <ingesterUrl>/internal/config  { signingEnabled, engineRequiresAuthorization, engineRequiresExternalTaskAuthorization, engineRequiresUserTaskAuthorization, rbacEnabled, trustedKeyIds }
         ↓
       NamespaceConfigResource (ingester)
         ├─ IngesterConfigHolder.update(signingEnabled)  — caches for local Ed25519 enforcement
         └─ TaktXClient.publishGlobalConfig(GlobalConfigurationDTO)
              └─ Publishes to <namespace>.taktx-configuration (key="config", compacted)
                   ↓
              Engine GLOBAL_CONFIGURATION KTable
              Updates security flags (signingEnabled, engineRequiresAuthorization, engineRequiresExternalTaskAuthorization, engineRequiresUserTaskAuthorization, etc.)
              No restart required
```

**Planned redesign note:** this flow is the currently implemented transport, but the intended
architecture direction is to publish an explicit namespace security policy rather than isolated
security booleans. Until a different transport is verified, the Platform Service remains the
authority for desired policy and the existing Console -> ingester -> engine bridge remains the
verified delivery seam.

Under the redesigned model, participants must observe the same canonical policy identity
(`policyVersion` + `policyHash`) before a stricter policy becomes active. If not all required
participants can satisfy the requested policy, activation is rejected and the previous active policy
remains in force.

During that transition, control-plane distribution remains available, but protected data-plane
behavior continues to follow the previous active policy until the new policy is actually `ACTIVE`.

### 7.2 Push triggers — all stale-license/config edge cases covered

| Trigger | License push | Config push |
|---|---|---|
| Platform Service startup | `LicenseService.loadActiveLicense()` (`@PostConstruct`) | `NamespaceConfigPublisher` (`@Priority(20) StartupEvent`) |
| License upload | `LicenseService.uploadLicense()` | — (config unchanged) |
| Namespace created | `NamespaceResource.createNamespace()` | `NamespaceResource.createNamespace()` |
| Namespace updated | `NamespaceResource.updateNamespace()` | `NamespaceResource.updateNamespace()` |
| Ingester reconnect (UNREACHABLE/UNCONFIGURED → OK) | `IngesterMonitorService.probeNamespace()` | `IngesterMonitorService.probeNamespace()` |
| RSA key rotation (future) | — | `NamespaceConfigPublisher.publishToAllNamespaces()` (updates `trustedKeyIds`) |

The monitor runs every 10 seconds (configurable via `taktx.monitor.interval`). An ingester that was offline during startup will receive the platform key, license, and runtime config within one probe cycle of coming back up.

---

## 8. License — Implementation Status

### ✅ Complete

| Item | Status |
|---|---|
| License3j signature verification (`LicenseVerifier`) | ✅ |
| License storage in Postgres | ✅ |
| `LicenseService` — `getMaxKafkaPartitions()`, `enforcePartitionLimit()` | ✅ |
| `LicenseService` — `enforceNamespaceLimit()` (uses `maxNamespaces`) | ✅ |
| `LicenseService` — `canCustomizePermissions()`, `isEventSigningEnabled()`, `getMaxWorkers()`, `getRunwayStorageTier()`, `getDeploymentModel()` | ✅ |
| `customPermissions` enforcement — all permission write mutations gated | ✅ |
| `LicensePublisher` — full HTTP push to all ingesters, all edge cases covered | ✅ |
| License API (`GET /api/license`, `POST /api/license/upload`, `POST /api/license/validate`, `GET /api/license/raw`) | ✅ |
| Frontend license page — displays type, customer, expiry, features, limits | ✅ |
| `LicenseContext.tsx` — provides license info to all components | ✅ |
| Seed COMMUNITY license in `001-schema.sql` (new field names) | ✅ |
| Ingester `POST /internal/license` — receives, caches, forwards to engine | ✅ |
| `InMemoryLicenseHolder` — parses and caches license flags in ingester | ✅ |
| `TaktXClient.publishLicense()` — publishes to `taktx-configuration` KTable | ✅ (engine team, `0.3.0-beta-1`) |
| Engine enforces `partitionBudget` and `maxWorkers` from KTable | ✅ (engine team) |

### ❌ Not yet done (post-beta)

| # | What is needed |
|---|---|
| **Persistent ingester** | `runwayStorageTier=persisted` and `persisted-scalable` are forward-declared in the license but no persistent ingester implementation exists yet. This is the most important feature gap before STANDARD tier is sellable. |
| **`eventSigning` enforcement at engine level** | Flag is distributed to the engine via KTable but enforcement (rejecting unsigned events) is not yet wired in the engine. Ingester enforces it at startup only. |

---

## 9. Open Items & Gaps

> All beta-scope items (C1–C5, L1–L8) are complete. The items below are post-beta gaps that do not block the beta release but must be addressed before production.

### 9.1 Security gaps (pre-production)

**JWT authorization on user-task and external-task completions**
Platform Service now exposes completion endpoints and mints `USER_TASK_COMPLETE` / `EXTERNAL_TASK_COMPLETE` JWTs after resolving task ownership through an ingester lookup seam, but the ingester/runtime side is not yet implemented in the active app.
- Remaining fix: add ingester active-task lookup + completion forwarding, then validate these completion JWTs in the ingester and engine
- Scope decision: keep the same authorization granularity as existing write commands — `processDefinitionId + version`, not per-flow-node claims in the first slice
- Identity model: support both human users and OIDC service accounts via the same bearer-token / `SecurityIdentity` path
- Detailed delivery docs: `docs/TASK-COMPLETION-AUTH-IMPLEMENTATION-PLAN.md`, `docs/TASK-COMPLETION-AUTH-ENGINE-DESIGN.md`

**`processInstanceId` binding on CANCEL tokens**
CANCEL tokens bind to `processDefinitionId + version` but not to a specific `processInstanceId`. A captured CANCEL token could be used against a different instance of the same definition/version within its 5-minute TTL. The `auditId` nonce prevents exact replay but not cross-instance use.
- Fix: add `processInstanceId` claim to `AuthorizationTokenService.generateToken()`, validate in engine's `EngineAuthorizationService`
- Impact: low risk in beta (requires Kafka access to capture a token); must fix before production

**Key rotation**
The `X-TaktX-Rotation-Proof` mechanism is fully designed (§4.2) but not implemented. In production, responding to an RSA key compromise requires downtime. 
- Fix: implement rotation proof in `PlatformKeyPublisher` + engine `PublicKeyProvider`
- Impact: blocks production key management; does not block beta

**Explicit namespace security policy convergence**
The current production code path still distributes security posture as independent runtime flags.
The planned architecture requires a canonical namespace policy identity plus participant convergence
rules, but that redesign is not yet implemented.
- Remaining fix: replace the flag-centric model with explicit namespace policy, policy version,
  policy digest, participant posture reporting, and activation rejection when required participants
  do not converge
- Remaining fix: explicitly classify control-plane vs protected data-plane message flows per
  participant role so engine/client/runtime participants know what may continue during convergence
  and what must be gated on `ACTIVE` + `READY`
- Remaining fix: secure authoritative control-plane mutation so arbitrary Kafka writers cannot
  overwrite desired policy or trust material
- Remaining fix: add official `TaktXClient` support for any new control-plane operations required by
  the policy model and use those methods instead of bespoke duplicate publishers
- Remaining fix: define activation authority, desired-vs-active identity fields, rollback rules, and
  participant TTL/incarnation semantics explicitly before implementation
- Safety rule: participant status must remain observability only; runtime enforcement must continue
  to fail closed if a participant later drifts or was falsely reported compatible
- Detailed delivery docs: `docs/SECURITY-POLICY-IMPLEMENTATION-PLAN.md`, `docs/SECURITY-POLICY-ENGINE-REQUIREMENTS.md`

**Future verifiability extension**
Hash-chaining / verifiable process-instance update chains should be tracked as a separate future epic
rather than bundled into the first namespace-security-policy slice.

### 9.2 Feature gaps (before STANDARD tier is sellable)

**Persistent ingester**
`runwayStorageTier=persisted` is forward-declared in the license schema but no persistent ingester implementation exists. The in-memory ingester loses all state on restart.
- Required for: STANDARD tier, any production customer

**Multi-namespace support in the ingester**
Each ingester instance handles a single `TAKTX_ENGINE_TENANT_ID` + `TAKTX_ENGINE_NAMESPACE` combination. Multiple console namespaces belonging to the same tenant each require their own ingester instance, configured with the same `TAKTX_ENGINE_TENANT_ID` but a distinct `TAKTX_ENGINE_NAMESPACE`.
- Required for: multi-environment deployments within a single tenant

**`eventSigning` enforcement in engine**
The `eventSigning` flag is distributed to the engine via the `taktx-configuration` KTable but the engine does not yet reject unsigned events based on the flag value. Ingester enforces it at startup only.

### 9.3 Priority order before production

1. Task-completion JWT authorization (`USER_TASK_COMPLETE`, `EXTERNAL_TASK_COMPLETE`)
2. Explicit namespace security policy convergence (`SecurityMode`, canonical policy identity,
   participant convergence / drift handling)
3. `processInstanceId` CANCEL token binding
4. Key rotation (`X-TaktX-Rotation-Proof`)
5. Persistent ingester (`runwayStorageTier=persisted`)
6. Multi-namespace ingester support
7. `eventSigning` enforcement in engine

---

## 10. Future Roadmap

### Module architecture

The Console is three independent future-proof modules, each with its own license flag:

```
TaktX Engine (Kafka Streams) — always free, standalone
       ↓ <tenant-id>.<namespace>.instance-update topic
┌─────────────────┐   ┌──────────────────────��   ┌──────────────────────┐
│  RUNWAY         │   │  INTEL               │   │  STUDIO              │
│  Runtime        │   │  Stream analytics    │   │  BPMN modelling      │
│  monitoring     │   │  (separate consumer  │   │  (design-time,       │
│  (Ingester)     │   │   of event stream)   │   │   collaboration,     │
│  runwayPersist- │   │                      │   │  AI-assisted)       │
│  entHistory     │   │                      │   │  studioEnabled flag  │
└─────────────────┘   └──────────────────────┘   └──────────────────────┘
```

Intel is **not** the ingester — it is a separate service consuming the same `instance-update` Kafka stream independently for historical analysis, SLA tracking, and reporting.

### SaaS architecture (future milestone)

#### Namespace terminology clarification

Two different "namespace" concepts exist in the system — they must not be confused:

| Concept | Where | Meaning |
|---|---|---|
| **Console namespace** | Platform Service DB, frontend UI | A user-visible operational environment (e.g. dev, staging, production). Maps to exactly one ingester. |
| **Engine namespace** | Kafka topic prefix, ingester env var `TAKTX_ENGINE_NAMESPACE` | The second segment of the Kafka topic prefix. An infrastructure concept, never exposed in the UI. |

The engine also introduces a **tenant-id** (`TAKTX_ENGINE_TENANT_ID`), which is the first segment of the topic prefix. Together they form `<tenant-id>.<namespace>.<topicname>` (e.g. `acme.production.instance-update`).

In a SaaS deployment, all console namespaces belonging to the same customer share one `TAKTX_ENGINE_TENANT_ID`. A customer may have multiple console namespaces (dev, staging, production), each backed by its own ingester configured with the same tenant-id but a distinct engine namespace value. The tenant-id is a pure Kafka ACL boundary and is never surfaced in the console API or UI.

#### Topic isolation on shared Kafka

Solved by Kafka prefix ACLs (available since Kafka 2.0). One service account per tenant (`sa-<tenant-id>`), one prefix ACL covering all current and future topics for that tenant:

```
ALLOW  sa-<tenant-id>  ON TOPIC  PREFIXED:<tenant-id>.  READ, WRITE, DESCRIBE
ALLOW  sa-<tenant-id>  ON GROUP  PREFIXED:<tenant-id>.  READ, DESCRIBE
```

Consumer groups and Kafka Streams `application.id` must also carry the `<tenant-id>.<namespace>.` prefix to fall within the ACL scope. The TaktXClient composes the full prefix from `TAKTX_ENGINE_TENANT_ID` and `TAKTX_ENGINE_NAMESPACE` automatically.

**Job worker isolation** — job workers are customer code and must never run in TaktX infrastructure. Workers connect to Kafka via:
- **Starter/Growth**: Internet + SASL/SCRAM over TLS (authenticated, encrypted — acceptable for low-throughput workloads)
- **Scale/Enterprise**: AWS PrivateLink — worker's VPC endpoint connects to TaktX's Kafka NLB endpoint privately within the AWS backbone; TaktX infra remains unreachable from the customer VPC

**Kafka credentials** — managed via cloud provider secret manager (AWS Secrets Manager, GCP Secret Manager, Azure Key Vault) using IAM/workload identity. The `CredentialStore` abstraction in the platform service supports this via a `CredentialBackend` interface. Stored as secret ARN/path rather than encrypted blob for SaaS deployments.

**Engine node scaling** — `num.standby.replicas=1` enables fast scale-up (seconds, not minutes) by pre-warming state on standby nodes. Scale-to-zero is never applied — at least one node must always run to prevent process instance freezes. Recommended: scale-down only during off-peak hours due to Kafka Streams stop-the-world rebalancing cost.

**Replication factor** — tier-based, not customer-configurable, not license-gated (it is a reliability guarantee, not a feature):

| SaaS Tier | Replication factor |
|---|---|
| SAAS_STARTER | 1 (eval only — explicit Console warning) |
| SAAS_GROWTH | 2 |
| SAAS_SCALE | 3 |
| SAAS_ENTERPRISE | 3+ |

### Priority order before production (beta items all complete)

1. Task-completion JWT authorization (security / RBAC gap)
2. `processInstanceId` CANCEL token binding (security gap)
3. Key rotation (`X-TaktX-Rotation-Proof`) (security gap)
4. Persistent ingester (first sellable STANDARD tier deployment)
5. Multi-namespace ingester support
6. `eventSigning` engine enforcement


