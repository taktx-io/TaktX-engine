# TaktX — RBAC & Message Signing: Release Summary

**Date:** March 7, 2026
**Release:** Coordinated beta — `taktx-engine`, `taktx-shared`, `taktx-client`, `taktx-client-quarkus`, `taktx-client-spring`
**Branch:** `feature/rbac-signing`
**Status:** Engine team scope — **complete and all tests passing**

---

## 1. What We Built — The Short Version

Every externally-originated command sent to the TaktX engine can now be **cryptographically verified** as having been authorised by a legitimate user through the TaktX Console. Every event the engine emits can now be **cryptographically signed** so that downstream consumers — starting with the Console Ingester — can verify it came from a genuine engine instance and was not tampered with in transit.

Both features are **off by default**. A bare engine deployment with no Console requires zero configuration change and behaves identically to before.

---

## 2. The Security Gap We Closed

### Before this release

```
User → Browser → Console → Ingester → Kafka → Engine
```

The engine consumed commands from Kafka without any verification. Any process that could write to the `<namespace>.process-instance` Kafka topic could start or cancel process instances — regardless of who requested it or whether they had permission. There was no way to distinguish a legitimate command issued by an authorised user from a forged one injected directly into Kafka.

Similarly, events flowing back from the engine (`instance-update` topic) carried no proof of origin. A compromised broker or a rogue producer could inject fake state updates that the Ingester would accept as genuine.

### After this release

```
User → Browser → Console (Platform Service)
  │  validates Keycloak JWT + RBAC permissions
  │  mints short-lived RS256 JWT (5-min TTL, auditId, action, processDefinitionId)
  ▼
Ingester → Kafka record with X-TaktX-Authorization header
  ▼
Engine → verifies RS256 JWT → checks auditId not replayed → executes command
  │
  └─ emits instance-update with X-TaktX-Signature header (Ed25519)
       ▼
    Ingester → verifies Ed25519 signature against engine's published public key
```

The engine now enforces that **every externally-originated command was explicitly authorised** by the Platform Service. The Ingester can now **verify that every event was emitted by the real engine** and has not been altered.

---

## 3. What Was Built — Engine Perspective

### 3.1 New classes in `taktx-shared` (reusable by Console team)

| Class | What it does |
|---|---|
| `AuthorizationTokenValidator` | Framework-agnostic RS256 JWT validation using `jjwt 0.12.5`. Verifies signature, expiry, and maps claims to `TokenClaims`. Used by both the engine and available to the Ingester. |
| `TokenClaims` | Parsed JWT claims: `userId`, `issuer`, `action`, `processDefinitionId`, `version`, `namespaceId`, `auditId`, `issuedAt`, `expiresAt`. |
| `AuthorizationTokenException` | Shared exception type — thrown on any validation failure. |
| `PublicKeySource` | `@FunctionalInterface` key-lookup abstraction wired into `AuthorizationTokenValidator`. |
| `Ed25519Service` | JDK-native Ed25519 sign and verify. No external crypto library. ~50,000 ops/sec per core. |
| `SigningKeyGenerator` | Generates and encodes Ed25519 key pairs. Used at engine startup and in tests. |
| `SigningKeyProvider` / `EnvironmentVariableKeyProvider` | Abstraction for private key retrieval. The environment variable implementation reads `TAKTX_SIGNING_PRIVATE_KEY` (or any key ID). |
| `SigningKeyDTO` | Public key distribution DTO — published to the `taktx-signing-keys` compacted topic so the Ingester can discover and verify engine signatures. |
| `GlobalConfigurationDTO` / `ConfigurationEventDTO` | Cluster-wide configuration model (signing enabled, RBAC enabled, active key IDs) published via the `taktx-configuration` compacted topic. |
| `LicenseDTO` | License feature flags for signing, RBAC, and encryption (enforcement deferred to a licensing sprint). |

All of the above are in `taktx-shared` — the Console team can take this module as a dependency instead of maintaining their own copies.

### 3.2 New classes in `taktx-engine`

