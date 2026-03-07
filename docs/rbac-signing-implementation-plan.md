# TaktX — RBAC & Message Signing: Implementation Plan

**Date:** March 7, 2026
**Starting point:** `main` @ `33577e7b` — clean, no signing or envelope code
**Branch to create:** `feature/rbac-signing`
**Status:** Approved — ready to implement
**Release:** Coordinated beta release across taktx-engine, taktx-client, taktx-client-quarkus, taktx-client-spring, and Console (Ingester)
**Authors:** Engine team

---

## 1. Goal

Close the trust gap between the TaktX Console (Platform Service + Ingester) and the TaktX Engine, delivered as a **coordinated beta release** across all components:

1. Allow the engine to **verify that every externally-originated command was authorised by the Platform Service**, using a short-lived RS256 JWT carried as a Kafka record header.
2. Allow the engine to **sign its own internally-generated commands** with Ed25519, so the Ingester and other downstream consumers can verify they came from a legitimate engine instance.
3. Allow the **Ingester to verify engine-signed events** on the `instance-update` topic using the engine's public key distributed via the `taktx-signing-keys` topic.
4. Provide a **traceable `auditId`** on all execution events, linking every emitted event back to the user action that caused it.
5. Keep everything **opt-in and backward compatible** — a bare engine deployment without the Console continues to work with zero configuration change.

---

## 2. Why We Start from Main (not `feature/identity-security-2`)

The earlier `feature/identity-security-2` branch built a `MessageEnvelope<T>` body wrapper that serialised every Kafka message as `{"s":<signature>,"p":<base64-CBOR>}`. During review we decided to **drop the envelope entirely** for three reasons:

1. **It re-encodes every payload in base64 inside JSON**, adding ~33% size overhead to every message on every topic, even when signing is disabled.
2. **It breaks all plain-CBOR consumers** — any consumer reading a topic directly (Kafka UI, `taktx-client`, future Cassandra ingesters) now receives garbled bytes instead of CBOR.
3. **Signing is a per-message concern, not a per-topic serde concern.** Kafka headers are the correct place for it — the value payload stays completely untouched.

Starting from `main` means zero cleanup work. All new code is purely additive.

---

## 3. Kafka Record Anatomy

A Kafka record has **three** parts, not two:

```
┌──────────────────────────────────────────────────────────────┐
│  Kafka Record                                                │
│                                                              │
│  KEY      bytes — e.g. UUID (process instance ID)            │
│                   determines partition assignment            │
│                                                              │
│  VALUE    bytes — serialised payload (plain CBOR)            │
│                   e.g. StartCommandDTO — unchanged           │
│                                                              │
│  HEADERS  list of name → bytes pairs  (optional metadata)    │
│                                                              │
│    X-TaktX-Authorization  →  RS256 JWT as UTF-8 bytes        │
│        present on console-originated commands only           │
│                                                              │
│    X-TaktX-Signature      →  "keyId.base64(sig)" as UTF-8    │
│        present on engine-internal commands only              │
└──────────────────────────────────────────────────────────────┘
```

**Headers** have been part of the Kafka protocol since version 0.11 (2017). They travel alongside the record value without touching it. They are already used in this codebase: `ExternalTaskTriggerDTO.getHeaders()` is populated from Kafka record headers inside the engine, and `HeadersParameterResolver` in `taktx-client` reads them for job worker callbacks.

The CBOR value bytes are **never touched** by the signing mechanism. The wire format is identical to `main` today.

---

## 4. Target Architecture

