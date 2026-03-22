# Security Chain Redesign — Final Implementation Plan

**Date:** March 22, 2026  
**Status:** Ready for implementation  
**Scope:** `taktx-shared`, `taktx-engine`, `taktx-client`

---

## Contents

1. [Edge Case Analysis](#1-edge-case-analysis)
2. [Gap Summary](#2-gap-summary)
3. [Implementation Steps](#3-implementation-steps)
4. [Key Rotation Summary](#4-key-rotation-summary)

---

## 1. Edge Case Analysis

### Legend

| Status | Meaning |
|---|---|
| ✅ | Works with this plan, no extra action |
| 🔧 | Requires changes in this plan |
| ⚠️ | Known limitation, documented |

### A. Standalone engine, no workers, no platform

**Scenario:** Simplest deployment. No config record pushed.

| What happens | Status |
|---|---|
| Engine starts, generates Ed25519 key, publishes to `taktx-signing-keys` | ✅ (publish with `role=ENGINE`) |
| Defaults: `signingEnabled=false`, `engineRequiresAuthorization=false` | ✅ |
| External `startProcess()` via TaktXClient — no JWT, no signature | ✅ (auth disabled → `authorize()` returns null at step 1) |
| Config record `signingEnabled=true` pushed later | ✅ (engine starts signing outbound records) |
| Config record `engineRequiresAuthorization=true` pushed — but no JWT issuer | ⚠️ See §1.F |

### B. Standalone engine + workers, no platform

**Scenario:** Workers execute external tasks. No platform service.

| What happens | Status |
|---|---|
| Engine publishes key with `role=ENGINE` | ✅ |
| Workers publish keys with `role=CLIENT` via `TaktXClient.ensureWorkerKeyPublished()` | ✅ |
| Worker receives `ExternalTaskTriggerDTO` — engine-signed, verified by `FaultTolerantExternalTaskTriggerDeserializer` via `SigningKeysStore` | ✅ (cryptographic verification only, no role check on worker side) |
| Worker sends `ExternalTaskResponseTriggerDTO` back — worker-signed | ✅ |
| Engine receives worker response → `ProcessInstanceTriggerEnvelopeDeserializer` verifies signature → `EngineAuthorizationService.authorize()` → non-entry command → existing Ed25519 flow | ✅ (unchanged) |
| Engine-internal call-activity → `StartCommandDTO` → Ed25519 signed with engine key | 🔧 (**THE BUG FIX** — now accepted because `role=ENGINE`) |

### C. Engine + workers + platform service

**Scenario:** Full deployment with JWT authorization enabled.

| What happens | Status |
|---|---|
| Platform publishes RSA public key to `taktx-signing-keys` with `algorithm=RSA` | ✅ |
| Config pushed: `signingEnabled=true`, `engineRequiresAuthorization=true` | ✅ |
| External client calls `startProcess(proc, ver, vars, jwt)` → JWT attached via `ProcessInstanceProducer.attachAuthorizationHeader()` | ✅ (JWT path unchanged) |
| Engine validates JWT: signature via `kid` → KTable lookup in `PublicKeyProvider` → claims → expiry → nonce | ✅ (unchanged) |
| Engine-internal `StartCommandDTO` (call-activity child start) — Ed25519 signed, no JWT | 🔧 (now accepted: key has `role=ENGINE`, `keyTrustPolicy.isTrustedForRole(key, ENGINE)` → true) |
| Engine-internal `AbortTriggerDTO` (child abort) — Ed25519 signed, no JWT | 🔧 (same fix) |
| Worker response (`ContinueFlowElementTriggerDTO`) — Ed25519 signed, `role=CLIENT` | ✅ (non-entry command → existing flow) |

### D. Worker request/response lifecycle (detailed trace)

**Engine → Worker (request):**

1. Engine processes a service task → `Forwarder.forwardExternalTaskRequests()` → `context.forward()`
2. Goes through `TopologyProducer` sink with `SigningSerializer` wrapping the value serializer
3. `SigningSerializer.serialize(topic, headers, data)` → calls `SigningServiceHolder.get().sign(bytes)` → `MessageSigningService.signToHeaderValue()` → Ed25519 signature → adds `X-TaktX-Signature: <engineKeyId>.<base64sig>` header
4. Produced to `taktx-external-task-trigger` topic
5. Worker's `FaultTolerantExternalTaskTriggerDeserializer` (extends `FaultTolerantJsonDeserializer`) receives record
6. `deserialize(topic, headers, data)` → signature header present → `verifySignature()` → `resolvePublicKey(keyId)` → `SigningKeysStore.getPublicKeyBase64(keyId)` → finds engine key → Ed25519 verify
7. If verification fails: returns `DeserializationResult.failure()` — worker can report BPMN error
8. If unsigned and `localSigningRequired=true`: rejects
9. If unsigned and `localSigningRequired=false`: passes through silently

**Impact of redesign:** None. Worker-side deserialization does cryptographic verification only. `KeyRole` is not consulted. ✅

**Worker → Engine (response):**

1. Worker completes task → `ExternalTaskInstanceResponder.complete()` → `ProcessInstanceResponder.responseEmitter.send()`
2. Producer uses `SigningSerializer` → `SigningServiceHolder.get().sign(bytes)` → `TaktXClient.signWorkerPayload()` → Ed25519 with worker key → `X-TaktX-Signature: <workerKeyId>.<base64sig>`
3. Produced to `taktx-process-instance-trigger` topic
4. Engine's `ProcessInstanceTriggerEnvelopeDeserializer.deserialize()`:
   - Reads signature header → `EngineSigningKeysHolder.get().resolvePublicKey(keyId)` → KTable lookup → finds worker key → verifies
   - Returns `ProcessInstanceTriggerEnvelope(trigger, signatureVerified=true, keyId=workerKeyId)`
5. `EngineAuthorizationService.authorize()`:
   - `engineRequiresAuthorization=true` → proceed
   - Trigger is `ContinueFlowElementTriggerDTO` or `ExternalTaskResponseTriggerDTO` → **NOT** an entry command
   - Falls to step 5 in new flow: existing Ed25519/signing flow
   - Signature header present + verified → `authorizeViaEd25519()` → key lookup → `ACTIVE` + not revoked → accepted

**Impact of redesign:** None for non-entry commands. The role check only applies to entry commands. ✅

### E. Standalone clients consuming instance update stream

**Trace:**

1. Client: `taktXClient.registerInstanceUpdateConsumer(groupId, consumer)`
2. `ProcessInstanceUpdateConsumer.subscribeToTopic()` creates `KafkaConsumer` with `InstanceUpdateJsonDeserializer` (extends `JsonDeserializer` with `shouldValidateSignature=true`)
3. `JsonDeserializer.configure()` picks up `SigningKeysStore` from `SigningKeysStoreHolder` (set by `TaktXClient.initSigningKeysStore()` at startup)
4. On each record from `taktx-instance-update`:
   - `deserialize(topic, headers, data)` → `hasKeySource()` → `signingKeysStore != null` → true
   - If `X-TaktX-Signature` present → `verifySignature()` → `resolvePublicKey(keyId)` → `signingKeysStore.getPublicKeyBase64(keyId)` → finds engine key → verify
   - If absent and `localSigningRequired=false` (default): passes through
   - If absent and `localSigningRequired=true` (or `RuntimeConfigurationHolder.isSigningEnabled()`): rejects
5. Deserialized `InstanceUpdateDTO` is delivered to the registered consumer callback

**Impact of redesign:** None. Instance update stream consumption is a read-only, cryptographic-verification-only path. `KeyRole` is never consulted. ✅

### F. Standalone client starting processes when authorization is enabled but no platform exists

**Trace:**

1. Client calls `taktXClient.startProcess("myProc", vars)` — no JWT token parameter
2. `ProcessInstanceProducer.attachAuthorizationHeader()` → `resolveAuthorizationToken()` → `authorizationTokenProvider == null` → returns null → no header attached
3. Record also goes through `SigningSerializer` → if signing enabled, worker Ed25519 signature is attached
4. Engine receives → `EngineAuthorizationService.authorize()`:
   - `engineRequiresAuthorization=true` → proceed
   - `isEntryCommand = true` (StartCommandDTO)
   - New flow step 4a: JWT header present? **No**
   - Step 4b: signature header present? Yes (worker-signed). Key lookup → `role=CLIENT` → `keyTrustPolicy.isTrustedForRole(key, ENGINE)` → **false** → **rejected: "Key role CLIENT cannot produce entry commands"**
5. Without signing enabled: no JWT header, no sig header → step 4c: **rejected: "Entry command requires JWT or authorized Ed25519 signer"**

**Conclusion:** When `engineRequiresAuthorization=true`, external clients MUST provide a JWT. This is **by design** — it's the entire point of JWT authorization. To use `startProcess()` without JWT, keep `engineRequiresAuthorization=false`.

**How to provide JWT:** Use `TaktXClient.builder().authorizationTokenProvider(myProvider).build()` or pass JWT explicitly: `startProcess(proc, ver, vars, jwtToken)`.

**Status:** ✅ Correct behavior. No gap.

### G. Greenfield deployment — no rolling upgrade concern

Nothing is deployed yet, so there are no pre-existing keys without the `role` field. All keys
will be published with the new schema from day one. No migration strategy is needed.

### H. Signal/message/timer-triggered StartCommandDTOs

These are all engine-originated:
- `SignalProcessor` → signal event → produces `StartCommandDTO`
- `MessageEventProcessor` → message event → produces `StartCommandDTO`
- `ProcessDefinitionActivationProcessor` → timer → produces `StartCommandDTO`

All go through `TopologyProducer` sinks with `SigningSerializer` → signed with engine's Ed25519 key.

**With the redesign:** Engine key has `role=ENGINE` → accepted for entry commands. ✅

### I. UserTaskTriggerDTO forwarded by engine

Engine produces `UserTaskTriggerDTO` to the user-task-trigger topic. This is an outbound event, not an inbound command. It doesn't go through `EngineAuthorizationService`. ✅

---

### J. Key Rotation — Engine (generated source, default)

**Scenario:** Engine restarts → `GeneratedSigningIdentitySource` creates new `engine-<uuid>` key pair.

| What happens | Status |
|---|---|
| New key published to `taktx-signing-keys` with `role=ENGINE`, `status=ACTIVE` | ✅ |
| New outbound records signed with new key | ✅ |
| Workers' `SigningKeysStore` picks up new key within ~1s background poll | ✅ |
| Engine-side KTable picks up new key immediately (same process, live state) | ✅ |
| In-flight records signed with old key are still verifiable (old key stays in topic) | ✅ |
| Old key stays `ACTIVE` forever — never retired or revoked | ⚠️ **GAP** |
| Private key was in-memory only, lost on shutdown — no practical exploit | ✅ (low risk) |

### K. Key Rotation — Engine (file source)

**Scenario:** Operator replaces key files on disk. `FileSigningIdentitySource` detects change within `refreshIntervalMs` (default 1s).

| What happens | Status |
|---|---|
| `FileSigningIdentitySource.currentIdentity()` returns new identity with new keyId | ✅ |
| `MessageSigningService.refreshActiveIdentity()` detects keyId change → resets `publicKeyPublished=false` → schedules re-publication | ✅ |
| New public key published to `taktx-signing-keys` with `role=ENGINE`, `status=ACTIVE` | ✅ |
| Old key stays `ACTIVE` — never moved to `TRUSTED` or `REVOKED` | ⚠️ **GAP** |
| Old private key may still exist on disk → could be used to sign commands | ⚠️ (operator responsibility to delete) |

### L. Key Rotation — Worker

**Scenario:** Worker restarts or key files change → new signing identity.

| What happens | Status |
|---|---|
| `TaktXClient.ensureWorkerKeyPublished()` publishes new key with `role=CLIENT` | ✅ |
| Engine KTable picks up new worker key | ✅ |
| Old worker key stays `ACTIVE` | ⚠️ **GAP** (same as engine) |

### M. Key Revocation

**Scenario:** Operator wants to explicitly revoke a compromised key.

| What happens | Status |
|---|---|
| `SigningKeyRegistrar` has a method to publish with `status=REVOKED` | ❌ **No such method exists** |
| Engine's `EngineAuthorizationService` rejects REVOKED keys | ✅ (code exists at line 163, 265) |
| Worker-side `SigningKeysStore.getPublicKeyBase64()` returns null for REVOKED keys | ✅ (code exists at line 117) |
| `JsonDeserializer.resolvePublicKey()` returns null for REVOKED keys (via store) | ✅ |
| The only way to publish a REVOKED key today is raw Kafka producer code | ⚠️ (as done in `SecurityIntegrationTest` line 936–970) |

### N. Drain Window (ACTIVE → TRUSTED → REVOKED lifecycle)

**Scenario:** Graceful key rotation with overlap period.

| What happens | Status |
|---|---|
| `KeyStatus.TRUSTED` enum value exists | ✅ (defined but never used) |
| TRUSTED keys are accepted for verification (not REVOKED) | ✅ (all non-REVOKED keys accepted) |
| No code transitions a key from ACTIVE → TRUSTED | ❌ **Not implemented** |
| No code transitions a key from TRUSTED → REVOKED after a drain window | ❌ **Not implemented** |
| No scheduled/automatic drain window | ❌ **Not implemented** |

---

## 2. Gap Summary

| Gap | Severity | Resolution |
|---|---|---|
| Engine-originated entry commands rejected (the bug) | **High** | Fixed by key roles + rewritten authorize flow |
| Old keys never retired after rotation | **Medium** | Add `publishKeyStatusChange()` to `SigningKeyRegistrar` + old-key retirement in `MessageSigningService` (Steps 16–17) |
| No revocation API | **Medium** | Add `publishKeyStatusChange()` to `SigningKeyRegistrar` (Step 16) |
| Self-declared roles (malicious key publisher) | **Low** | By design — Kafka ACLs + future Phase 3 countersigning |
| No JWT issuer in standalone + auth-enabled mode | **None** | By design — `engineRequiresAuthorization=true` requires JWT for external entry commands |
| No automated drain window (ACTIVE → TRUSTED → REVOKED) | **Low** | Deferred — manual revocation via `publishKeyStatusChange()` is sufficient for now |

---

## 3. Implementation Steps

Each step below specifies the exact file, the exact change, and the acceptance criteria. Steps are ordered so each is independently testable.

---

### Step 1: Create `KeyRole` enum

**New file:** `taktx-shared/src/main/java/io/taktx/dto/KeyRole.java`

#### Design decision: Why coarse actor-based roles, not fine-grained operation-based roles

An alternative was considered with roles like `EXTERNAL_TASK_LISTENER`,
`EXTERNAL_TASK_RESPONDER`, `USER_TASK_LISTENER`, `USER_TASK_RESPONDER`,
`COMMAND_EXTERNAL`, `COMMAND_INTERNAL`, etc. This was rejected for the following reasons:

**1. One key per process, not per operation.**
A single `TaktXClient` instance has ONE `SigningIdentitySource` and ONE key pair. That same
key signs external task responses, user task responses, and set-variable commands. You can't
give a key the role `EXTERNAL_TASK_RESPONDER` because it also responds to user tasks.
Assigning multiple roles per key turns the enum into a permission *set*, which is a different
(heavier) model.

**2. Listeners don't produce — they don't need a role.**
Listening to external tasks, user tasks, or instance updates is a Kafka consumer (read)
operation. No signing happens. No role is checked. The `KeyRole` only applies to the
producer side (signing outbound records). Roles like `EXTERNAL_TASK_LISTENER` or
`USER_TASK_LISTENER` would be meaningless on a signing key.

**3. The actual security boundary is binary.**
Tracing every code path through `EngineAuthorizationService.authorize()`, there is exactly
ONE authorization decision that depends on the key role:

> *Is this key allowed to produce entry commands (StartCommandDTO / AbortTriggerDTO)?*

Everything else — task responses, set-variable, continuations — goes through the same
non-entry-command path with no role check. There is no security reason to distinguish between
"can respond to external tasks" and "can respond to user tasks" — both are non-entry commands
from the same trust tier.

**4. Enumeration grows with the API surface.**
Every new command type or task type would need a new role value. The `KeyRole` enum becomes
a maintenance burden that tracks the DTO hierarchy instead of expressing a trust level.

**5. What if we need finer control later?**
The `KeyTrustPolicy` interface is the correct extension point. A future policy implementation
could check more than just the role — e.g. restrict a key to specific process definitions,
or require certain claims. That's richer than an enum can express. The coarse role captures
the 80% case; the policy interface captures the edge cases.

#### Recommended design

Three roles, named after who owns the key (which directly maps to what it can do):

| Role | Who uses it | What it can authorize |
|---|---|---|
| `ENGINE` | The engine process itself | Entry commands (StartCommandDTO, AbortTriggerDTO) via Ed25519 — these are engine-internal operations like call-activity child starts, signal/timer starts, child aborts |
| `CLIENT` | Any `TaktXClient` user — workers, standalone clients, platform tools | Non-entry commands (task responses, set-variable) via Ed25519. Entry commands require JWT regardless of key role |
| `PLATFORM` | Reserved for future platform service | Implies ENGINE permissions. Will be relevant when countersigning (Phase 3) is implemented |

Note: The role was named `CLIENT` (instead of the original `WORKER` draft) because a `TaktXClient` user is not always a "worker"
(task processor). It might be a standalone client that only starts processes (via JWT) and
consumes instance updates, or a tool that sets variables. `CLIENT` is more inclusive.

```java
package io.taktx.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;

/**
 * Machine-readable role for a signing key, determining what operations
 * the key is trusted to authorize.
 *
 * <p>The role is a property of the key entry in the {@code taktx-signing-keys} topic,
 * not of the individual operation. A single process has one key with one role.
 *
 * <p>Trust hierarchy: {@code PLATFORM ⊇ ENGINE ⊇ CLIENT}.
 */
@RegisterForReflection
public enum KeyRole {
  /**
   * Engine key — trusted to authorize entry commands (StartCommandDTO, AbortTriggerDTO)
   * via Ed25519 without JWT. Used for engine-internal operations such as call-activity
   * child starts, signal/timer-triggered starts, and child aborts.
   */
  ENGINE,
  /**
   * Client key — trusted to authorize non-entry commands (task responses, set-variable)
   * via Ed25519. Entry commands from clients require JWT authorization regardless of
   * the key role. This is the default role for keys published by TaktXClient.
   */
  CLIENT,
  /**
   * Platform key — reserved for future platform-level trust anchoring.
   * Implies ENGINE permissions in the trust hierarchy.
   */
  PLATFORM
}
```

**Acceptance:** Compiles, no other changes needed.

---

### Step 2: Add `role` field to `SigningKeyDTO`

**File:** `taktx-shared/src/main/java/io/taktx/dto/SigningKeyDTO.java`

**Changes:**
1. Add import for `KeyRole`
2. Add `role` as the **last** field (preserves CBOR array order):
   ```java
   @Builder.Default private KeyRole role = KeyRole.CLIENT;
   ```
3. Add convenience method:
   ```java
   /** Returns the effective role, treating {@code null} (pre-role keys) as {@link KeyRole#CLIENT}. */
   public KeyRole effectiveRole() {
     return role != null ? role : KeyRole.CLIENT;
   }
   ```

**Acceptance:** Existing tests compile and pass. Deserializing a CBOR payload without the `role` field yields `role=null`, and `effectiveRole()` returns `CLIENT`.

---

### Step 3: Add `ENGINE_SIGNED` to `CommandTrustVerificationResult`

**File:** `taktx-shared/src/main/java/io/taktx/dto/CommandTrustVerificationResult.java`

**Change:** Add `ENGINE_SIGNED` value after `SIGNATURE_VERIFIED`:

```java
public enum CommandTrustVerificationResult {
  JWT_AUTHORIZED,
  SIGNATURE_VERIFIED,
  ENGINE_SIGNED,
  AUTHORIZATION_DISABLED,
  LICENSE_BYPASSED
}
```

**Acceptance:** Compiles. Existing tests pass (none reference the enum by ordinal).

---

### Step 4: Create `KeyTrustPolicy` interface

**New file:** `taktx-shared/src/main/java/io/taktx/security/KeyTrustPolicy.java`

```java
package io.taktx.security;

import io.taktx.dto.KeyRole;
import io.taktx.dto.SigningKeyDTO;

/**
 * Determines whether a signing key is trusted to perform operations requiring a given role.
 *
 * <p>Implementations:
 * <ul>
 *   <li>{@link OpenKeyTrustPolicy} — trusts any non-revoked key for its declared role (standalone/dev)
 *   <li>Future: {@code AnchoredKeyTrustPolicy} — additionally verifies a countersignature from
 *       a platform root key
 * </ul>
 */
public interface KeyTrustPolicy {

  /**
   * Returns {@code true} if the given signing key is trusted to perform operations
   * that require the specified role.
   *
   * @param key           the signing key entry from the KTable (never {@code null})
   * @param requiredRole  the minimum role needed for the operation
   */
  boolean isTrustedForRole(SigningKeyDTO key, KeyRole requiredRole);
}
```

**Acceptance:** Compiles.

---

### Step 5: Create `OpenKeyTrustPolicy`

**New file:** `taktx-shared/src/main/java/io/taktx/security/OpenKeyTrustPolicy.java`

```java
package io.taktx.security;

import io.taktx.dto.KeyRole;
import io.taktx.dto.SigningKeyDTO;

/**
 * Default trust policy for standalone and docker-compose deployments.
 *
 * <p>Trusts any non-revoked key whose declared (or effective) role is sufficient
 * for the required role, using the hierarchy: {@code PLATFORM ⊇ ENGINE ⊇ CLIENT}.
 *
 * <p>This is an honor-system policy: the declared role is taken at face value.
 * Enforcement against malicious publishers requires Kafka ACLs (infrastructure)
 * or the future {@code AnchoredKeyTrustPolicy} (countersigning).
 */
public class OpenKeyTrustPolicy implements KeyTrustPolicy {

  @Override
  public boolean isTrustedForRole(SigningKeyDTO key, KeyRole requiredRole) {
    if (key == null) return false;
    if (key.getStatus() == SigningKeyDTO.KeyStatus.REVOKED) return false;

    KeyRole effectiveRole = key.effectiveRole(); // null → CLIENT

    return roleLevel(effectiveRole) >= roleLevel(requiredRole);
  }

  private static int roleLevel(KeyRole role) {
    return switch (role) {
      case CLIENT -> 0;
      case ENGINE -> 1;
      case PLATFORM -> 2;
    };
  }
}
```

**Acceptance:** Unit tests:
- `isTrustedForRole(engineKey, ENGINE)` → true
- `isTrustedForRole(clientKey, ENGINE)` → false
- `isTrustedForRole(clientKey, CLIENT)` → true
- `isTrustedForRole(platformKey, ENGINE)` → true
- `isTrustedForRole(revokedKey, CLIENT)` → false
- `isTrustedForRole(nullRoleKey, CLIENT)` → true (null → CLIENT)
- `isTrustedForRole(nullRoleKey, ENGINE)` → false
- `isTrustedForRole(null, CLIENT)` → false

---

### Step 6: Propagate `role` in `SigningKeyRegistrar`

**File:** `taktx-shared/src/main/java/io/taktx/security/SigningKeyRegistrar.java`

**Changes:**

1. Add import for `KeyRole`.

2. Add new overloads that accept `KeyRole` for each existing public method:

   **Instance method (line 68–78):**
   ```java
   public void publishPublicKey(String keyId, String publicKeyBase64, String owner, KeyRole role) {
     publishPublicKey(keyId, publicKeyBase64, owner, DEFAULT_ED25519_ALGORITHM, role);
   }

   public void publishPublicKey(String keyId, String publicKeyBase64, String owner, String algorithm, KeyRole role) {
     String topic = taktPropertiesHelper.getPrefixedTopicName(Topics.SIGNING_KEYS_TOPIC.getTopicName());
     publishPublicKey(taktPropertiesHelper, topic, keyId, publicKeyBase64, owner, algorithm, role);
   }
   ```
   Existing overloads without `role` default to `KeyRole.CLIENT`.

   **Static method (line 95–133):**
   ```java
   public static void publishPublicKey(
       String bootstrapServers, String topic, String keyId, String publicKeyBase64,
       String owner, String algorithm, KeyRole role) {
     // same as existing but pass role to doPublish
   }
   ```
   Existing overloads without `role` default to `KeyRole.CLIENT`.

   **Internal helper (line 140–160):**
   ```java
   static void publishPublicKey(
       TaktPropertiesHelper taktPropertiesHelper, String topic, String keyId,
       String publicKeyBase64, String owner, String algorithm, KeyRole role) {
     // same as existing but pass role to doPublish
   }
   ```

3. Update `doPublish()` (line 162–195):
   - Add `KeyRole role` parameter
   - In the DTO builder (line 169–177): add `.role(role != null ? role : KeyRole.CLIENT)`

4. Update `publishFromKeyPair()` (line 201–207):
   - Add overload with `KeyRole` parameter
   - Existing method defaults to `KeyRole.CLIENT`

**Acceptance:** Existing tests compile and pass. New test: verify published DTO contains the specified role.

---

### Step 7: Publish engine key with `role=ENGINE`

**File:** `taktx-engine/src/main/java/io/taktx/engine/security/MessageSigningService.java`

**Change in `publishEnginePublicKey()` (line 128–136):**

```java
SigningKeyRegistrar.publishPublicKey(
    config.getBootstrapServers(),
    topic,
    identity.getKeyId(),
    identity.getPublicKeyBase64(),
    "engine",
    identity.getAlgorithm(),
    KeyRole.ENGINE);               // ← ADD THIS
```

Add import for `io.taktx.dto.KeyRole`.

**Acceptance:** Engine publishes its key with `role=ENGINE` visible in the signing-keys topic.

---

### Step 8: Worker key publication uses `role=CLIENT`

**File:** `taktx-client/src/main/java/io/taktx/client/TaktXClient.java`

**Changes:**

1. In the static `publishSigningKey(Properties, String, String, String, String)` method (line 361–368): call the `SigningKeyRegistrar` overload with `KeyRole.CLIENT`.

2. In `ensureWorkerKeyPublished()` (line 434–439): the call to `publishSigningKey()` already flows through the same path, so it inherits `CLIENT`.

3. Add a new `publishSigningKey(...)` overload that accepts `KeyRole` for extensibility (used by platform-team tools that may publish platform keys).

**Acceptance:** Worker keys published with `role=CLIENT`.

---

### Step 9: Create `KeyTrustPolicyProducer`

**New file:** `taktx-engine/src/main/java/io/taktx/engine/security/KeyTrustPolicyProducer.java`

```java
package io.taktx.engine.security;

import io.taktx.security.KeyTrustPolicy;
import io.taktx.security.OpenKeyTrustPolicy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

/**
 * CDI producer for the engine's key trust policy.
 *
 * <p>Currently always returns {@link OpenKeyTrustPolicy}. In the future, this can
 * select an {@code AnchoredKeyTrustPolicy} when {@code TAKTX_PLATFORM_PUBLIC_KEY} is configured.
 */
@ApplicationScoped
public class KeyTrustPolicyProducer {

  @Produces
  @ApplicationScoped
  public KeyTrustPolicy keyTrustPolicy() {
    return new OpenKeyTrustPolicy();
  }
}
```

**Acceptance:** CDI injection of `KeyTrustPolicy` resolves to `OpenKeyTrustPolicy`.

---

### Step 10: Rewrite `EngineAuthorizationService.authorize()`

**File:** `taktx-engine/src/main/java/io/taktx/engine/security/EngineAuthorizationService.java`

This is the core change. Detailed diff:

**A. Add constructor parameter:**

Add `KeyTrustPolicy keyTrustPolicy` to both constructors (CDI constructor at line 78 and test constructor at line 94). Store as field.

```java
private final KeyTrustPolicy keyTrustPolicy;

@Inject
public EngineAuthorizationService(
    TaktConfiguration config,
    GlobalConfigStore globalConfigStore,
    PublicKeyProvider publicKeyProvider,
    NonceStore nonceStore,
    KafkaStreams kafkaStreams,
    LicenseManager licenseManager,
    KeyTrustPolicy keyTrustPolicy) {
  // ...existing assignments...
  this.keyTrustPolicy = keyTrustPolicy;
}
```

Test constructor: accept `KeyTrustPolicy` parameter (or default to `new OpenKeyTrustPolicy()`).

**B. Delete `requiresJwtAuthorization()` method (lines 321–323).**

**C. Rewrite `authorize()` method (lines 171–212):**

```java
public CommandTrustMetadataDTO authorize(
    Headers headers, ProcessInstanceTriggerEnvelope triggerEnvelope) {
  ProcessInstanceTriggerDTO trigger = triggerEnvelope.trigger();
  GlobalConfigurationDTO config = effectiveConfig();
  if (!config.isEngineRequiresAuthorization()) {
    return null;
  }
  if (!licenseManager.isEngineRequiresAuthorization()) {
    log.warn("engineRequiresAuthorization=true in runtime config but the active license "
        + "does not permit command authorization — command accepted without validation");
    return null;
  }

  Header authHeader = lastHeader(headers, AUTH_HEADER);
  Header sigHeader  = lastHeader(headers, SIG_HEADER);

  boolean isEntryCommand =
      trigger instanceof StartCommandDTO || trigger instanceof AbortTriggerDTO;

  if (isEntryCommand) {
    // Path A: JWT present → validate JWT (external client path)
    if (authHeader != null && authHeader.value() != null) {
      return authorizeViaJwt(authHeader, trigger);
    }
    // Path B: Ed25519 signature present → check key role
    if (sigHeader != null && sigHeader.value() != null) {
      return authorizeEntryCommandViaEd25519(sigHeader, triggerEnvelope);
    }
    // Path C: neither JWT nor signature → reject
    throw new AuthorizationTokenException(
        "Entry command " + trigger.getClass().getSimpleName()
        + " requires " + AUTH_HEADER + " (JWT) or " + SIG_HEADER
        + " from an ENGINE-role key");
  }

  // Non-entry commands: existing Ed25519/signing flow (unchanged)
  if (sigHeader != null && sigHeader.value() != null) {
    return authorizeViaEd25519(sigHeader, triggerEnvelope);
  }

  if (config.isSigningEnabled()) {
    throw new AuthorizationTokenException(
        "Missing required " + SIG_HEADER + " header on command "
        + trigger.getClass().getSimpleName());
  }

  return trigger.getCurrentTrustMetadata();
}
```

**D. Add `authorizeEntryCommandViaEd25519()` method:**

```java
/**
 * Authorizes an entry command (StartCommandDTO / AbortTriggerDTO) via Ed25519 signature
 * when the signing key has a role that is trusted for ENGINE operations.
 */
private CommandTrustMetadataDTO authorizeEntryCommandViaEd25519(
    Header sigHeader, ProcessInstanceTriggerEnvelope triggerEnvelope) {
  if (triggerEnvelope.hasSignatureError()) {
    throw new AuthorizationTokenException(triggerEnvelope.signatureError());
  }

  String headerValue = new String(sigHeader.value(), StandardCharsets.UTF_8);
  int dot = headerValue.indexOf('.');
  String keyId = dot >= 0 ? headerValue.substring(0, dot) : headerValue;

  SigningKeyDTO entry = lookupSigningKey(keyId);
  if (entry == null) {
    throw new AuthorizationTokenException(
        "Unknown Ed25519 keyId '" + keyId + "' — rejecting entry command "
        + triggerEnvelope.trigger().getClass().getSimpleName());
  }
  if (entry.getStatus() == SigningKeyDTO.KeyStatus.REVOKED) {
    throw new AuthorizationTokenException(
        "Revoked Ed25519 keyId '" + keyId + "' — rejecting entry command "
        + triggerEnvelope.trigger().getClass().getSimpleName());
  }
  if (!triggerEnvelope.signatureVerified()) {
    throw new AuthorizationTokenException(
        "Ed25519 header present for entry command "
        + triggerEnvelope.trigger().getClass().getSimpleName()
        + " but the signature was not verified by the deserializer");
  }
  if (!keyTrustPolicy.isTrustedForRole(entry, KeyRole.ENGINE)) {
    throw new AuthorizationTokenException(
        "Ed25519 keyId '" + keyId + "' has role " + entry.effectiveRole()
        + " which is not trusted for entry commands (requires ENGINE or PLATFORM)");
  }

  log.info("✅ Authorised (Ed25519/ENGINE) command={} keyId={} owner={}",
      triggerEnvelope.trigger().getClass().getSimpleName(), keyId, entry.getOwner());

  return CommandTrustMetadataDTO.builder()
      .authMethod(CommandAuthMethod.ED25519)
      .verificationResult(CommandTrustVerificationResult.ENGINE_SIGNED)
      .trusted(true)
      .signerKeyId(keyId)
      .signerOwner(entry.getOwner())
      .signerAlgorithm(entry.getAlgorithm())
      .build();
}
```

**E. Keep existing `authorizeViaEd25519()` unchanged** — used for non-entry commands, no role check.

**Acceptance:**
- Engine-signed `StartCommandDTO` with `role=ENGINE` → accepted with `ENGINE_SIGNED`
- Engine-signed `AbortTriggerDTO` with `role=ENGINE` → accepted
- Worker-signed `StartCommandDTO` with `role=CLIENT` → rejected
- Worker-signed `StartCommandDTO` with `role=null` → rejected (null → CLIENT)
- JWT `StartCommandDTO` → accepted (unchanged)
- Worker-signed `SetVariableTriggerDTO` → accepted (unchanged)
- Unsigned `StartCommandDTO` → rejected with clear error message

---

### Step 11: Update `EngineAuthorizationServiceTest`

**File:** `taktx-engine/src/test/java/io/taktx/engine/security/EngineAuthorizationServiceTest.java`

**Changes:**

1. Update the `EngineAuthorizationService` construction in the test setup to pass `new OpenKeyTrustPolicy()` as the `KeyTrustPolicy` parameter.

2. **Split `startCommand_withOnlySignatureStillRequiresJwt` (line 174–184)** into:

   a. `startCommand_workerSignedEntryCommand_rejected`:
      - Register a signing key in the KTable mock with `role=CLIENT`
      - Attach signature header with that worker keyId
      - Create envelope with `signatureVerified=true`
      - Assert throws `AuthorizationTokenException` containing "not trusted for entry commands"

   b. `startCommand_engineSignedEntryCommand_accepted`:
      - Register a signing key in the KTable mock with `role=ENGINE`
      - Attach signature header with that engine keyId
      - Create envelope with `signatureVerified=true`
      - Assert returns `CommandTrustMetadataDTO` with `verificationResult=ENGINE_SIGNED`

   c. `startCommand_nullRoleSignedEntryCommand_rejected`:
      - Register a signing key with `role=null` (backward compat)
      - Assert throws `AuthorizationTokenException`

   d. `abortTrigger_engineSignedEntryCommand_accepted`:
      - Same as (b) but with `AbortTriggerDTO`

3. **Add `ed25519Header_engineKey_returnsEngineSignedMetadata`**:
   - Non-entry command signed with ENGINE key → accepted with `SIGNATURE_VERIFIED` (not `ENGINE_SIGNED` — the ENGINE_SIGNED result is only for entry commands)

4. Verify all existing JWT tests still pass unchanged.

5. Update test helper `envelope(StartCommandDTO)` and `envelope(AbortTriggerDTO)` — keep returning `signatureVerified=false, keyId=null` for the JWT test paths where no Ed25519 is involved.

---

### Step 12: Create `OpenKeyTrustPolicyTest`

**New file:** `taktx-shared/src/test/java/io/taktx/security/OpenKeyTrustPolicyTest.java`

Test all combinations from Step 5 acceptance criteria:

```java
class OpenKeyTrustPolicyTest {
  private final OpenKeyTrustPolicy policy = new OpenKeyTrustPolicy();

  @Test void engineKey_trustedForEngine()      { ... role=ENGINE, required=ENGINE → true }
  @Test void clientKey_notTrustedForEngine()   { ... role=CLIENT, required=ENGINE → false }
  @Test void clientKey_trustedForClient()      { ... role=CLIENT, required=CLIENT → true }
  @Test void platformKey_trustedForEngine()    { ... role=PLATFORM, required=ENGINE → true }
  @Test void platformKey_trustedForPlatform()  { ... role=PLATFORM, required=PLATFORM → true }
  @Test void engineKey_notTrustedForPlatform() { ... role=ENGINE, required=PLATFORM → false }
  @Test void revokedKey_neverTrusted()         { ... status=REVOKED, role=ENGINE → false }
  @Test void nullKey_neverTrusted()            { ... null → false }
  @Test void nullRole_treatedAsClient()        { ... role=null → trusted for CLIENT, not ENGINE }
}
```

---

### Step 13: Add CBOR backward compatibility test

**New file or extend:** `taktx-shared/src/test/java/io/taktx/dto/SigningKeyDTOSerializationTest.java`

Test that a CBOR-encoded `SigningKeyDTO` from BEFORE the `role` field was added (i.e. a shorter array) deserializes successfully with `role=null`.

```java
@Test
void deserialize_oldFormatWithoutRole_yieldsNullRole() {
  // Serialize a SigningKeyDTO using the OLD field set (keyId, publicKeyBase64, algorithm, createdAt, status, owner)
  // Deserialize with the NEW class (which has 7 fields including role)
  // Assert role == null && effectiveRole() == CLIENT
}
```

This test MUST be written before the implementation goes to production — it validates the wire-format migration safety.

---

### Step 14: Update `SigningKeyRegistrar` tests

**File:** `taktx-shared/src/test/java/io/taktx/security/SigningKeyRegistrarTest.java` (extend or new)

- Verify that `publishPublicKey(..., KeyRole.ENGINE)` produces a DTO with `role=ENGINE`
- Verify that the no-role overloads produce a DTO with `role=CLIENT`
- Verify that `publishFromKeyPair(...)` defaults to `role=CLIENT`

---

### Step 15: Update integration test

**File:** Extend existing call-activity or security integration test.

**New scenario:** Enable `engineRequiresAuthorization=true` via config topic, then trigger a call-activity. Assert:
- The child `StartCommandDTO` is accepted (not rejected)
- The `CommandTrustMetadataDTO` on the resulting instance update has `verificationResult=ENGINE_SIGNED`

---

### Step 16: Add `publishKeyStatusChange()` to `SigningKeyRegistrar`

**File:** `taktx-shared/src/main/java/io/taktx/security/SigningKeyRegistrar.java`

Add a method that publishes a status transition for an existing key. This is the building block for both manual revocation and automated old-key retirement.

**Instance method:**
```java
/**
 * Publishes a status change for an existing key. Used for key rotation
 * (ACTIVE → TRUSTED) and revocation (any → REVOKED).
 *
 * <p>The caller must supply the full {@link SigningKeyDTO} with the desired new status.
 * The compacted topic will retain only the latest record per keyId, so this overwrites
 * the previous entry.
 */
public void publishKeyStatusChange(SigningKeyDTO updatedKey) {
  String topic = taktPropertiesHelper.getPrefixedTopicName(
      Topics.SIGNING_KEYS_TOPIC.getTopicName());
  doPublish(
      taktPropertiesHelper.getKafkaProducerProperties(),
      new StringSerializer(),
      new ByteArraySerializer(),
      topic,
      updatedKey);
}
```

**Static convenience methods:**
```java
/**
 * Publishes a REVOKED status for the given keyId. Requires the full key entry
 * so the compacted topic retains the public key material for audit purposes.
 */
public static void revokeKey(String bootstrapServers, String topic, SigningKeyDTO existingKey) {
  // rebuild with status=REVOKED, preserving all other fields
}
```

**Update `doPublish()`:** Currently `doPublish()` rebuilds the DTO and forces `createdAt=Instant.now()`. For status changes, the original `createdAt` should be preserved. Change the rebuild to:
```java
.createdAt(dto.getCreatedAt() != null ? dto.getCreatedAt() : Instant.now())
```

**Acceptance:**
- `publishKeyStatusChange()` with `status=REVOKED` → key is REVOKED in topic, engine/workers reject it within ~1s
- `publishKeyStatusChange()` with `status=TRUSTED` → key is still accepted for verification but marked as retired

---

### Step 17: Retire old engine key on rotation

**File:** `taktx-engine/src/main/java/io/taktx/engine/security/MessageSigningService.java`

When `refreshActiveIdentity()` detects a key change (line 230–251), the old key should be retired in the signing-keys topic. This closes the gap where old keys stay `ACTIVE` forever.

**Changes to `refreshActiveIdentity()`:**

```java
private SigningIdentity refreshActiveIdentity() {
  SigningIdentity identity = signingIdentitySource.currentIdentity();
  if (identity == null) {
    return null;
  }
  boolean changed = !identity.getKeyId().equals(keyId);
  // ...existing change detection...
  if (changed) {
    String previousKeyId = this.keyId;  // ← capture before overwrite
    this.keyId = identity.getKeyId();
    this.cachedPrivateKeyBase64 = identity.getPrivateKeyBase64();
    this.cachedPublicKeyBase64 = identity.getPublicKeyBase64();
    publicKeyPublished.set(!identity.hasPublicKey());
    log.info("Active engine signing identity loaded from source={} keyId={}",
        signingIdentitySource.getSourceType(), keyId);
    if (identity.hasPublicKey() && keyPublicationExecutor != null) {
      schedulePublicKeyPublication(0L);
    }
    // Retire the previous key if it was published
    if (previousKeyId != null && !previousKeyId.equals(keyId)) {
      retirePreviousKey(previousKeyId);
    }
  }
  return identity;
}
```

**New method `retirePreviousKey()`:**

```java
/**
 * Publishes the previous engine key with status=TRUSTED so it is still accepted
 * for in-flight verification but no longer considered the active signing key.
 *
 * <p>Full revocation (TRUSTED → REVOKED) is left to operational tooling or a future
 * scheduled task, since the drain window depends on consumer lag.
 */
private void retirePreviousKey(String previousKeyId) {
  if (keyPublicationExecutor == null) return;
  keyPublicationExecutor.schedule(() -> {
    try {
      String topic = config.getPrefixed(Topics.SIGNING_KEYS_TOPIC.getTopicName());
      // We don't have the full DTO, so we publish a minimal TRUSTED entry.
      // The compacted topic overwrites the old ACTIVE record.
      SigningKeyRegistrar.publishPublicKey(
          config.getBootstrapServers(), topic, previousKeyId,
          /* publicKeyBase64= */ "", // empty — verification will fail, which is OK for TRUSTED
          "engine", "Ed25519", KeyRole.ENGINE);
      // TODO: once publishKeyStatusChange with status param exists, use it here
      log.info("Previous engine key retired: keyId={} → TRUSTED", previousKeyId);
    } catch (Exception e) {
      log.warn("Failed to retire previous engine key keyId={}: {}", previousKeyId, e.getMessage());
    }
  }, PUBLICATION_RETRY_DELAY_SECONDS, TimeUnit.SECONDS);
}
```

**Important design note:** We can only publish a `TRUSTED` transition if we still have the full `SigningKeyDTO` (with the public key). Since `MessageSigningService` only caches the keyId/private/public base64, we need to reconstruct the DTO. The public key is needed so that in-flight records can still be verified during the drain window.

**Revised approach — cache previous identity:**

Add a field:
```java
private volatile SigningIdentity previousIdentity;
```

In `refreshActiveIdentity()` when `changed`:
```java
if (previousKeyId != null && !previousKeyId.equals(keyId)) {
  previousIdentity = SigningIdentity.ed25519(previousKeyId, cachedPrivateKeyBase64, cachedPublicKeyBase64);
  retirePreviousKey();
}
```

Then `retirePreviousKey()` uses `previousIdentity.getPublicKeyBase64()` to publish the TRUSTED entry with the correct public key material.

**Acceptance:**
- Engine restart (generated source): old key is moved to `TRUSTED` in the signing-keys topic
- File-based key rotation: same — old key moves to `TRUSTED`
- Workers' `SigningKeysStore` picks up the status change; old key still valid for verification
- Future operational tooling can push `REVOKED` for old TRUSTED keys

---

### Step 18: Add key rotation tests

**File:** `taktx-engine/src/test/java/io/taktx/engine/security/MessageSigningServiceTest.java` (extend)

- Verify that when the `SigningIdentitySource` returns a new identity (different keyId), the previous key is published with `status=TRUSTED`
- Verify that the new key is published with `status=ACTIVE` and `role=ENGINE`

**File:** `taktx-shared/src/test/java/io/taktx/security/SigningKeyRegistrarTest.java` (extend)

- Verify `publishKeyStatusChange()` publishes the given DTO as-is (preserving status, role, createdAt)
- Verify `revokeKey()` produces a DTO with `status=REVOKED`

---

## 4. Key Rotation Summary

### What this plan covers

| Rotation scenario | Covered |
|---|---|
| Engine restart (generated key) → new key published, old key retired to TRUSTED | ✅ (Step 17) |
| Engine file-based key swap → new key published, old key retired to TRUSTED | ✅ (Step 17) |
| Worker restart → new key published, old key stays ACTIVE | ⚠️ (worker-side retirement is deferred — less critical since worker keys can't produce entry commands) |
| Manual key revocation via `SigningKeyRegistrar.revokeKey()` | ✅ (Step 16) |
| Programmatic key revocation via `publishKeyStatusChange()` | ✅ (Step 16) |
| Automated TRUSTED → REVOKED after drain window | ❌ Deferred — depends on consumer lag monitoring, out of scope |

### What this plan does NOT cover (deferred)

1. **Automated drain window:** Moving keys from TRUSTED → REVOKED after a configurable drain period requires knowledge of consumer lag (how far behind are workers reading?). This is operational tooling, not core engine logic. Can be added later as a scheduled task or external tool.

2. **Worker old-key retirement:** When a worker restarts with new key material, the old key stays ACTIVE. This is lower risk than engine keys because worker keys (role=CLIENT) cannot produce entry commands. Adding retirement to `TaktXClient` is a future enhancement.

3. **Key garbage collection:** Over time, old REVOKED keys accumulate in the compacted topic. A future compaction policy or cleanup job can tombstone very old REVOKED keys.

---

*End of implementation plan*
