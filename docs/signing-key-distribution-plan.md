# TaktX — Full Signing & Public Key Distribution Plan

**Date:** March 7, 2026  
**Branch:** `feature/signing-rbac`  
**Status:** Planning — nothing in this document is implemented yet beyond what is listed under "Already Done"

---

## 1. What Is Already Done

The first security increment (committed on `feature/signing-rbac`) delivered:

| Component | Description |
|---|---|
| `X-TaktX-Authorization` RS256 JWT | Validated on inbound `start-process` and `abort` commands via `EngineAuthorizationService` |
| `X-TaktX-Signature` Ed25519 | Engine signs outbound `instance-update` records via `MessageSigningService` |
| `AuthorizationTokenValidator` | Framework-agnostic RS256 JWT validator in `taktx-shared` |
| `Ed25519Service`, `SigningKeyGenerator` | Ed25519 sign/verify and key generation in `taktx-shared` |
| `EnvironmentVariableKeyProvider` | Reads engine private key from `TAKTX_SIGNING_PRIVATE_KEY` env var |
| `PublicKeyProvider` | Holds the Platform RSA public key loaded at engine startup |
| `NonceStore` | Replay protection for `auditId` on inbound commands |
| `SigningKeyDTO` | DTO for distributing public keys via `taktx-signing-keys` topic (defined, not yet published) |
| `GlobalConfigurationDTO` | Cluster config DTO with `signingEnabled`, `rbacEnabled`, `activeKeyIds` (partially wired) |
| `ConfigurationEventDTO` | Wrapper for the `taktx-configuration` compacted topic |
| `Topics.SIGNING_KEYS_TOPIC` | `taktx-signing-keys` compacted topic defined and built into the KStreams GlobalTable |
| `Topics.CONFIGURATION_TOPIC` | `taktx-configuration` compacted topic defined and built into the KStreams GlobalTable |
| Full unit + integration test coverage | For the above |

---

## 2. What Still Needs To Be Built

### 2a. Engine → Worker signing (task triggers)
`MessageSigningService.signIfEnabled()` is currently only called for `instance-update` records in `Forwarder`. It must also be called when the engine writes `external-task-trigger` and `user-task-trigger` records.

### 2b. Client-side signature verification (task triggers + instance-update)
The `InstanceUpdateJsonDeserializer` already contains signature verification logic. This must be generalised and shared so `ExternalTaskTriggerJsonDeserializer` and `UserTaskTriggerJsonDeserializer` get it for free without copy-paste.

### 2c. Worker identity and response signing
Each worker process must have its own Ed25519 key pair and sign its responses (`external-task-response`, `user-task-response`) back to the engine. The engine must verify these.

### 2d. Public key distribution
The engine's public key must be published to `taktx-signing-keys` so workers can discover it dynamically instead of relying on static config. Workers must also publish their own public keys so the engine can verify their responses.

### 2e. Rolling key rotation support
The current `SigningKeyDTO.active` boolean and `GlobalConfigurationDTO.activeKeyIds` list are too coarse for zero-downtime key rotation. Both DTOs need to be updated before anything else is built on top of them.

---

## 3. Security Model

### 3a. Trust boundaries and header conventions

Two headers, two trust domains:

| Header | Algorithm | Direction | Issued by |
|---|---|---|---|
| `X-TaktX-Authorization` | RS256 JWT | Console / Platform / Ingester → Engine | Platform Service JWT issuer |
| `X-TaktX-Signature` | Ed25519 `<keyId>.<base64sig>` | Engine → Workers/Consumers | Engine (private key) |
| `X-TaktX-Signature` | Ed25519 `<keyId>.<base64sig>` | Worker → Engine | Worker (private key) |

The `X-TaktX-Authorization` path handles *human-initiated commands with rich claims* (userId, action, processDefinitionId, auditId, expiry, replay protection). The `X-TaktX-Signature` path handles *machine-to-machine message integrity* — no expiry, no claims, just "this byte sequence was produced by the holder of this key".