```
Platform Service
  ├─ Holds RSA-2048 key pair (private key never leaves the service)
  └─ Exposes:  GET /api/public-key  →  Base64-DER RSA public key

Operator (once, at deploy time)
  └─ Fetches public key → sets TAKTX_PLATFORM_PUBLIC_KEY env var
     on all engine instances  (Kubernetes Secret / Docker secret)

─────────────────────────────────────────────────────────────────

User Action
  └─ HTTP request → Platform Service
       ├─ Validates Keycloak JWT
       ├─ Checks RBAC permissions
       └─ Mints short-lived RS256 JWT (5 min TTL):
            {
              iss:                 "taktx-platform-service",
              sub:                 "<userId>",
              action:              "START" | "CANCEL",
              processDefinitionId: "loan-approval",
              version:             3,
              namespaceId:         "<uuid>",
              auditId:             "<uuid>"  ← unique per user action
            }

       └─ HTTP response to Ingester includes header:
            X-TaktX-Authorization: <jwt>

Ingester
  └─ TaktXClient.startProcess(defId, version, vars, jwt)
       └─ ProcessInstanceProducer sends ProducerRecord:
            KEY:    processInstanceId (UUID)
            VALUE:  StartCommandDTO  (plain CBOR — unchanged from today)
            HEADER: X-TaktX-Authorization = <jwt as UTF-8 bytes>

─────────────────────────────────────────────────────────────────

Kafka: <namespace>.process-instance topic

Engine: ProcessInstanceProcessor.process(triggerRecord)
  │
  ├─ Extract header "X-TaktX-Authorization" from triggerRecord.headers()
  │
  ├─ [if taktx.security.authorization.enabled = true]
  │    ├─ EngineAuthorizationService.authorize(headers, trigger)
  │    │    ├─ AuthorizationTokenValidator.validate(jwt, platformPublicKey)
  │    │    │    ├─ verify RS256 signature
  │    │    │    ├─ check expiry
  │    │    │    └─ return TokenClaims
  │    │    ├─ match claims to command:
  │    │    │    START  → action=="START", processDefinitionId+version match
  │    │    │    CANCEL → action=="CANCEL"
  │    │    ├─ NonceStore.checkAndRecord(claims.auditId)  ← replay prevention
  │    │    └─ return auditId
  │    └─ store auditId in ProcessInstanceProcessingContext
  │
  └─ Execute command
       └─ all emitted InstanceUpdateDTO carry auditId from context

─────────────────────────────────────────────────────────────────

Engine-internal commands (timer fires, call activities, message starts)
  └─ No X-TaktX-Authorization header (engine-generated, not user-triggered)
  └─ [if taktx.security.signing.enabled = true]
       MessageSigningService.signIfEnabled(headers, payloadBytes)
         → adds header: X-TaktX-Signature = "<keyId>.<base64(Ed25519sig)>"

─────────────────────────────────────────────────────────────────

Engine startup
  └─ [if taktx.security.signing.enabled = true]
       Publishes own SigningKeyDTO (public key only) to:
         <namespace>.taktx-signing-keys  (compacted, key = engineKeyId)

─────────────────────────────────────────────────────────────────

Kafka: <namespace>.instance-update topic

Ingester (consuming instance-update events)
  └─ [if signing verification enabled]
       ├─ Reads engine public keys from <namespace>.taktx-signing-keys
       │    (AuthorizationTokenValidator + SigningKeyDTO from taktx-shared)
       ├─ Extracts X-TaktX-Signature header from each record
       └─ Ed25519Service.verify(payloadBytes, signature, enginePublicKey)
            → rejects or flags events with invalid/missing signatures
```

---

## 5. Design Decisions

### 5.1 Kafka headers for all signing metadata

Both the RS256 JWT and the Ed25519 signature travel as Kafka record headers, not in the value. Benefits:

- The CBOR value is identical to today — **zero wire format change**, no consumer breakage.
- Any consumer that does not care about signing simply ignores the headers.
- Kafka UI, `kafkacat`, and all existing tooling read messages normally.
- The `taktx-client` needs **no serialiser change** — it already produces plain CBOR.

### 5.2 Ed25519 for engine-internal signing, RS256 for Platform Service tokens

Two algorithms, two purposes, no conflict:

| | Ed25519 (engine-internal) | RS256 (Platform Service JWT) |
|---|---|---|
| **Who signs** | Engine instance | Platform Service |
| **What is signed** | Raw CBOR payload bytes | Structured JWT claims |
| **Frequency** | Every internally-generated message | Once per user action |
| **Signing speed** | ~50,000 ops/sec per core | ~3,000 ops/sec (17× slower) |
| **Signature size** | 64 bytes | 256 bytes |
| **Extra dependency** | None — JDK 15+ built-in | `jjwt 0.12.5` |

Using RS256 for engine-internal signing would create a 17× throughput reduction at the signing step and add ~300 bytes of JWT overhead per message with no benefit. Ed25519 is the right tool here. RS256 is the right tool for the structured identity claims in the JWT ecosystem.

### 5.3 `TAKTX_PLATFORM_PUBLIC_KEY` is the root of trust

