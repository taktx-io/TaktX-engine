# TaktX Engine — Security: Signing & License Enforcement

**Last updated:** March 2026  
**Status:** Implemented — all features described here are live in the current codebase

---

## Contents

1. [Overview](#1-overview)
2. [Threat Model](#2-threat-model)
3. [Command Authorization (inbound)](#3-command-authorization-inbound)
4. [Message Signing (outbound)](#4-message-signing-outbound)
5. [Public Key Distribution](#5-public-key-distribution)
6. [License Enforcement](#6-license-enforcement)
7. [Environment Variables & Configuration Reference](#7-environment-variables--configuration-reference)
8. [Key Rotation (design — not yet implemented)](#8-key-rotation-design--not-yet-implemented)
9. [Operational Requirements](#9-operational-requirements)
10. [Known Gaps & Future Work](#10-known-gaps--future-work)

---

## 1. Overview

TaktX enforces security at two levels:

**Command authorization** — Every externally-originated command (start process, cancel instance) arriving at the engine via the `process-instance` Kafka topic must carry a short-lived RS256 JWT issued and signed by the Platform Service. The engine validates the signature, checks claims against the command payload, and enforces replay protection. Commands without a valid token are silently dropped.

**Message signing** — Every execution event the engine emits on the `instance-update` topic is signed with an Ed25519 key. The engine publishes its public key to the `taktx-signing-keys` compacted topic so that downstream consumers (the Ingester, data pipelines) can verify the event came from a genuine engine instance and was not tampered with.

**License enforcement** — The engine enforces license limits (`maxKafkaPartitions`, `eventSigning`) both from a license file at startup and from license records pushed dynamically via the `taktx-configuration` topic. A license push takes effect immediately without a restart.

All features are **opt-in and off by default**. A standalone engine deployment without the Console requires zero configuration change.

---

## 2. Threat Model

### Security gap this closes

Before these features, the engine accepted every command arriving on the `process-instance` Kafka topic unconditionally. Anyone who could write to that topic could start or cancel process instances regardless of permissions. Similarly, execution events carried no proof of origin.

### What each feature protects

| Threat | Primary control | Secondary |
|---|---|---|
| External attacker with Kafka access | Engine rejects (no valid JWT) | Kafka ACLs |
| Compromised ingester process | Engine rejects (ingester cannot forge Platform Service token) | — |
| Insider / Kafka admin | Engine rejects (no Platform Service private key) | Kafka ACLs |
| Replay of captured valid token | `auditId` nonce check (10-min window) | 5-min JWT TTL |
| Forged token | RS256 signature verification | — |
| Wrong action/process in token | Claim-to-command matching | — |
| Fake event injection on `instance-update` | Ed25519 signature verification by consumers | Kafka ACLs |

### What Kafka ACLs give you that signing does not

Kafka ACLs block external attackers before they produce a single message. Engine signing is **defence-in-depth** — it covers the scenarios ACLs cannot handle (compromised ingester, insider with broker access, deployments without ACLs). Both should be used in production. The signing feature works regardless of whether ACLs are configured.

---

## 3. Command Authorization (inbound)

### How it works

```
User → Console (Platform Service)
  │  validates Keycloak JWT + RBAC permissions
  │  mints short-lived RS256 JWT (5-min TTL)
  ▼
Ingester → Kafka record with X-TaktX-Authorization header
  ▼
Engine (EngineAuthorizationService)
  → validates RS256 JWT signature
  → checks action, processDefinitionId, version match command
  → checks auditId not replayed (NonceStore)
  → extracts auditId → stored in ProcessInstanceProcessingContext
  → auditId propagated to every InstanceUpdateDTO emitted
```

### JWT token claims

| Claim | Description |
|---|---|
| `iss` | Issuer — `"taktx-platform-service"` for external commands |
| `sub` | User ID |
| `action` | `"START"` or `"CANCEL"` |
| `processDefinitionId` | Must match the command payload |
| `version` | Must match the command payload |
| `namespaceId` | Namespace the token was issued for |
| `auditId` | UUID — unique per user action, used for replay prevention |
| `exp` | Expiry (5-minute TTL) |

### What the engine checks

1. RS256 signature against `TAKTX_PLATFORM_PUBLIC_KEY`
2. `action` claim matches command type (`START` / `CANCEL`)
3. `processDefinitionId` and `version` match the command payload (for `START`)
4. `auditId` not seen in the last 10 minutes (Caffeine cache, 100k entries)
5. Token not expired (requires `TAKTX_SECURITY_REJECT_EXPIRED=true` to enforce strictly)

### Audit trail

The `auditId` extracted from the token is stored in `ProcessInstanceProcessingContext` and propagated to every `InstanceUpdateDTO` emitted during that command's execution. This provides an unbroken link from user click → engine event.

### Ed25519 authorization for worker responses

Worker responses arriving on the `process-instance` topic with `X-TaktX-Signature` (instead of `X-TaktX-Authorization`) are authorized by:

1. Extracting `keyId` from the header value prefix
2. Looking up the `SigningKeyDTO` from the `taktx-signing-keys` KTable
3. Rejecting if not found or `status == REVOKED`

The engine's own `keyId` (from `TAKTX_SIGNING_KEY_ID`) is trusted directly without a KTable lookup.

---

## 4. Message Signing (outbound)

### How it works

Every record emitted on the `instance-update` topic carries an `X-TaktX-Signature` Kafka record header:

```
X-TaktX-Signature: <keyId>.<base64(Ed25519 signature of value bytes)>
```

The signature covers the raw serialized record value (CBOR bytes). The `keyId` prefix allows consumers to look up the correct public key from the `taktx-signing-keys` topic.

### Key selection

`MessageSigningService.resolveKeyId()` uses this precedence:

1. `signingKeyId` from `GlobalConfigurationDTO` (received via `taktx-configuration` topic — runtime reconfiguration)
2. `TAKTX_SIGNING_KEY_ID` environment variable (startup configuration)

### License gate

Signing is gated on **both** the environment property (`TAKTX_SECURITY_SIGNING_ENABLED=true`) **and** the license (`eventSigning=true`). If the active license does not permit event signing, `MessageSigningService` skips registration even when the env property is `true`. A license push that disables `eventSigning` takes effect immediately.

### Engine public key publication

On startup, when signing is enabled, `EngineSigningKeyPublisher` publishes the engine's Ed25519 public key to the `taktx-signing-keys` compacted topic:

```
Key:   <keyId>          (from TAKTX_SIGNING_KEY_ID)
Value: SigningKeyDTO {
         keyId, publicKeyBase64, algorithm: "Ed25519",
         status: ACTIVE, owner: "engine"
       }
```

This is idempotent — re-publishing on every restart simply overwrites the same compacted record.

---

## 5. Public Key Distribution

### `taktx-signing-keys` topic

A compacted Kafka topic. Each record represents one signing key:

| Field | Description |
|---|---|
| Key (Kafka record key) | `keyId` string |
| `publicKeyBase64` | Base64-encoded Ed25519 public key |
| `algorithm` | `"Ed25519"` |
| `status` | `ACTIVE` \| `TRUSTED` \| `REVOKED` |
| `owner` | `"engine"` or worker identifier |

**Root of trust:** Kafka ACLs must restrict produce rights on `taktx-signing-keys` to only the engine and trusted worker service accounts. This is the same trust model as a JWKS endpoint.

### `taktx-configuration` topic

A compacted Kafka topic with two logical record types distinguished by key:

| Kafka record key | Value type | Purpose |
|---|---|---|
| `"config"` | `ConfigurationEventDTO` (JSON) | Runtime configuration: `signingEnabled`, `signingKeyId`, `trustedKeyIds`, `rbacEnabled` |
| `"license"` | Raw License3j plain text (UTF-8) | License push from Platform Service (see §6) |

The engine registers a single `addGlobalStore` processor on this topic (`LicenseConfigProcessor`) that handles both keys:
- `"config"` → deserialises to `ConfigurationEventDTO`, updates `GlobalConfigStore` (read by `MessageSigningService`)
- `"license"` → calls `LicenseManager.parsePushedLicense()`

### How consumers discover keys

The `taktx-signing-keys` KTable is a Kafka Streams `globalTable` in the engine — replayed from offset 0 at every startup. Worker clients use `SigningKeysStore` from `taktx-shared`: a `ConcurrentHashMap<keyId, SigningKeyDTO>` populated by consuming the topic to end-of-topic before accepting any trigger records.

---

## 6. License Enforcement

### License sources (priority order)

| Priority | Source | When active |
|---|---|---|
| 1 (highest) | Pushed license via `taktx-configuration` topic (key `"license"`) | After first push; survives restarts via topic compaction |
| 2 | License file on disk (`TAKTX_LICENSE_FILE` or `taktx.license.file`) | Engine startup |
| 3 | Default free tier | No file or push present |

### Enforced limits

| License field | Enforcement point | Default (free tier) |
|---|---|---|
| `maxKafkaPartitions:INT` | `TopicBootstrapper` + `DynamicTopicManager` via `LicenseManager.getPartitionBudget()` — enforced as **total partitions** across all managed topics, not per-topic | 60 |
| `eventSigning=false` | `MessageSigningService.registerSigningFunction()`, `resolveKeyId()`, `EngineSigningKeyPublisher.publishIfEnabled()` | `false` |
| `commandAuthorization=false` | `EngineAuthorizationService.authorize()` — when `false`, authorization is bypassed even if `taktx.security.authorization.enabled=true` | `false` |

### Not enforced in the engine (by design)

| License field | Reason |
|---|---|
| `maxWorkers` | Correct enforcement requires a worker heartbeat registry topic (future work). Topic-count proxy is unreliable — see architectural decision record. |
| `maxNamespaces` | Enforced at Platform Service / Console level, not per-engine node. |
| `licenseType`, `customerName`, `customerEmail` | Informational / logging only. |
| `deploymentModel`, `runwayStorageTier` | Enforced by Platform Service or future Runway storage feature. |
| `customPermissions` | Informational only. |

### License push mechanism

The Platform Service validates the License3j file, then the Ingester forwards it to the engine via `TaktXClient.publishLicense(licenseText)`:

```
Platform Service → POST /internal/license (License3j signed text)
  ▼
Ingester (LicenseResource)
  → taktXClient.publishLicense(licenseText)
  ▼
Kafka: <namespace>.taktx-configuration
  key:   "license"
  value: raw License3j UTF-8 bytes
  ▼
Engine (LicenseConfigProcessor on GlobalStreamThread)
  → LicenseManager.parsePushedLicense(licenseText)
  → signature verified (License3j isOK())
  → expiry checked
  → LicenseManager.updateFromLicensePush(licenseType, maxKafkaPartitions, eventSigning)
  → log: "License updated from configuration topic: type={} maxPartitions={}"
```

The License3j signature is verified against the same embedded public key used for file-based licenses — Platform Service signs pushed licenses with the same key.

Compaction ensures only the latest license record is retained. Publishing the same license twice is idempotent.

### License3j field reference

```
licenseType=COMMUNITY              # COMMUNITY | STANDARD | ENTERPRISE
maxKafkaPartitions:INT=3           # null = unlimited
maxWorkers:INT=25                  # parsed but not enforced (see above)
eventSigning=false                 # Boolean — permits Ed25519 signing of outbound engine events
commandAuthorization=false         # Boolean — permits RS256 JWT validation of inbound commands
customPermissions=false            # informational
customerName=...                   # informational
customerEmail=...                  # informational
runwayStorageTier=inmemory         # inmemory | persisted | persisted-scalable
deploymentModel=SELF_MANAGED       # SELF_MANAGED | SAAS
expiryDate:DATE=2099-12-31 00:00:00.000
licenseSignature:BINARY=...        # License3j signature — verified by isOK()
signatureDigest=SHA-512
```

---

## 7. Environment Variables & Configuration Reference

### Engine (`taktx-engine`)

#### Command authorization

| Environment variable | `application.properties` key | Default | Description |
|---|---|---|---|
| `TAKTX_SECURITY_AUTHORIZATION_ENABLED` | `taktx.security.authorization.enabled` | `false` | Enable RS256 JWT validation on inbound commands. Requires `TAKTX_PLATFORM_PUBLIC_KEY`. |
| `TAKTX_SECURITY_REJECT_EXPIRED` | `taktx.security.authorization.reject-expired` | `false` | Strictly reject expired JWTs. Set `true` in production. |
| `TAKTX_SECURITY_NONCE_CHECK` | `taktx.security.authorization.nonce-check.enabled` | `true` | Replay protection via `auditId` nonce store. |
| `TAKTX_PLATFORM_PUBLIC_KEY` | `taktx.platform.public-key` | _(none)_ | Base64-DER RSA public key of the Platform Service. **Required** when authorization is enabled. Obtain from `GET http://<platform-service>/api/public-key`. Inject as a Kubernetes/Docker secret — never hardcode. |

#### Message signing

| Environment variable | `application.properties` key | Default | Description |
|---|---|---|---|
| `TAKTX_SECURITY_SIGNING_ENABLED` | `taktx.security.signing.enabled` | `false` | Enable Ed25519 signing of outbound `instance-update` records. Also gates public key publication to `taktx-signing-keys`. Requires `TAKTX_SIGNING_PRIVATE_KEY` and `TAKTX_SIGNING_KEY_ID`. |
| `TAKTX_SIGNING_PRIVATE_KEY` | `taktx.signing.private-key` | _(none)_ | Base64-encoded Ed25519 private key for the engine. Generate with `SigningKeyGenerator.generate()`. Inject as a secret. |
| `TAKTX_SIGNING_PUBLIC_KEY` | `taktx.signing.public-key` | _(none)_ | Base64-encoded Ed25519 public key corresponding to the private key. Used for self-verification logging and for publication to `taktx-signing-keys`. |
| `TAKTX_SIGNING_KEY_ID` | `taktx.signing.key-id` | _(none)_ | Identifier for the engine's signing key, e.g. `engine-2026-001`. Included in the `X-TaktX-Signature` header prefix and in the `SigningKeyDTO` published to `taktx-signing-keys`. |

#### License

| Environment variable | `application.properties` key | Default | Description |
|---|---|---|---|
| `TAKTX_LICENSE_FILE` | `taktx.license.file` | _(none)_ | Path to the License3j signed license file. If absent, the engine runs on free-tier defaults (3 Kafka partitions, signing disabled). |

#### Generating an Ed25519 key pair (one-time setup per environment)

```java
import io.taktx.security.SigningKeyGenerator;
import java.security.KeyPair;

KeyPair kp = SigningKeyGenerator.generate();
String privateKeyBase64 = SigningKeyGenerator.encodePrivateKey(kp.getPrivate());
String publicKeyBase64  = SigningKeyGenerator.encodePublicKey(kp.getPublic());
// Choose a stable keyId, e.g. engine-<env>-<year>-001
```

Set the outputs as secrets in your deployment environment:
```
TAKTX_SIGNING_PRIVATE_KEY=<privateKeyBase64>
TAKTX_SIGNING_PUBLIC_KEY=<publicKeyBase64>
TAKTX_SIGNING_KEY_ID=engine-prod-2026-001
```

### Client library (`taktx-client`)

| Environment variable | Description |
|---|---|
| `TAKTX_SIGNING_PRIVATE_KEY` | Worker's Ed25519 private key (same format as engine). Used to sign worker responses. |
| `TAKTX_SIGNING_PUBLIC_KEY` | Worker's Ed25519 public key. Published to `taktx-signing-keys` at startup. |
| `TAKTX_SIGNING_KEY_ID` | Worker's key identifier, e.g. `worker-billing-2026-001`. |

Workers using `TaktXClient.publishLicense()`:

| Method | Description |
|---|---|
| `TaktXClient.publishLicense(String licenseText)` | Publishes raw License3j text to `<namespace>.taktx-configuration` with key `"license"`. Called by the Ingester when Platform Service delivers a new license. Uses `taktPropertiesHelper` for auth/TLS passthrough. |

### `application.properties` template (engine)

```properties
# ── Authorization ─────────────────────────────────────────────────────────────
taktx.security.authorization.enabled=${TAKTX_SECURITY_AUTHORIZATION_ENABLED:false}
taktx.security.authorization.reject-expired=${TAKTX_SECURITY_REJECT_EXPIRED:false}
taktx.security.authorization.nonce-check.enabled=${TAKTX_SECURITY_NONCE_CHECK:true}
%prod.taktx.platform.public-key=${TAKTX_PLATFORM_PUBLIC_KEY:}

# ── Message signing ───────────────────────────────────────────────────────────
taktx.security.signing.enabled=${TAKTX_SECURITY_SIGNING_ENABLED:false}
%prod.taktx.signing.private-key=${TAKTX_SIGNING_PRIVATE_KEY:}
%prod.taktx.signing.public-key=${TAKTX_SIGNING_PUBLIC_KEY:}
%prod.taktx.signing.key-id=${TAKTX_SIGNING_KEY_ID:}

# ── License ───────────────────────────────────────────────────────────────────
taktx.license.file=${TAKTX_LICENSE_FILE:}
```

---

## 8. Key Rotation (design — not yet implemented)

### Key lifecycle states

```
ACTIVE  — used for signing AND accepted for verification
TRUSTED — no longer used for signing; still accepted for verification (drain window)
REVOKED — rejected immediately for both signing and verification
```

### Zero-downtime rotation procedure

```
1. Generate new key pair
2. Publish: SigningKeyDTO { keyId: "engine-2026-002", status: ACTIVE }
3. Update GlobalConfigurationDTO: signingKeyId = "engine-2026-002"
             trustedKeyIds = ["engine-2026-001", "engine-2026-002"]
   → Engine switches to signing with new key immediately
   → All consumers accept both key IDs during drain window
4. After drain window (≥ 1 hour):
   Publish: SigningKeyDTO { keyId: "engine-2026-001", status: TRUSTED }
5. After trust window (≥ 24 hours):
   Publish: SigningKeyDTO { keyId: "engine-2026-001", status: REVOKED }
```

### Consumer verification logic (multi-key aware)

1. Hold all keys from `taktx-signing-keys` where `status != REVOKED`
2. Extract `keyId` from `X-TaktX-Signature` header prefix
3. Look up by `keyId` — reject if not found or `status == REVOKED`
4. Verify the Ed25519 signature

**Prerequisite:** `GlobalConfigurationDTO` must use `signingKeyId` (single active key) + `trustedKeyIds` (all keys accepted for verification) — already implemented in the current DTOs.

---

## 9. Operational Requirements

1. **Kafka ACLs on `taktx-signing-keys`** — produce rights must be restricted to engine and worker service accounts only. This is the root of trust for the key distribution mechanism.

2. **Kafka ACLs on `taktx-configuration`** — produce rights must be restricted to trusted service accounts (Ingester, Platform Service). The license and global config pushed via this topic are trusted by the engine.

3. **Key provisioning** — Engine must have `TAKTX_SIGNING_PRIVATE_KEY`, `TAKTX_SIGNING_PUBLIC_KEY`, and `TAKTX_SIGNING_KEY_ID` set when `TAKTX_SECURITY_SIGNING_ENABLED=true`. If any are absent, startup skips signing registration with a warning rather than failing — allow the operator to enable signing incrementally.

4. **License file** — `TAKTX_LICENSE_FILE` should point to a volume-mounted secret. If absent the engine runs on free-tier defaults silently. A license pushed via the configuration topic overrides the file-based license immediately.

5. **Drain window for key rotation** — The TRUSTED window must exceed maximum message-in-flight time plus worst-case consumer lag. Default recommendation: 1 hour for TRUSTED, 24 hours before REVOKED.

6. **Platform public key** — `TAKTX_PLATFORM_PUBLIC_KEY` is a Base64-DER RSA public key. Obtain from `GET http://<platform-service>/api/public-key`. Rotate by updating the secret and restarting the engine (no hot rotation needed since the Platform Service key changes infrequently).

---

## 10. Known Gaps & Future Work

| Item | Description |
|---|---|
| **Worker response signing** (Phase 2) | Workers should sign task responses with their own Ed25519 key. The engine should verify these against the `taktx-signing-keys` KTable. `EngineAuthorizationService` has the Ed25519 path stubbed out. |
| **Worker key publication** (Phase 2) | Workers should publish their public key to `taktx-signing-keys` at startup via `SigningKeyRegistrar`. `TaktXClient` will need `TAKTX_SIGNING_PRIVATE_KEY` / `TAKTX_SIGNING_KEY_ID` wired through `WorkerSigningContext`. |
| **Dynamic key lookup in client deserializers** (Phase 2) | `JsonDeserializer` base class should accept a `SigningKeysStore` for multi-key lookup instead of a single static `taktx.engine.public.key` property. `SigningKeysStore` (already designed) consumes `taktx-signing-keys` to end-of-topic at startup then maintains a live map. |
| **`maxWorkers` enforcement** | Correct enforcement requires a worker heartbeat/registry topic. Topic-count in `DynamicTopicManager` is unreliable (counts non-worker topics, retains stale entries, racy). Deferred until a `worker-registry` compacted topic is introduced. |
| **Definition deployment signing** (Phase 3) | `DefinitionsTriggerDTO` records on the `definitions` topic are unsigned. Mitigation today: Kafka ACLs. Full fix: sign at the Ingester and verify at the engine. |
| **`processInstanceId` binding on CANCEL tokens** | JWT `action == "CANCEL"` is checked but not bound to a specific instance ID. Requires a coordinated Platform Service change. |
| **Key rotation tooling** | The key lifecycle design (§8) is specified but no tooling or automation exists yet. |
| **`auditId` in child instances (call activities)** | Verify that `auditId` from the parent JWT is propagated into child process instances started by call activities. |