### 3b. Root of trust for `taktx-signing-keys`

The `taktx-signing-keys` topic is a compacted Kafka topic. Its integrity relies entirely on **Kafka ACLs**: only the engine and trusted worker startup processes may produce to it. This is the same trust model used by JWKS endpoints — the distribution channel is trusted by controlling write access, not by signing the keys themselves (which would just push the trust problem one level up to a root CA).

**Operational requirement:** Kafka ACLs must restrict produce rights on `taktx-signing-keys` to only the engine service account and worker service accounts. All other principals get consume-only access.

---

## 4. Rolling Key Rotation Design

### 4a. The problem with the current model

`SigningKeyDTO.active` is a single boolean — it conflates "currently signing with this key" and "still trusted for verification". This breaks zero-downtime rotation:

- Engine switches to new key and marks old key `active=false`
- Workers that haven't yet consumed the new `SigningKeyDTO` try to verify against the old public key
- Verification fails for records already in flight that were signed with the old key

The same problem applies on the worker→engine direction.

### 4b. `KeyStatus` enum — three-state lifecycle

Replace `boolean active` with a `KeyStatus` enum:

```
ACTIVE  — key is currently used for signing AND accepted for verification
TRUSTED — key is no longer used for signing but still accepted for verification
           (overlap/drain window — consumers catch up before old sigs are rejected)
REVOKED — key must be rejected immediately for both signing and verification
```

### 4c. Rotation procedure (zero-downtime)

```
Step 1: Generate new key pair on the new node / via rotation tooling
Step 2: Publish new SigningKeyDTO { keyId: "engine-2026-002", status: ACTIVE }
Step 3: Update GlobalConfigurationDTO.signingKeyId = "engine-2026-002"
        (GlobalConfigurationDTO.trustedKeyIds = ["engine-2026-001", "engine-2026-002"])
        → Engine immediately starts signing with new key
        → All consumers still accept both key IDs during drain window
Step 4: After drain window (e.g. 1 hour — longer than max message-in-flight time):
        Publish updated SigningKeyDTO { keyId: "engine-2026-001", status: TRUSTED }
        → Old key still accepted for any lagging records
Step 5: After trust window (e.g. 24 hours):
        Publish SigningKeyDTO { keyId: "engine-2026-001", status: REVOKED }
        → Old key fully retired
```

### 4d. Consumer verification logic (multi-key aware)

Consumers (including the engine verifying worker responses) must:
1. Hold all keys from `taktx-signing-keys` where `status != REVOKED`
2. Extract `keyId` from the `X-TaktX-Signature` header value
3. Look up that specific key by `keyId`
4. Reject if not found or if `status == REVOKED`
5. Verify the signature

This replaces the current single-key `taktx.engine.public.key` static property approach.

### 4e. DTO changes required

**`SigningKeyDTO`** — replace `boolean active` with:
```java
public enum KeyStatus { ACTIVE, TRUSTED, REVOKED }

private KeyStatus status;        // replaces boolean active
private String owner;            // e.g. "engine", "worker-billing", "platform"
```

**`GlobalConfigurationDTO`** — replace `List<String> activeKeyIds` with:
```java
private String signingKeyId;           // the single key currently used to sign
private List<String> trustedKeyIds;    // all keyIds accepted for verification
                                        // (includes signingKeyId + any TRUSTED keys)
```

The `signingKeyId` / `trustedKeyIds` split is important: `signingKeyId` is what the engine uses when calling `signIfEnabled()`. `trustedKeyIds` is what any verifier iterates when checking an incoming signature. During a rotation they temporarily differ.

**Impact on existing code:**
- `MessageSigningService.signIfEnabled()` currently reads `globalConfig.getActiveKeyIds()` and picks the first key the provider has. After the change it reads `globalConfig.getSigningKeyId()` directly — simpler.
- `MessageSigningServiceTest` helper `configEvent(boolean, List<String>)` must be updated to use the new field names.
- `SecurityIntegrationTest.publishSigningConfiguration()` must be updated likewise.