The engine never unconditionally trusts a public key arriving over a Kafka topic. The operator-injected env var is the immutable root of trust. Key rotation via the config topic is possible but requires a rotation-proof JWT signed by the currently-trusted key (deferred to Phase 2).

### 5.4 `AuthorizationTokenValidator` and `TokenClaims` live in `taktx-shared`

The validation logic belongs in `taktx-shared` so it can be used by the Ingester (console team) and the engine without duplication. It is a plain Java class with a `@FunctionalInterface` key source — no CDI, no framework dependency. The platform team's existing copy can be replaced with the shared one.

### 5.5 `NonceStore` is in-memory (Caffeine), per engine instance

Kafka partition assignment guarantees that records with the same process instance UUID always land on the same engine consumer. A nonce stored locally is sufficient — no distributed store needed.

### 5.6 `auditId` on `InstanceUpdateDTO` — added to the abstract base

`auditId` is added as a `@Nullable String` field on the abstract `InstanceUpdateDTO` base class. Since there is no need to worry about migration compatibility, it is placed wherever it is most convenient in the serialised CBOR array — appended at the end. It is `null` for engine-internal commands and populated for all console-originated commands when authorization is enabled.

### 5.7 All security features default to disabled

A deployment with no new configuration behaves identically to `main` today.

---

## 6. What to Cherry-pick from `feature/identity-security-2`

The previous branch contains well-tested, correct classes that are reused as-is:

| Class | Module / Package | Notes |
|---|---|---|
| `Ed25519Service` | `taktx-shared / io.taktx.security` | JDK-native sign + verify, unit tested |
| `SigningKeyGenerator` | `taktx-shared / io.taktx.security` | Key pair generation, unit tested |
| `SigningKeyProvider` interface | `taktx-shared / io.taktx.security` | Abstraction for private key retrieval |
| `EnvironmentVariableKeyProvider` | `taktx-shared / io.taktx.security` | Reads `TAKTX_SIGNING_PRIVATE_KEY` env var |
| `SigningException` | `taktx-shared / io.taktx.security` | Runtime exception for crypto failures |
| `SigningKeyDTO` | `taktx-shared / io.taktx.dto` | Public key distribution DTO |
| `GlobalConfigurationDTO` | `taktx-shared / io.taktx.dto` | Cluster config (signing enabled, active key IDs) |
| `ConfigurationEventDTO` | `taktx-shared / io.taktx.dto` | Wrapper for config topic events |
| `LicenseDTO` | `taktx-shared / io.taktx.dto` | License feature flags (signing, RBAC, encryption) |
| `SignatureVerificationException` | `taktx-engine / io.taktx.engine.security` | Thrown on failed Ed25519 verify |

**Not cherry-picked** (replaced by the header approach):
`MessageEnvelope`, `MessageEnvelopeSerde`, `MessageEnvelopeSerializer`, `MessageEnvelopeDeserializer`, `SerializerSigningContext`, `DeserializerVerificationContext`, `SigningCallback`, `VerificationCallback`, `SigningContext`, `SigningKeySerde`, `SigningKeySerializer`, `SigningKeyDeserializer`, `MessageVerificationService`.

---

## 7. New Code to Build

### 7.1 `taktx-shared` — new classes

#### `io.taktx.dto.TokenClaims`
Plain Java bean (not serialised to Kafka). Fields:
- `String userId` — from JWT `sub`
- `String issuer` — from JWT `iss`
- `String action` — `"START"` or `"CANCEL"`
- `String processDefinitionId`
- `int version`
- `UUID namespaceId`
- `String auditId` — unique per user action, used for replay prevention
- `Instant issuedAt`, `Instant expiresAt`

#### `io.taktx.security.AuthorizationTokenException`
`RuntimeException`. Constructors: `(String message)` and `(String message, Throwable cause)`.

#### `io.taktx.security.PublicKeySource`
```java
@FunctionalInterface
public interface PublicKeySource {
    PublicKey getKey(String issuer) throws AuthorizationTokenException;
}
```