| Class | What it does |
|---|---|
| `PublicKeyProvider` | `@ApplicationScoped` CDI bean. Parses the Base64-DER RSA public key from `TAKTX_PLATFORM_PUBLIC_KEY` at startup. Fails fast with a clear message if authorization is enabled but the key is absent. |
| `NonceStore` | `@ApplicationScoped` Caffeine cache (10-min TTL, 100k entries). Records every `auditId` seen. `checkAndRecord()` returns `false` if the same `auditId` arrives twice — preventing replay attacks. |
| `EngineAuthorizationService` | `@ApplicationScoped` CDI bean. Validates the `X-TaktX-Authorization` header on every incoming `ProcessInstanceTriggerDTO`: extracts the JWT, validates it, matches claims to the command payload (`START`/`CANCEL`), checks the nonce store. Returns the `auditId` on success; throws `AuthorizationTokenException` on any failure. |
| `MessageSigningService` | `@ApplicationScoped` CDI bean. Adds the `X-TaktX-Signature` header (`"<keyId>.<base64(Ed25519sig)>"`) to every engine-emitted record on the `instance-update` topic. Reads active key IDs from the `taktx-configuration` global store. No-ops silently when signing is disabled. |

### 3.3 Changes to existing engine classes

| Class | Change |
|---|---|
| `TaktConfiguration` | Four new config properties: `authorizationEnabled`, `rejectExpiredTokens`, `nonceCheckEnabled`, `signingEnabled`, plus `platformPublicKeyBase64` (Optional). |
| `Stores` | Two new entries: `GLOBAL_CONFIGURATION` and `SIGNING_KEYS`. |
| `TopologyProducer` | Two new Kafka Streams `globalTable` setups — one consuming `taktx-configuration`, one consuming `taktx-signing-keys`. Both are compacted topics, replayed from the beginning on every engine startup so the engine always has the current configuration. |
| `ProcessInstanceProcessor` | Authorization hook at the top of `process()`: calls `EngineAuthorizationService.authorize()` before executing any command. Rejects with a log entry on failure — no exception propagates to the Kafka Streams error handler. Stores the `auditId` in thread-local context. |
| `ProcessInstanceProcessingContext` | New nullable `auditId` field populated from the authorization token. |
| `Forwarder` | `forwardInstanceUpdates()` now sets `auditId` on every emitted `InstanceUpdateDTO` and calls `MessageSigningService.signIfEnabled()` to add the signature header before forwarding. |
| `InstanceUpdateDTO` | New nullable `auditId` field on the abstract base — present on all subtypes automatically. `null` for engine-internal commands (timers, call activities, message starts). |
| `Topics` | Two new entries: `CONFIGURATION_TOPIC` (`taktx-configuration`) and `SIGNING_KEYS_TOPIC` (`taktx-signing-keys`), both compacted and both included in `quarkus.kafka-streams.topics`. |
| `DynamicTopicManager` | Added `@PreDestroy` shutdown method, `running` flag, and daemon thread factory — prevents background threads from outliving the CDI container during test profile restarts. |

### 3.4 Changes to `taktx-client`

`ProcessInstanceProducer` and `TaktXClient` have new overloads:

```java
// Start a process with a Platform Service authorization token
taktClient.startProcess(processDefinitionId, version, variables, jwtToken);

// Cancel/abort with a Platform Service authorization token
taktClient.abortElementInstance(instanceId, elementPath, jwtToken);
```

Existing overloads without a token delegate to the new ones with `null` — **no behaviour change** for any existing caller. The token is attached as the `X-TaktX-Authorization` Kafka record header when non-null and non-blank.

### 3.5 Configuration

All features are **opt-in**:

```properties
# Enable JWT authorization of incoming commands (requires Console + Platform Service)
taktx.security.authorization.enabled=${TAKTX_SECURITY_AUTHORIZATION_ENABLED:false}
taktx.security.authorization.reject-expired=${TAKTX_SECURITY_REJECT_EXPIRED:false}
taktx.security.authorization.nonce-check.enabled=${TAKTX_SECURITY_NONCE_CHECK:true}

# Base64-DER RSA public key from: GET http://<platform-service>/api/public-key
# Required only when authorization is enabled. Inject as a Kubernetes/Docker secret.
%prod.taktx.platform.public-key=${TAKTX_PLATFORM_PUBLIC_KEY:}

# Enable Ed25519 signing of engine-emitted events
taktx.security.signing.enabled=${TAKTX_SECURITY_SIGNING_ENABLED:false}
```

---

## 4. What Was Built — Test Coverage

### Integration tests (`SecurityIntegrationTest`)

All five scenarios exercise a fully-started engine instance with both authorization and signing enabled against a real embedded Kafka broker (Testcontainers Redpanda):

| Test | What it proves |
|---|---|
| `validJwt_commandAccepted_processInstanceStarted` | A properly-signed JWT from the Platform Service allows a process to start. |
| `missingJwt_commandRejected_noProcessInstanceStarted` | A command without the `X-TaktX-Authorization` header is silently dropped — no process instance is created. |
| `replayedAuditId_secondCommandRejected` | Replaying the exact same JWT a second time is rejected — the nonce store detects the duplicate `auditId`. |
| `instanceUpdateRecord_hasSignatureHeader` | Every `instance-update` record emitted by the engine carries an `X-TaktX-Signature` header. |
| `instanceUpdateRecord_signatureIsVerifiable` | The Ed25519 signature on the record is cryptographically valid against the engine's published public key. |

### Unit tests

| Class under test | Test class |
|---|---|
| `Ed25519Service` | `Ed25519ServiceTest` |
| `SigningKeyGenerator` | `SigningKeyGeneratorTest` |
| `EnvironmentVariableKeyProvider` | `EnvironmentVariableKeyProviderTest` |
| `AuthorizationTokenValidator` | `AuthorizationTokenValidatorTest` |
| `EngineAuthorizationService` | `EngineAuthorizationServiceTest` |
| `MessageSigningService` | `MessageSigningServiceTest` |
| `NonceStore` | `NonceStoreTest` |
| `PublicKeyProvider` | `PublicKeyProviderTest` |

### Test infrastructure fixes (no functional change, but important)

- **`DynamicTopicManager`** — background consumer threads now stop cleanly on CDI container shutdown (`@PreDestroy` + `running` flag + daemon threads). This eliminated a classloader conflict that caused spurious failures when multiple Quarkus test profiles ran in the same Gradle build.
- **`SecurityIntegrationTest`** — now owns and manages its own `BpmnTestEngine` instance instead of using the shared `SingletonBpmnTestEngine`. Quarkus profile restarts can no longer leave stale Kafka connections from the previous profile instance.
- **`BpmnTestEngine.reset()`** — now clears all per-test mutable state: `userTaskTriggerQueueMap`, `activeExternalTaskTriggers`, `variablesMap`, `messageSubscriptionMap`, `signalMap`, and active-selection pointers, in addition to the three maps it cleared previously. This fixed a cross-test state bleed where a queued user task trigger from a completed test was picked up by the next test's `waitUntilUserTaskIsWaitingForResponse`.
- **HTTP port** — both the default test profile and `security-test` profile now use `quarkus.http.port=0` (random port) so that Quarkus profile restarts never collide on port 8079.

---

## 5. Value for the End-User

### For operators deploying TaktX

**You control who can start processes.** When you enable authorization, only the TaktX Console — acting on behalf of authenticated users with the right RBAC roles — can trigger process execution. Nobody can bypass the Console by writing directly to Kafka. A misconfigured service or a compromised internal component cannot silently start or cancel thousands of process instances.

**You can prove what happened.** Every process instance started through the Console carries an `auditId` that links it to the specific user action. Every `instance-update` event emitted by the engine also carries that `auditId`. You have an unbroken, cryptographically-linked audit trail from user click to engine event.

### For teams consuming engine events (Ingester, data pipelines)