---

## 5. Step-by-Step Implementation Plan

### Step 1 — Update shared DTOs (foundation for everything else)

**Files:** `taktx-shared`

1. Add `KeyStatus` enum to `SigningKeyDTO`, replace `boolean active` with `KeyStatus status`, add `String owner` field.
2. In `GlobalConfigurationDTO`, replace `List<String> activeKeyIds` with `String signingKeyId` and `List<String> trustedKeyIds`.

**Tests to update:**
- `MessageSigningServiceTest.configEvent()` helper
- `SecurityIntegrationTest.publishSigningConfiguration()`

---

### Step 2 — Update `MessageSigningService` to use `signingKeyId`

**File:** `taktx-engine/…/security/MessageSigningService.java`

Change `signIfEnabled()` to read `globalConfig.getSigningKeyId()` instead of iterating `activeKeyIds`. The key selection becomes deterministic and simpler.

**Tests to update:** `MessageSigningServiceTest` — all mock setups.

---

### Step 3 — Move signature verification into `JsonDeserializer` base class

**File:** `taktx-shared/…/serdes/JsonDeserializer.java`

Add to the base class:
- `ENGINE_PUBLIC_KEY_CONFIG = "taktx.engine.public.key"` constant
- `configure(Map, boolean)` — reads the static public key property
- `deserialize(String, Headers, byte[])` — verifies `X-TaktX-Signature` when a key is configured
- Private `verifySignature(byte[], Header)` — the actual Ed25519 check

This means:
- `InstanceUpdateJsonDeserializer` — remove all duplicate logic, keep only `ENGINE_PUBLIC_KEY_CONFIG` re-export for backwards compatibility
- `ExternalTaskTriggerJsonDeserializer` — becomes a one-liner (already extends `JsonDeserializer`)
- `UserTaskTriggerJsonDeserializer` — same

**Key behaviour:** The deserializer looks up the signing key by `keyId` from the header value. Phase 1: only a single static key from config. Phase 2 (Step 8): lookup from live `SigningKeysStore`.

---

### Step 4 — Engine signs external-task and user-task trigger records

**Files:** wherever external-task and user-task triggers are written to Kafka in the engine topology

Locate the points in `TopologyProducer` / `Forwarder` / `DynamicTopicManager` where `ExternalTaskTriggerDTO` and `UserTaskTriggerDTO` are forwarded to their respective topics. Apply the same pattern as `forwardInstanceUpdates()` in `Forwarder`:

```java
byte[] payloadBytes = SERDE.serializer().serialize(null, dto);
RecordHeaders headers = new RecordHeaders();
messageSigningService.signIfEnabled(headers, payloadBytes);
context.forward(new Record<>(key, dto, clock.millis(), headers));
```

**Tests:** Add assertions in the relevant integration tests that external-task and user-task trigger records carry a valid `X-TaktX-Signature` header when signing is enabled.

---

### Step 5 — Worker key pair generation and management

**Files:** `taktx-client/…/TaktXClient.java`, `TaktXClientBuilder`

Add to `TaktXClient`:
- On startup, read `TAKTX_SIGNING_PRIVATE_KEY` env var (or `taktx.signing.private-key` property) — same mechanism as the engine
- If set, derive the corresponding public key and a `keyId` (e.g. `worker-<appName>-<hash-of-pubkey>`)
- Store the key pair in a `WorkerSigningContext` (private key + keyId)
- Expose `WorkerSigningContext` to the responders

`TaktXClientBuilder` gets an optional `withSigningKey(String privateKeyBase64, String keyId)` method, and an auto-detect variant that reads the env var.

---

### Step 6 — Worker signs task responses