#### `io.taktx.security.AuthorizationTokenValidator`
Framework-agnostic, pure Java. Constructor takes `PublicKeySource`.
Single method: `TokenClaims validate(String rawJwt)` — uses `jjwt 0.12.5`:
1. Peek at unsigned `iss` claim to route to the correct key via `PublicKeySource`.
2. `Jwts.parser().verifyWith(key).parseSignedClaims(token)` — validates RS256 signature + expiry.
3. Map JWT claims to `TokenClaims`.
4. `ExpiredJwtException` → `AuthorizationTokenException("Token expired")`.
5. `JwtException` → `AuthorizationTokenException("Invalid token: ...")`.

Unit-tested with a generated RSA key pair — no mocking needed.

#### `InstanceUpdateDTO` — add `auditId`
Add `@Nullable private String auditId` to the existing abstract base.
Lombok `@Getter` / `@Setter` are already on the class. Null by default — no constructor change needed.

### 7.2 `taktx-shared` — additions to existing classes

#### `Topics` enum — two new entries
```java
CONFIGURATION_TOPIC("taktx-configuration", true, CleanupPolicy.COMPACT),
SIGNING_KEYS_TOPIC("taktx-signing-keys",   true, CleanupPolicy.COMPACT),
```
`initialAvailable = true` means the engine expects these topics to exist at startup.

### 7.3 `taktx-engine` — new classes

#### `io.taktx.engine.security.PublicKeyProvider`
`@ApplicationScoped`. On `@PostConstruct`:
- If `taktx.security.authorization.enabled = false` → skip silently.
- If enabled and `TAKTX_PLATFORM_PUBLIC_KEY` present → parse Base64-DER via `X509EncodedKeySpec` + `KeyFactory("RSA")`, store as `volatile PublicKey`.
- If enabled and key absent → throw at startup: *"TAKTX_PLATFORM_PUBLIC_KEY must be set when authorization is enabled"*.

Exposes `PublicKey getPlatformKey()` and `boolean isReady()`.

#### `io.taktx.engine.security.NonceStore`
`@ApplicationScoped`. Caffeine cache with 10-minute TTL and 100k max entries.
```java
/** Returns false if auditId was already seen (replay attack). */
boolean checkAndRecord(String auditId)
```

#### `io.taktx.engine.security.MessageSigningService`
Cherry-picked from `feature/identity-security-2`, with one change: output goes to a Kafka `Headers` object instead of a body wrapper.
```java
/** Adds X-TaktX-Signature header to headers if signing is enabled. */
void signIfEnabled(Headers headers, byte[] payloadBytes)
```
Header value format: `"<keyId>.<base64(Ed25519 signature)>"` as UTF-8 bytes.

#### `io.taktx.engine.security.EngineAuthorizationService`
`@ApplicationScoped`. Wires together `PublicKeyProvider`, `AuthorizationTokenValidator`, `NonceStore`, and `TaktConfiguration`.
```java
/**
 * Validates the X-TaktX-Authorization header and returns the auditId.
 * Returns null if authorization is disabled.
 * Throws AuthorizationTokenException if validation fails.
 */
@Nullable String authorize(Headers headers, ProcessInstanceTriggerDTO trigger)
```
Logic:
1. If `taktx.security.authorization.enabled = false` → return `null`.
2. Read `X-TaktX-Authorization` header → raw JWT string.
3. If absent → throw `AuthorizationTokenException("Missing X-TaktX-Authorization header")`.
4. `validator.validate(jwt)` → `TokenClaims`.
5. Match claims to trigger:
   - `StartCommandDTO` → `action == "START"`, `processDefinitionId` and `version` must match.
   - `AbortTriggerDTO` → `action == "CANCEL"`.
6. `nonceStore.checkAndRecord(claims.getAuditId())` → throw `AuthorizationTokenException("Replayed auditId")` if false.
7. Return `claims.getAuditId()`.

### 7.4 `taktx-engine` — changes to existing classes

#### `Stores` enum — two new entries
```java
GLOBAL_CONFIGURATION(Topics.CONFIGURATION_TOPIC.getTopicName()),
SIGNING_KEYS(Topics.SIGNING_KEYS_TOPIC.getTopicName()),
```