**You can trust what you receive.** Every record on the `instance-update` topic carries an Ed25519 signature. The engine publishes its public key to a dedicated compacted topic (`taktx-signing-keys`). Any consumer can verify that an event genuinely came from a known engine instance and was not injected, replayed, or tampered with. This is essential for any pipeline that uses engine events to drive downstream state mutations — billing, audit logs, external system integrations.

### For security-conscious enterprises

**Zero-trust on the message bus.** The TLS encryption that Kafka provides between brokers and clients protects data in transit. What was missing was **authentication at the record level** — proof that the record came from who it claims to have come from. With this release, both directions of the trust relationship are covered: Console → Engine (RS256 JWT) and Engine → Ingester (Ed25519 signature). This satisfies the core requirement of a zero-trust message bus architecture.

**Replay protection built in.** Each JWT carries a unique `auditId` (UUID). The engine's nonce store rejects any second use of the same `auditId` within a 10-minute window. A compromised token captured in transit cannot be reused to trigger the same action again.

**Short-lived tokens.** JWTs have a 5-minute TTL. Even if a token is captured, it expires quickly. The `reject-expired` flag can be enabled for strict environments.

**Opt-in, backward compatible.** No existing deployment breaks. Teams can adopt the security features on their own schedule. A standalone engine deployment without the Console simply ignores all security headers.

---

## 6. What the Console Team Still Needs to Do

The engine team's scope is complete. The following items are for the Console team and are required before the features are active end-to-end:

| # | Where | Change |
|---|---|---|
| **C1** | Ingester `DefinitionResource` (start process endpoint) | Call `taktClient.startProcess(defId, version, vars, authToken)` — forward the `X-TaktX-Authorization` HTTP request header as the fourth argument. |
| **C2** | Ingester `InstanceResource` (cancel/abort endpoint) | Call `taktClient.abortElementInstance(instanceId, path, authToken)` — forward the header. |
| **C3** | Ingester — new `EngineKeyRegistryConsumer` | On startup, consume `<namespace>.taktx-signing-keys` and maintain an in-memory `Map<String keyId, PublicKey>`. Use `SigningKeyDTO` and `Ed25519Service` from `taktx-shared`. |
| **C4** | Ingester `InstanceUpdateConsumer` | For each record from `<namespace>.instance-update`: extract `X-TaktX-Signature`, look up the key by the `keyId` prefix, call `Ed25519Service.verify(payloadBytes, sigBytes, publicKey)`. Reject or flag records that fail or are missing a signature when signing is expected. |

Everything the Console team needs — `AuthorizationTokenValidator`, `TokenClaims`, `Ed25519Service`, `SigningKeyDTO`, `PublicKeySource` — is already in `taktx-shared` and ready to use.

---

## 7. What Is Deferred to Phase 2

| Item | Reason for deferral |
|---|---|
| **Key rotation** | Requires a `X-TaktX-Rotation-Proof` mechanism: a new JWT signed by the currently-trusted key and containing the new key fingerprint. Deferred until the key management sprint. |
| **`processInstanceId` binding on CANCEL tokens** | Phase 1 only checks `action == "CANCEL"`. Binding to a specific instance ID requires a coordinated Platform Service change. |
| **RBAC license flag enforcement** | `LicenseDTO.rbacAllowed` is present but not enforced. Deferred to the licensing sprint. |

---

## 8. Summary Table

| Capability | Who benefits | Enabled by |
|---|---|---|
| Only authorised users can start/cancel processes | Operators, compliance teams | `TAKTX_SECURITY_AUTHORIZATION_ENABLED=true` + `TAKTX_PLATFORM_PUBLIC_KEY` |
| Replay protection — same JWT rejected twice | Operators, security auditors | On by default when authorization enabled |
| Full audit trail — `auditId` on every event | Audit teams, data pipelines | Automatic when authorization enabled |
| Engine events are cryptographically signed | Downstream consumers, Ingester | `TAKTX_SECURITY_SIGNING_ENABLED=true` |
| Engine public keys auto-distributed via Kafka | Ingester, any consumer | Automatic when signing enabled |
| Standalone deployments unchanged | All existing users | Features default to `false` |