**Files:** `ExternalTaskInstanceResponder.java`, `UserTaskInstanceResponder.java`, `ProcessInstanceResponder.java`

Add an optional `WorkerSigningContext` (containing private key + keyId) to both responders. When present, every `send()` call serialises the payload, signs it with Ed25519, and adds `X-TaktX-Signature: <keyId>.<base64sig>` to the `ProducerRecord` headers.

`ProcessInstanceResponder` passes the `WorkerSigningContext` through to all three factory methods (`responderForExternalTaskTrigger`, `responderForExternalTask`, `responderForUserTaskTrigger`).

This is the same header format and algorithm as the engine uses for outbound records — no new concepts for the engine to learn.

---

### Step 7 — Engine verifies worker response signatures

**File:** `taktx-engine/…/security/EngineAuthorizationService.java`

Extend `authorize()` to handle the worker response path on the `process-instance` trigger topic. When a record arrives with `X-TaktX-Signature` instead of (or in addition to) `X-TaktX-Authorization`:

1. Extract `keyId` from the header value
2. Look up `SigningKeyDTO` from the `taktx-signing-keys` KTable (already available in `TopologyProducer`)
3. Reject if not found or `status == REVOKED`
4. Re-serialise the payload and verify the Ed25519 signature against the stored public key

The existing RS256 JWT path is unchanged — the two paths are distinguished by which header is present.

**Tests:** Add unit tests for the new signature-verification path in `EngineAuthorizationServiceTest`. Add integration tests in `SecurityIntegrationTest` that send a signed worker response and verify the engine processes it.

---

### Step 8 — Engine publishes its public key to `taktx-signing-keys`

**File:** A new `SigningKeyRegistrar` bean in the engine (or added to an existing startup component)

On engine startup (after `@PostConstruct`), when signing is enabled:
1. Read the public key corresponding to `TAKTX_SIGNING_PRIVATE_KEY`
2. Publish a `SigningKeyDTO { keyId, publicKeyBase64, algorithm: "Ed25519", status: ACTIVE, owner: "engine" }` to the namespaced `taktx-signing-keys` topic
3. The compacted topic ensures idempotent re-publishing on restart

**In `taktx-shared`:** Extract a reusable `SigningKeyRegistrar` utility (plain Java, no framework annotations) that takes bootstrap servers, topic name, and key details — reusable by engine, workers, and the Platform team's ingester.

---

### Step 9 — Worker publishes its public key to `taktx-signing-keys`

**File:** `TaktXClient.start()`

When a signing key is configured (Step 5), `TaktXClient.start()` publishes a `SigningKeyDTO` for the worker to the namespaced `taktx-signing-keys` topic using `SigningKeyRegistrar` from `taktx-shared`.

The engine's existing `taktx-signing-keys` GlobalKTable picks this up automatically — no engine topology change needed.

---

### Step 10 — Dynamic key lookup in client deserializers

**File:** `taktx-shared/…/serdes/JsonDeserializer.java`, a new `SigningKeysStore` in `taktx-shared`

Add a `SigningKeysStore` to `taktx-shared`:
- `ConcurrentHashMap<String, SigningKeyDTO>` of `keyId → SigningKeyDTO` (holds full DTO including `KeyStatus`)
- Populated at startup by consuming `taktx-signing-keys` from offset 0 to end-of-topic (blocking until caught up), then maintained by a background poll thread
- Thread-safe for reads; background thread updates are atomic map replacements

`TaktXClient` creates a `SigningKeysStore` at startup and passes it to the deserializer config. The `JsonDeserializer` base class is updated to accept either a static single key (Phase 1, `taktx.engine.public.key`) or a `SigningKeysStore` for dynamic multi-key lookup.

This replaces the static `taktx.engine.public.key` property for production deployments while remaining backwards compatible for simple test setups.

**Bootstrap guarantee — why no special handling is needed:**