#### `TopologyProducer` — two new global tables
```java
// Global configuration — single compacted key "config"
builder.globalTable(
    taktConfiguration.getPrefixed(Topics.CONFIGURATION_TOPIC.getTopicName()),
    Materialized.<String, ConfigurationEventDTO>as(
            keyValueStoreSupplier.get(Stores.GLOBAL_CONFIGURATION))
        .withKeySerde(Serdes.String())
        .withValueSerde(new ObjectMapperSerde<>(ConfigurationEventDTO.class)));

// Signing keys — one compacted entry per keyId
builder.globalTable(
    taktConfiguration.getPrefixed(Topics.SIGNING_KEYS_TOPIC.getTopicName()),
    Materialized.<String, SigningKeyDTO>as(
            keyValueStoreSupplier.get(Stores.SIGNING_KEYS))
        .withKeySerde(Serdes.String())
        .withValueSerde(new ObjectMapperSerde<>(SigningKeyDTO.class)));
```
`MessageSigningService` and `EngineAuthorizationService` are added as constructor-injected fields.
`SigningContext` is initialised in `buildTopology()` for use by `MessageSigningService`.

#### `TaktConfiguration` — new config properties
```java
@ConfigProperty(name = "taktx.security.authorization.enabled", defaultValue = "false")
boolean authorizationEnabled;

@ConfigProperty(name = "taktx.security.authorization.reject-expired", defaultValue = "false")
boolean rejectExpiredTokens;

@ConfigProperty(name = "taktx.security.authorization.nonce-check.enabled", defaultValue = "true")
boolean nonceCheckEnabled;

@ConfigProperty(name = "taktx.security.signing.enabled", defaultValue = "false")
boolean signingEnabled;

@ConfigProperty(name = "taktx.platform.public-key")
Optional<String> platformPublicKeyBase64;
```

#### `application.properties` — new entries
```properties
# Authorization: validate X-TaktX-Authorization JWT header on incoming commands
# Enable when running alongside the TaktX Console (Platform Service + Ingester)
taktx.security.authorization.enabled=${TAKTX_SECURITY_AUTHORIZATION_ENABLED:false}
taktx.security.authorization.reject-expired=${TAKTX_SECURITY_REJECT_EXPIRED:false}
taktx.security.authorization.nonce-check.enabled=${TAKTX_SECURITY_NONCE_CHECK:true}

# RS256 public key of the Platform Service (Base64-DER, no PEM headers).
# REQUIRED when taktx.security.authorization.enabled=true.
# Obtain from: GET http://<platform-service>/api/public-key
# Inject as a secret — never hardcode in source or docker-compose.yaml.
# TAKTX_PLATFORM_PUBLIC_KEY=MIIBIjAN...

# Engine signing: sign internally-generated messages with Ed25519
taktx.security.signing.enabled=${TAKTX_SECURITY_SIGNING_ENABLED:false}
```

Also extend `quarkus.kafka-streams.topics` to include the two new compacted topics:
```properties
quarkus.kafka-streams.topics=...,\
  ${taktx.engine.namespace}.taktx-configuration,\
  ${taktx.engine.namespace}.taktx-signing-keys
```

#### `ProcessInstanceProcessor` — authorization hook
At the top of the `try` block in `process()`:
```java
String auditId = engineAuthorizationService.authorize(
    triggerRecord.headers(), trigger);
// auditId is null when authorization is disabled — always safe to store
processInstanceProcessingContextThreadLocal.get().setAuditId(auditId);
```
For internally-generated commands forwarded via `context.forward(...)`, `MessageSigningService.signIfEnabled(headers, payloadBytes)` is called before forwarding when `taktx.security.signing.enabled = true`.

#### `ProcessInstanceProcessingContext` — add `auditId`
Add `@Nullable String auditId` field with getter/setter.

#### `Forwarder` — propagate `auditId`
Read `auditId` from `ProcessInstanceProcessingContext` and set it on every emitted `InstanceUpdateDTO` subtype.

### 7.5 `taktx-client` — new overloads only

No serialiser change needed. The client already produces plain CBOR, which is exactly what the engine expects.

#### `ProcessInstanceProducer` — new overloads
```java
public UUID startProcess(String processDefinitionId, int version,
                         VariablesDTO variables, @Nullable String authorizationToken)

public void abortElementInstance(UUID processInstanceId,
                                 List<Long> elementInstanceIdPath,
                                 @Nullable String authorizationToken)
```
When `authorizationToken` is not blank:
```java
record.headers().add("X-TaktX-Authorization",
    authorizationToken.getBytes(StandardCharsets.UTF_8));
```
Existing overloads without `authorizationToken` delegate to the new ones with `null` — **no behavior change** for existing callers.

