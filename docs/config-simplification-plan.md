# Configuration Simplification Plan

**Date:** March 16, 2026  
**Status:** In implementation  
**Scope:** `taktx-shared`, `taktx-engine`

---

## Contents

1. [Objective & Design Rule](#1-objective--design-rule)
2. [Target Environment Variable Model](#2-target-environment-variable-model)
3. [What Lives in the Config Topic](#3-what-lives-in-the-config-topic)
4. [Code Changes](#4-code-changes)
5. [Runtime Scenarios](#5-runtime-scenarios)
6. [Test Changes](#6-test-changes)
7. [What to Report Back to the Platform Team](#7-what-to-report-back-to-the-platform-team)

---

## 1. Objective & Design Rule

**One rule, applied consistently:**

| Category | Where it lives |
|---|---|
| Needed before the Kafka connection exists | env var |
| Must never appear in a Kafka topic (secret / trust anchor) | env var / secrets manager |
| Everything else (cluster-wide runtime behaviour) | `taktx-configuration` KTable |

This removes duplicated engine-side security toggles and eliminates injected Ed25519 engine signing keys entirely.
The engine now generates its own signing key pair in memory at startup.

### Design clarifications

- Each engine node generates its own Ed25519 `KeyPair` at startup.
- Each node also generates its own unique signing key ID: `engine-<uuid>`.
- The engine publishes its public key to `taktx-signing-keys` under that generated key ID.
- Public-key publication uses a retry loop until topics are available.
- The engine does **not** read a signing key ID from config or env anymore.
- JWT expiry checks are **always enforced**.
- JWT nonce replay checks are **always enforced**.

Because the signature header already carries the key ID, workers and other consumers can resolve the correct public key automatically from `taktx-signing-keys` without any preconfigured engine key identifier.

---

## 2. Target Environment Variable Model

### Required for every deployment

| Env var | Purpose |
|---|---|
| `KAFKA_BOOTSTRAP_SERVERS` | Kafka connection bootstrap |
| `TAKTX_ENGINE_TENANT_ID` | Topic/application namespace prefix |
| `TAKTX_ENGINE_NAMESPACE` | Topic/application namespace prefix |

### Secrets / trust anchors

| Env var | Purpose | Required when |
|---|---|---|
| `TAKTX_PLATFORM_PUBLIC_KEY` | Root-of-trust material for command authorization deployments | Authorization is enabled operationally |

### Kafka TLS / SASL

These remain environment-driven by definition:

- `KAFKA_SECURITY_PROTOCOL`
- `KAFKA_SASL_MECHANISM`
- `KAFKA_SASL_JAAS_CONFIG`
- `KAFKA_SSL_TRUSTSTORE_LOCATION`
- `KAFKA_SSL_TRUSTSTORE_PASSWORD`
- related broker-auth/bootstrap secrets

### Infrastructure / tuning

No change in this plan for existing deployment/infrastructure properties such as:

- topic partitions / replication
- license file location
- stream thread counts
- poll / commit intervals

### Removed entirely

| Removed env var | Why it is removed |
|---|---|
| `TAKTX_SECURITY_SIGNING_ENABLED` | Runtime cluster-wide flag now comes from config topic |
| `TAKTX_SECURITY_AUTHORIZATION_ENABLED` | Runtime cluster-wide flag now comes from config topic |
| `TAKTX_SECURITY_REJECT_EXPIRED` | Expired JWTs are always rejected |
| `TAKTX_SECURITY_NONCE_CHECK` | Replay protection is always enforced |

---

## 3. What Lives in the Config Topic

The compacted `taktx-configuration` topic (record key `"config"`) carries `GlobalConfigurationDTO`.

| Field | Type | Default | Description |
|---|---|---|---|
| `signingEnabled` | boolean | `false` | Master switch for outbound engine Ed25519 signing |
| `authorizationEnabled` | boolean | `false` | Master switch for RS256 JWT command authorization |
| `rbacEnabled` | boolean | `false` | Reserved for future use |
| `trustedKeyIds` | `List<String>` | `[]` | Reserved compatibility surface for broader trust/rotation workflows |

### Explicitly removed from the DTO

| Removed field | Why removed |
|---|---|
| `signingKeyId` | No longer functionally needed; engine signs with its own generated `engine-<uuid>` key ID |
| `rejectExpired` | Expiry enforcement is fixed behaviour, not runtime config |
| `nonceCheckEnabled` | Replay protection is fixed behaviour, not runtime config |

### Safe-default behaviour when no config record exists yet

- `signingEnabled = false`
- `authorizationEnabled = false`
- `rbacEnabled = false`
- `trustedKeyIds = []`

So a new deployment with no config record behaves like today: BPMN processing runs normally with signing and JWT authorization inactive.

---

## 4. Code Changes

### 4.1 `taktx-shared` — `GlobalConfigurationDTO`

**File:** `taktx-shared/src/main/java/io/taktx/dto/GlobalConfigurationDTO.java`

Changes:

- add `authorizationEnabled`
- remove `signingKeyId`
- do **not** add `rejectExpired`
- do **not** add `nonceCheckEnabled`

---

### 4.2 `taktx-engine` — `MessageSigningService`

**File:** `taktx-engine/src/main/java/io/taktx/engine/security/MessageSigningService.java`

Changes:

- remove env/system-property key loading for engine signing
- generate Ed25519 `KeyPair` at startup via `SigningKeyGenerator.generate()`
- generate runtime key ID as `engine-<uuid>`
- keep the private key in memory only
- publish the public key to `taktx-signing-keys`
- retry publication until topics are available
- use `GlobalConfigStore.signingEnabled` as the runtime on/off switch
- keep the license check as a gate for actual signing

### Publication retry behaviour

`MessageSigningService` now owns public-key publication and retries until publication succeeds.
This avoids startup-order races where the signing-keys topic is not yet ready when the bean starts.

### Signing decision flow

`signToHeaderValue()` returns a signature only when all of the following are true:

1. event signing is licensed
2. `GlobalConfigurationDTO.signingEnabled == true`
3. the engine public key has already been published successfully

Otherwise it returns `null` and the record is sent unsigned.

---

### 4.3 `taktx-engine` — `EngineAuthorizationService`

**File:** `taktx-engine/src/main/java/io/taktx/engine/security/EngineAuthorizationService.java`

Changes:

- inject `GlobalConfigStore`
- use `GlobalConfigurationDTO.authorizationEnabled` as the only runtime authorization toggle
- remove config/property-based nonce toggle
- remove config/property-based expiry toggle
- remove the engine-key special case for Ed25519 authorization
- resolve all Ed25519 keys uniformly from the signing-keys KTable

### Fixed behaviour

- Expired JWTs are always rejected.
- Replay detection via `auditId` nonce store is always enforced.

---

### 4.4 `taktx-engine` — `TaktConfiguration`

**File:** `taktx-engine/src/main/java/io/taktx/engine/config/TaktConfiguration.java`

Remove obsolete properties:

- `authorizationEnabled`
- `rejectExpiredTokens`
- `nonceCheckEnabled`
- `signingEnabled`
- `signingKeyId`

Keep bootstrap, tenancy, topic, host, port, and infrastructure settings.

---

### 4.5 `taktx-engine` — `application.properties`

**File:** `taktx-engine/src/main/resources/application.properties`

Remove obsolete bindings for:

- `taktx.security.authorization.enabled`
- `taktx.security.authorization.reject-expired`
- `taktx.security.authorization.nonce-check.enabled`
- `taktx.security.signing.enabled`
- `taktx.signing.key-id`

Keep only secret/bootstrap-level material there.

---

### 4.6 `taktx-engine` — `EngineSigningKeyPublisher`

**File:** `taktx-engine/src/main/java/io/taktx/engine/security/EngineSigningKeyPublisher.java`

Delete this class.
Its responsibility is absorbed into `MessageSigningService`, which now owns both generated key material and retrying public-key publication.

---

### 4.7 `taktx-engine` — `TopicBootstrapper`

**File:** `taktx-engine/src/main/java/io/taktx/engine/topicmanagement/TopicBootstrapper.java`

Remove the dependency on `EngineSigningKeyPublisher`.
Topic creation remains unchanged; signing-key publication no longer depends on synchronous startup sequencing because `MessageSigningService` retries until the topic is ready.

---

### 4.8 `docker/docker-compose-full.yaml`

Simplify the `taktx` container environment to the bootstrap settings plus optional trust anchor.
All runtime signing/authorization switches now come from config-topic updates.

---

## 5. Runtime Scenarios

### Standalone engine, no console/platform

**Env:**

```bash
KAFKA_BOOTSTRAP_SERVERS=kafka:9094
TAKTX_ENGINE_TENANT_ID=acme
TAKTX_ENGINE_NAMESPACE=default
```

**Behaviour:**

1. engine starts and creates/verifies topics
2. engine resolves its signing identity from the configured source (default: generated `engine-<uuid>` key)
3. public-key publication retries until `taktx-signing-keys` is available
4. no config record means `authorizationEnabled=false` and `signingEnabled=false`
5. BPMN processing runs normally without JWT enforcement or outbound signing

If a config record is later pushed with `signingEnabled=true`, signing starts live once the key has been published and the license permits it.

### Full stack deployment

**Env:**

```bash
KAFKA_BOOTSTRAP_SERVERS=kafka:9094
TAKTX_ENGINE_TENANT_ID=acme
TAKTX_ENGINE_NAMESPACE=production
TAKTX_PLATFORM_PUBLIC_KEY=<base64-DER RSA public key>
```

**Behaviour:**

1. each node generates its own `engine-<uuid>` signing key ID
2. each node publishes its own public key to `taktx-signing-keys`
3. each node reads runtime flags from `taktx-configuration`
4. workers resolve whichever engine key ID appears in `X-TaktX-Signature`
5. config pushes can enable/disable signing or authorization without restart

### Important fixed rules

- JWT expiry is always enforced.
- JWT replay protection is always enforced.
- Engine signing key IDs are not platform-configured.

---

## 6. Test Changes

| Test area | Update |
|---|---|
| `MessageSigningServiceTest` | Use a prebuilt `KeyPair` test constructor instead of env/system-property key injection |
| `EngineAuthorizationServiceTest` | Drive authorization through `GlobalConfigStore.authorizationEnabled`; remove nonce-disable scenarios |
| `SecurityTestConfigResource` | Stop injecting engine Ed25519 keys; read runtime-generated key ID/public key from `MessageSigningService` |
| `SecurityIntegrationTest` | Publish `authorizationEnabled=true` and `signingEnabled=true` through config topic; assert against runtime engine key ID |
| `WorkerEndToEndTest` | Use the runtime-generated engine public key from the test resource |
| `SecurityEndToEndHelper` | Stop writing removed DTO fields |

---

## 7. What to Report Back to the Platform Team

### 1. Stable bootstrap env vars

The engine still uses:

- `TAKTX_ENGINE_TENANT_ID`
- `TAKTX_ENGINE_NAMESPACE`
- `KAFKA_BOOTSTRAP_SERVERS`

No change there.

### 2. Runtime config ownership

The platform/config topic owns runtime security switches.
The engine no longer accepts env vars for runtime signing or authorization toggles.

### 3. Removed toggles

The following are removed rather than moved:

- `rejectExpired`
- `nonceCheckEnabled`
- `signingKeyId`

Reason:

- expired JWT rejection must always happen
- replay protection must always happen
- engine key IDs are runtime-generated per node

### 4. DTO/schema change

`GlobalConfigurationDTO` now only needs:

- `signingEnabled`
- `authorizationEnabled`
- `rbacEnabled`
- `trustedKeyIds`

It no longer contains `signingKeyId`, `rejectExpired`, or `nonceCheckEnabled`.

### 5. Explicit security semantics

The engine behaviour is now intentionally fixed:

- **expired JWTs are always rejected**
- **nonce replay checks are always enforced**
- **engine signing keys are generated in-memory and never injected**
- **engine public-key publication retries until topics are available**

---

*End of plan*