The compacted `taktx-signing-keys` topic solves bootstrap completely:
- The engine publishes its `SigningKeyDTO` at startup (Step 8) *before* Kafka Streams starts processing any records
- The `SigningKeysStore` polls from offset 0 to end-of-topic before signalling ready — so any key published during a previous engine run is already present
- The worker does not start consuming trigger topics until `SigningKeysStore.awaitReady()` returns

A "transient miss" (trigger arriving with an unknown `keyId`) is not a real scenario: if a worker is receiving trigger records, the engine is by definition already running and has already published its key. A missing `keyId` is therefore always a genuine security violation, not a timing issue, and must be rejected immediately.

The static `taktx.engine.public.key` property is retained for test environments and simple single-node setups where dynamic distribution is overkill.

---

## 6. What Goes in Each Module

### `taktx-shared` changes

| Class | Change |
|---|---|
| `SigningKeyDTO` | Replace `boolean active` → `KeyStatus status`, add `String owner` |
| `GlobalConfigurationDTO` | Replace `List<String> activeKeyIds` → `String signingKeyId` + `List<String> trustedKeyIds` |
| `JsonDeserializer` | Add `configure()`, `deserialize(topic, Headers, byte[])`, `verifySignature()` (Step 3) |
| `SigningKeyRegistrar` (new) | Reusable: publishes a `SigningKeyDTO` to `taktx-signing-keys` (Step 8/9) |
| `SigningKeysStore` (new) | Reusable: subscribes to `taktx-signing-keys`, maintains live `keyId → pubkey` map (Step 10) |

### `taktx-client` changes

| Class | Change |
|---|---|
| `InstanceUpdateJsonDeserializer` | Remove duplicate logic — inherits from base (Step 3) |
| `ExternalTaskTriggerJsonDeserializer` | Remove duplicate logic — inherits from base (Step 3) |
| `UserTaskTriggerJsonDeserializer` | Remove duplicate logic — inherits from base (Step 3) |
| `TaktXClient` / `TaktXClientBuilder` | Add worker signing key management (Step 5), publish key at startup (Step 9) |
| `ExternalTaskInstanceResponder` | Add `WorkerSigningContext`, sign all sends (Step 6) |
| `UserTaskInstanceResponder` | Add `WorkerSigningContext`, sign all sends (Step 6) |
| `ProcessInstanceResponder` | Thread `WorkerSigningContext` through factory methods (Step 6) |

### `taktx-engine` changes

| Class | Change |
|---|---|
| `MessageSigningService` | Use `signingKeyId` instead of `activeKeyIds` (Step 2) |
| `Forwarder` | Sign external-task and user-task trigger records (Step 4) |
| `EngineAuthorizationService` | Add Ed25519 verification path for worker responses (Step 7) |
| `SigningKeyRegistrar` bean (new) | Publish engine public key at startup (Step 8) |
| `MessageSigningServiceTest` | Update mock helper (Steps 1/2) |
| `SecurityIntegrationTest` | Update config helper, add task trigger + response signing tests (Steps 4/7) |

---

## 7. What the Console / Platform Team Needs to Do

The console team already uses `X-TaktX-Authorization` for `startProcess` — no changes needed there.

For the **Ingester** validating engine-signed `instance-update` events:
1. Subscribe to `taktx-signing-keys` (or use `SigningKeysStore` from `taktx-shared`) to obtain the engine's Ed25519 public key dynamically
2. Verify `X-TaktX-Signature` on consumed `instance-update` records using `Ed25519Service` from `taktx-shared`
3. Respect `KeyStatus`: accept `ACTIVE` and `TRUSTED`, reject `REVOKED`

`SigningKeyDTO`, `Ed25519Service`, `SigningKeysStore`, and `SigningKeyRegistrar` are all in `taktx-shared` which the Ingester already depends on.

---

## 8. Operational Requirements