#### `TaktXClient` — expose the same new overloads
Existing methods unchanged.

---

## 8. Dependencies to Add

### `gradle/libs.versions.toml`
```toml
[versions]
jjwt     = "0.12.5"
caffeine = "3.1.8"

[libraries]
jjwt-api     = { module = "io.jsonwebtoken:jjwt-api",     version.ref = "jjwt" }
jjwt-impl    = { module = "io.jsonwebtoken:jjwt-impl",    version.ref = "jjwt" }
jjwt-jackson = { module = "io.jsonwebtoken:jjwt-jackson", version.ref = "jjwt" }
caffeine     = { module = "com.github.ben-manes.caffeine:caffeine", version.ref = "caffeine" }
```

### `taktx-shared/build.gradle.kts`
```kotlin
api(libs.jjwt.api)           // consumers get the API transitively
runtimeOnly(libs.jjwt.impl)
runtimeOnly(libs.jjwt.jackson)
```

### `taktx-engine/build.gradle.kts`
```kotlin
implementation(libs.caffeine)
```

---

## 9. What `taktx-shared` Offers the Platform Team

These classes in `taktx-shared` are intended for consumption by the Ingester and Platform Service as well as the engine. The platform team can take `taktx-shared` as a dependency instead of maintaining their own copies:

| Class | Purpose for the platform team |
|---|---|
| `TokenClaims` | Canonical parsed token model — consistent across Ingester and Engine |
| `AuthorizationTokenException` | Shared exception type — consistent error handling |
| `AuthorizationTokenValidator` | Framework-agnostic — works in Spring (Ingester) and Quarkus (Engine). Can replace the platform team's existing copy directly. |
| `PublicKeySource` | The key-lookup interface used by the validator — easy to wire into any DI framework |
| `Ed25519Service` | If the Ingester ever needs to verify engine-signed events on `instance-update` topic |
| `SigningKeyDTO` / `GlobalConfigurationDTO` / `ConfigurationEventDTO` / `LicenseDTO` | Shared config and signing model for any consumer of the config topic |

---

## 10. What the Console Team Needs to Do

All items below are **Phase 1 — required for the coordinated beta release**.

### Required — engine command authorization

| # | Where | Change |
|---|---|---|
| C1 | Ingester `DefinitionResource` | Forward the incoming `X-TaktX-Authorization` HTTP header: `taktClient.startProcess(defId, version, vars, authToken)` |
| C2 | Ingester `InstanceResource` | Forward header: `taktClient.abortElementInstance(instanceId, elementPath, authToken)` |

### Required — engine-signed event verification

| # | Where | Change |
|---|---|---|
| C3 | Ingester — new `EngineKeyRegistryConsumer` | On startup, consume the `<namespace>.taktx-signing-keys` compacted topic and build an in-memory map of `keyId → PublicKey`. Keep it up to date as new engine keys are published. Use `Ed25519Service` and `SigningKeyDTO` from `taktx-shared`. |
| C4 | Ingester — `InstanceUpdateConsumer` | For each record consumed from `<namespace>.instance-update`: extract the `X-TaktX-Signature` header, look up the public key by `keyId` prefix, call `Ed25519Service.verify(payloadBytes, sigBytes, publicKey)`. Reject or flag records that fail verification or are missing a signature when signing is enabled. |

### Required for key rotation — Phase 2, can be deferred

| # | Where | Change |
|---|---|---|
| C5 | Ingester startup | Publish Platform Service public key to `taktx-signing-keys` topic |
| C6 | Ingester | On Platform Service key change, republish with `X-TaktX-Rotation-Proof` header (JWT signed by current key, containing `newKeyFingerprint` claim) |

### Platform Service — no changes needed

The Platform Service already exposes `GET /api/public-key` and already mints RS256 JWTs. Nothing to change.

---

## 11. Implementation Sequence

| # | Task | Module | Size |
|---|---|---|---|
| 1 | Add `jjwt` + `caffeine` to `libs.versions.toml` and build files | build | S |
| 2 | Cherry-pick `Ed25519Service`, `SigningKeyGenerator`, `SigningKeyProvider`, `EnvironmentVariableKeyProvider`, `SigningException` | taktx-shared | S |
| 3 | Cherry-pick `SigningKeyDTO`, `GlobalConfigurationDTO`, `ConfigurationEventDTO`, `LicenseDTO` | taktx-shared | S |
| 4 | Add `TokenClaims`, `AuthorizationTokenException`, `PublicKeySource` | taktx-shared | S |
| 5 | Add `AuthorizationTokenValidator` + unit tests | taktx-shared | M |
| 6 | Add `auditId` field to `InstanceUpdateDTO` | taktx-shared | S |
| 7 | Add `CONFIGURATION_TOPIC` + `SIGNING_KEYS_TOPIC` to `Topics` | taktx-shared | S |
| 8 | Add security flags to `TaktConfiguration` + `application.properties` | taktx-engine | S |
| 9 | Add `GLOBAL_CONFIGURATION` + `SIGNING_KEYS` to `Stores` | taktx-engine | S |
| 10 | Add two `globalTable` setups to `TopologyProducer` | taktx-engine | S |
| 11 | Add `PublicKeyProvider` bean | taktx-engine | S |
| 12 | Add `NonceStore` bean | taktx-engine | S |
| 13 | Cherry-pick + adapt `MessageSigningService` (output to header instead of body wrapper) + publish engine public key to `taktx-signing-keys` on startup | taktx-engine | M |
| 14 | Add `EngineAuthorizationService` bean | taktx-engine | M |
| 15 | Wire `EngineAuthorizationService` into `ProcessInstanceProcessor` | taktx-engine | M |
| 16 | Add `auditId` to `ProcessInstanceProcessingContext`, propagate through `Forwarder` → `InstanceUpdateDTO` | taktx-engine | M |
| 17 | Add `authorizationToken` overloads to `ProcessInstanceProducer` + `TaktXClient` | taktx-client | S |
| 18 | Ingester: implement `EngineKeyRegistryConsumer` using `Ed25519Service` + `SigningKeyDTO` from `taktx-shared` | console/ingester | M |
| 19 | Ingester: wire signature verification into `InstanceUpdateConsumer` (C4) | console/ingester | M |
| 20 | Ingester: forward `X-TaktX-Authorization` header in `DefinitionResource` + `InstanceResource` (C1, C2) | console/ingester | S |
| 21 | Integration test: valid token → command executes, `auditId` present in events | taktx-engine | M |
| 22 | Integration test: authorization enabled, no token → command rejected | taktx-engine | S |
| 23 | Integration test: replayed `auditId` → command rejected | taktx-engine | S |
| 24 | Integration test: authorization disabled → command executes regardless of header | taktx-engine | S |
| 25 | Integration test: engine-signed event → Ingester verifies successfully | taktx-engine + console | M |
| 26 | Integration test: tampered engine event → Ingester rejects | taktx-engine + console | S |

> S = Small (< 1h) · M = Medium (2–4h)

---

## 12. Out of Scope — Phase 2

1. **Key rotation** — `PublicKeyProvider` will have a `rotateKey()` stub in Phase 1. Full rotation via the config topic with a rotation-proof header signed by the current key is deferred.
2. **`processInstanceId` binding on CANCEL tokens** — Phase 1 only checks `action == "CANCEL"`. Binding to a specific instance ID requires a coordinated Platform Service change.
3. **RBAC license flag enforcement** — `LicenseDTO.rbacAllowed` is present (cherry-picked) but not enforced. Deferred to a licensing sprint.

---

## 13. Resolved Decisions

All open questions from the review session have been answered and are reflected in this plan:

| # | Question | Decision |
|---|---|---|
| 1 | `auditId` placement in `InstanceUpdateDTO` CBOR array | No migration concern — append at the end of the abstract base. `null` for internal commands. |
| 2 | `jjwt` version alignment with console team | Confirmed: console team uses `jjwt 0.12.5`. Declaring it `api`-scoped in `taktx-shared` is safe. |
| 3 | Namespace prefix on `taktx-configuration` and `taktx-signing-keys` topics | **Namespaced** — consistent with all other topics. One signing key set per namespace. |
| 4 | Ed25519 engine signing — Phase 1 or Phase 2 | **Phase 1.** The Ingester will verify engine-signed events as part of this coordinated beta release. `MessageSigningService` and Ingester `EngineKeyRegistryConsumer` + `InstanceUpdateConsumer` verification are all in scope. |