1. **Kafka ACLs:** Produce rights on `taktx-signing-keys` must be restricted to only the engine and worker service accounts. This is the root of trust for the entire key distribution mechanism.
2. **Key provisioning:** Engine and worker processes must have `TAKTX_SIGNING_PRIVATE_KEY` set (or `taktx.signing.private-key` property). If absent and signing is enabled, startup should fail fast with a clear error.
3. **Drain window:** During key rotation, the TRUSTED window must be longer than the maximum expected message-in-flight time plus consumer lag. A default of 1 hour is recommended; 24 hours before REVOKED.
4. **Bootstrap ordering:** The engine publishes its signing key (Step 8) before Kafka Streams begins processing. Workers poll `taktx-signing-keys` to end-of-topic at startup before consuming trigger topics (`SigningKeysStore.awaitReady()`). A worker only receives trigger records from a running engine, so the engine key is guaranteed to be present in the store by the time the first trigger arrives. An unknown `keyId` is always a genuine security violation and must be rejected immediately — no retry or fallback is needed. The static `taktx.engine.public.key` property is retained for test environments only.

---

## 9. Gaps Identified by Full Scenario Walkthrough

Walking through the complete start-to-finish flow (engine start → worker start → deploy → user starts process → call activity → child process → job worker → responses → ingester → console) exposes several items not yet addressed in the plan:

### Gap 1 — `auditId` propagation into child instances (call activities)
When the engine internally starts a child process instance (call activity), it does so without going through the `process-instance` trigger topic — there is no inbound JWT. The `auditId` from the parent's JWT must be explicitly propagated into the child instance's `ProcessInstanceProcessingContext` so that all `InstanceUpdateDTO` events from the child carry the same `auditId` as the parent. **Verify this is already happening**; if not, add it.

### Gap 2 — `SigningKeysStore` behaviour when signing is disabled
If the engine has signing disabled (`taktx.security.signing.enabled=false`), it publishes no key to `taktx-signing-keys`. The worker's `SigningKeysStore` will be empty for the engine key. The deserializer must not reject records that arrive without an `X-TaktX-Signature` header when no key is configured — this is already the intended behaviour ("pass through if no key configured"). Ensure this is explicit in `JsonDeserializer.deserialize()`: only verify if a key is present in the store **and** the record carries the header.

### Gap 3 — Process definition deployment is unsigned
`DefinitionsTriggerDTO` records on the `definitions` topic carry no signature. A malicious actor with Kafka write access could deploy modified BPMN. This is out of scope for the current increment but should be tracked as a future work item. Mitigation for now: Kafka ACLs restrict produce rights on the `definitions` topic to trusted worker service accounts only.

### Gap 4 — Console querying signed data (end-to-end audit trail)
The scenario ends with the console querying the ingester. The ingester has verified every `InstanceUpdateDTO` signature and stored the `auditId`. The console should surface the `auditId` in the process instance detail view so end users can correlate a process instance back to the original authorising token. This is a console/ingester concern, not an engine concern, but worth noting for the platform team.

---

## 10. Implementation Order and Dependencies

```
Step 1  ──▶  Step 2  (DTO changes unlock everything)
         ├──▶  Step 3  (shared deserializer)
         │        └──▶  Step 4  (engine signs task triggers — needs Step 2 for signing, Step 3 for test verification)
         ├──▶  Step 5  (worker key management)
         │        └──▶  Step 6  (worker response signing — needs Step 5)
         │                 └──▶  Step 7  (engine verifies responses — needs Steps 1, 6)
         └──▶  Step 8  (engine publishes key)
                  └──▶  Step 9  (worker publishes key — needs Step 5, Step 8 for SigningKeyRegistrar)
                           └──▶  Step 10 (dynamic lookup — needs Steps 8, 9)
```

Steps 1–4 can be delivered as one increment (engine signing complete, static verification).  
Steps 5–7 are the second increment (worker identity and response trust).  
Steps 8–10 are the third increment (dynamic key distribution, full rolling rotation support).






