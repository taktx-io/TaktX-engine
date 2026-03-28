# TaktX — Security & Trust Chain Reference

**Last updated:** March 26, 2026
**Status:** Fully implemented — all features described here are live in the current codebase

---

## Contents

1. [Architecture overview](#architecture-overview)
2. [Ed25519 message signing](#ed25519-message-signing)
3. [RS256 JWT command authorization](#rs256-jwt-command-authorization)
4. [Signing keys topic](#signing-keys-topic)
5. [Root trust chain — anchored mode](#root-trust-chain--anchored-mode)
6. [Configuration topic](#configuration-topic)
7. [Trust metadata on instance updates](#trust-metadata-on-instance-updates)
8. [Environment variable reference](#environment-variable-reference)
9. [Key generation quick reference](#key-generation-quick-reference)
10. [Operational runbook](#operational-runbook)
11. [Migration notes](#migration-notes)

---

## Architecture overview

TaktX has **two orthogonal security mechanisms** that can be enabled or disabled independently at runtime via the `taktx-configuration` topic:

| Mechanism | Controlled by | Applies to | Always enforced? |
|---|---|---|---|
| **Ed25519 message signing** | `signingEnabled` | Engine outbound records, worker responses, engine-internal continuations | No — opt-in per config topic |
| **RS256 JWT command authorization** | `engineRequiresAuthorization` | `StartCommandDTO`, `AbortTriggerDTO` only (entry commands) | No — opt-in per config topic |

Both mechanisms share a single trust registry: the compacted Kafka topic **`taktx-signing-keys`**.

Two trust enforcement policies are available and are selected automatically at engine startup:

| Policy | Activated when | Enforcement |
|---|---|---|
| `OpenKeyTrustPolicy` | `TAKTX_PLATFORM_PUBLIC_KEY` is absent | Declared key role accepted at face value. Security boundary is Kafka ACLs. |
| `AnchoredKeyTrustPolicy` | `TAKTX_PLATFORM_PUBLIC_KEY` is set | Every key in `taktx-signing-keys` must carry a cryptographic countersignature from the platform root RSA key. |

These two policies are referred to throughout this document as **community mode** and **anchored mode** respectively.

### Safe defaults

When no `"config"` record has been received yet from the configuration topic, the engine defaults to:

```
signingEnabled              = false
engineRequiresAuthorization = false
```

BPMN processing runs normally on a fresh deployment with no configuration record. Both mechanisms must be explicitly enabled by publishing a `GlobalConfigurationDTO`.

---

## Ed25519 message signing

### What is signed

When `signingEnabled=true`, the engine signs its outbound records:

- `instance-update` records
- external-task trigger records
- engine-internal `process-instance` trigger records (continuations, timer wake-ups, etc.)

Inbound non-entry `ProcessInstanceTriggerDTO` records (worker responses, engine-internal continuations) are **verified** against the signing-keys topic when a matching key is found.

### Engine signing identity sources

The engine resolves its active signing identity from the configured source, set via `TAKTX_SIGNING_IDENTITY_SOURCE`:

| Value | Behaviour | Suitable for |
|---|---|---|
| `generated` (default when unset) | Generates a fresh Ed25519 key pair at startup; key ID is `engine-<uuid>` | Local development, CI |
| `env` | Reads from `TAKTX_SIGNING_PRIVATE_KEY` / `TAKTX_SIGNING_PUBLIC_KEY` / `TAKTX_SIGNING_KEY_ID` | Kubernetes Secrets, environment-injected secrets |
| `file` | Reads from files at paths in `TAKTX_SIGNING_FILE_*_PATH`; re-reads on each poll cycle (default: 1 s) | Docker Compose bind mounts, live key rotation |

Once an identity is available the engine publishes its public key to `taktx-signing-keys` and retries until the topic is available.

> **Note:** In **anchored mode**, `generated` is incompatible because the key changes on every restart and cannot be pre-signed. Use `file` or `env` instead.

### Worker signing identity sources

Workers (client applications) use the same source types. The client builder auto-detects the source:

1. If `taktx.signing.identity-source` (or `TAKTX_SIGNING_IDENTITY_SOURCE`) is set, that source is used explicitly.
2. Otherwise the builder checks `TAKTX_SIGNING_PRIVATE_KEY` in the environment; if found, `env` is used.
3. If no key material is found anywhere, the client falls back to a `generated` key.

### File-based signing — live key rotation

The `file` source polls the key files at the configured interval (default: 1 000 ms). Write key updates atomically (write to a temp file, then `mv`) to avoid partial reads:

```bash
cp new-private-key.b64 /opt/taktx/signing/engine/private-key.b64.tmp
mv -f /opt/taktx/signing/engine/private-key.b64.tmp /opt/taktx/signing/engine/private-key.b64
# same for public-key.b64 and key-id
```

The engine detects the change within one poll interval and publishes the new public key to `taktx-signing-keys`. The old key is retired (set to `TRUSTED` status) by `MessageSigningService.retirePreviousKey()` automatically.

### Signing gate

The engine only signs a record when **all** of the following are true:

1. `GlobalConfigurationDTO.signingEnabled == true`.
2. The engine public key has already been successfully published to `taktx-signing-keys`.

If any condition is false, the record is produced without a signature. Unsigned records are always accepted when `signingEnabled=false`.

### Signature format

The `X-TaktX-Signature` header value is a compound string:

```
<keyId>.<base64-Ed25519-signature>
```

The signature is over the raw serialized record bytes (CBOR). The algorithm is always Ed25519.

---

## RS256 JWT command authorization

### Scope

JWT authorization applies **only** to entry commands:

- `StartCommandDTO`
- `AbortTriggerDTO`

It is **not** required for non-entry commands (`ExternalTaskResponseTriggerDTO`, `SetVariableTriggerDTO`, `ContinueFlowElementTriggerDTO`, etc.). Those are governed by the Ed25519 signing gate instead.

### JWT transport

The JWT is attached in the `X-TaktX-Authorization` Kafka record header as a compact JWT string.

### Validation steps (when `engineRequiresAuthorization=true`)

1. JWT signature — verified against the RSA public key fetched from `taktx-signing-keys` by `kid`
2. Required claims — `sub`, `exp`, `auditId`, and command-specific action/scope claims
3. Token expiry (`exp`) — **always enforced**, even if authorization is disabled
4. Command-to-claim match — the JWT's declared action must match the inbound command
5. Replay protection via `auditId` nonce — **always enforced**

### Key lookup

The JWT `kid` header is used as a lookup key in the `taktx-signing-keys` KTable to retrieve the RSA public key. The engine does not maintain a static configured `signingKeyId`; all RSA keys are looked up dynamically from Kafka state.

### Publishing a JWT verification key (platform / ingester)

```java
// Publish once; compaction keeps the latest entry. Re-publish on rotation.
TaktXClient.publishSigningKey(
    props,
    "platform-key-2026-03",  // must match JWT kid exactly
    rsaPublicKeyBase64,       // X.509 DER, base64-encoded
    "platform",
    "RSA");
```

In anchored mode, include the countersignature:

```java
TaktXClient.publishSigningKey(
    props,
    "platform-key-2026-03",
    rsaPublicKeyBase64,
    "platform",
    "RSA",
    KeyRole.PLATFORM,
    registrationSignature);
```

### JWT requirements summary

| Field | Requirement |
|---|---|
| `kid` header | Must match a published RSA key in `taktx-signing-keys` |
| `exp` | Must be in the future — **always enforced** |
| `auditId` | Must be unique across all received commands — **always enforced** |
| Algorithm | RS256 |
| Action claims | Must match the inbound command type |

---

## Signing keys topic

The compacted Kafka topic `<tenantId>.<namespace>.taktx-signing-keys` stores `SigningKeyDTO` records serialized as CBOR.

### Record format

| Field | Type | Description |
|---|---|---|
| `keyId` | `String` | Unique identifier. Kafka record key. |
| `publicKeyBase64` | `String` | Base64-encoded X.509 DER public key |
| `algorithm` | `String` | `Ed25519` or `RSA` |
| `owner` | `String` | Human-readable label (e.g. `"engine"`, `"billing-worker"`, `"platform"`) |
| `role` | `KeyRole` | `CLIENT`, `ENGINE`, or `PLATFORM` |
| `status` | `KeyStatus` | `TRUSTED` or `REVOKED` |
| `registrationSignature` | `String?` | Base64-encoded RSA/SHA-256 countersignature (anchored mode only) |
| `publishedAt` | `Instant` | Timestamp of last publish |

### Role hierarchy

```
PLATFORM ⊇ ENGINE ⊇ CLIENT
```

A key with `PLATFORM` role can satisfy any required role. `ENGINE` satisfies `ENGINE` and `CLIENT`. `CLIENT` only satisfies `CLIENT`.

### Key revocation

Publish a new record with the same `keyId` and `status=REVOKED`. The engine and all watching clients pick up the change within one poll cycle (≤ 1 s).

### Workers and the signing-keys store

When `TaktXClient.start()` is called, a `SigningKeysStore` is initialised that reads the entire `taktx-signing-keys` topic to end-of-topic before accepting signed trigger records. This guarantees the engine key is present before the first trigger arrives. A missing key ID after that point is always a security violation, never a race condition.

---

## Root trust chain — anchored mode

### Overview

In **community mode** (default), any non-revoked key whose declared role is sufficient is trusted. The security perimeter is entirely Kafka ACLs.

In **anchored mode**, every key entry in `taktx-signing-keys` — regardless of role — must carry a `registrationSignature` that is an RSA/SHA-256 (PKCS#1 v1.5) signature produced by the platform root private key. A key without a valid countersignature is rejected at the trust gate even if its declared role is correct.

Anchored mode is activated automatically when `TAKTX_PLATFORM_PUBLIC_KEY` is set on the engine.

### Canonical payload

The payload that the platform root key signs is a pipe-delimited UTF-8 string:

```
keyId|publicKeyBase64|algorithm|owner|role
```

Java reference: `SigningKeyRegistrar.computeCanonicalPayload(SigningKeyDTO)`

Shell equivalent:

```bash
PAYLOAD=$(printf '%s|%s|%s|%s|%s' \
  "$KEY_ID" "$PUBLIC_KEY_B64" "Ed25519" "$OWNER" "$ROLE")

REGISTRATION_SIGNATURE=$(printf '%s' "$PAYLOAD" \
  | openssl dgst -sha256 -sign docker/signing/platform-private.pem \
  | base64 | tr -d '\n')
```

Where `$ROLE` is the `KeyRole` enum name: `ENGINE`, `CLIENT`, or `PLATFORM`.

### Platform root key format

The platform root key is an RSA 2048-bit key pair. The public key is distributed as a base64-encoded DER X.509 `SubjectPublicKeyInfo` — the value of `TAKTX_PLATFORM_PUBLIC_KEY`.

The private key (`platform-private.pem`) is only needed when generating new registration signatures. It must **never** be committed to version control or mounted into any container.

### Registration signatures per participant

| Participant | Env var carrying signature | Role | Who generates |
|---|---|---|---|
| Engine | `TAKTX_ENGINE_KEY_REGISTRATION_SIGNATURE` | `ENGINE` | Operator, via `generate_trust_anchor.sh --sign` |
| Worker / client | `TAKTX_SIGNING_REGISTRATION_SIGNATURE` | `CLIENT` | Operator or CI, via `generate_trust_anchor.sh --sign` |
| Platform JWT key | passed to `publishSigningKey(...)` | `PLATFORM` or `CLIENT` | Platform team |

### Enforcement rules (`AnchoredKeyTrustPolicy`)

A key is trusted if and only if **all** of the following are true:

1. Key is not `null`
2. Key status is not `REVOKED`
3. `registrationSignature` is non-null and non-blank
4. The signature verifies with `SHA256withRSA` against the canonical payload using the platform root public key
5. The key's declared role satisfies the required role (same as `OpenKeyTrustPolicy`)

### Incompatibility with `generated` signing source

When anchored mode is active and `TAKTX_SIGNING_IDENTITY_SOURCE=generated`, the engine logs a startup warning because the engine's own key will fail the countersignature check — a generated key changes on every restart and cannot be pre-signed.

```
⚠️  Anchored mode is active (TAKTX_PLATFORM_PUBLIC_KEY is set) but
    TAKTX_SIGNING_IDENTITY_SOURCE=generated and
    TAKTX_ENGINE_KEY_REGISTRATION_SIGNATURE is blank.
```

Switch to `file` or `env` and supply `TAKTX_ENGINE_KEY_REGISTRATION_SIGNATURE`.

### Step-by-step: activating anchored mode

```bash
# Step 1 — Generate the platform root key pair (once per deployment)
scripts/generate_trust_anchor.sh --init
# Outputs:
#   docker/signing/platform-private.pem   ← KEEP SECRET — never commit or mount
#   docker/signing/platform-public.b64    ← TAKTX_PLATFORM_PUBLIC_KEY

# Step 2 — Generate engine Ed25519 key files (if not done yet)
openssl genpkey -algorithm Ed25519 -out /tmp/engine-key.pem
openssl pkey -in /tmp/engine-key.pem -outform DER \
  | base64 | tr -d '\n' > docker/signing/engine/private-key.b64
openssl pkey -in /tmp/engine-key.pem -pubout -outform DER \
  | base64 | tr -d '\n' > docker/signing/engine/public-key.b64
echo "engine-prod-1" > docker/signing/engine/key-id
rm /tmp/engine-key.pem

# Step 3 — Sign the engine key
scripts/generate_trust_anchor.sh --sign \
  --key-dir docker/signing/engine \
  --owner engine \
  --role ENGINE
# → copy TAKTX_ENGINE_KEY_REGISTRATION_SIGNATURE=... into engine environment

# Step 4 — Sign each worker key (repeat per worker)
scripts/generate_trust_anchor.sh --sign \
  --key-dir docker/signing/worker-billing \
  --owner billing-worker \
  --role CLIENT
# → copy TAKTX_SIGNING_REGISTRATION_SIGNATURE=... into worker environment

# Step 5 — Set TAKTX_PLATFORM_PUBLIC_KEY on the engine
TAKTX_PLATFORM_PUBLIC_KEY=$(cat docker/signing/platform-public.b64)
```

### Key rotation in anchored mode

1. Generate new key files (same openssl commands as above, new key ID).
2. Re-sign with `generate_trust_anchor.sh --sign`.
3. Update `TAKTX_ENGINE_KEY_REGISTRATION_SIGNATURE` or `TAKTX_SIGNING_REGISTRATION_SIGNATURE`.
4. Restart the engine or worker. The old key is retired automatically.

### Deploying to Kubernetes

```yaml
# engine-secret.yaml
apiVersion: v1
kind: Secret
metadata:
  name: taktx-engine-secrets
stringData:
  TAKTX_PLATFORM_PUBLIC_KEY: "<base64 DER RSA public key from platform-public.b64>"
  TAKTX_ENGINE_KEY_REGISTRATION_SIGNATURE: "<signature from generate_trust_anchor.sh --sign>"
  TAKTX_SIGNING_FILE_KEY_ID_PATH: "/opt/taktx/signing/engine/key-id"
  TAKTX_SIGNING_FILE_PRIVATE_KEY_PATH: "/opt/taktx/signing/engine/private-key.b64"
  TAKTX_SIGNING_FILE_PUBLIC_KEY_PATH: "/opt/taktx/signing/engine/public-key.b64"

# worker-secret.yaml
apiVersion: v1
kind: Secret
metadata:
  name: taktx-worker-secrets
stringData:
  TAKTX_SIGNING_REGISTRATION_SIGNATURE: "<worker signature from generate_trust_anchor.sh --sign>"
  TAKTX_SIGNING_PRIVATE_KEY: "<base64 Ed25519 PKCS#8 private key>"
  TAKTX_SIGNING_PUBLIC_KEY: "<base64 Ed25519 X.509 public key>"
  TAKTX_SIGNING_KEY_ID: "billing-worker-prod-1"
```

Mount the engine key files via a `Secret` volume and set the path env vars accordingly.

---

## Configuration topic

The compacted topic `<tenantId>.<namespace>.taktx-configuration` carries `ConfigurationEventDTO` records under Kafka key `"config"`.

### `GlobalConfigurationDTO` fields

| Field | Type | Default | Meaning |
|---|---|---|---|
| `signingEnabled` | `boolean` | `false` | Enables Ed25519 signing of outbound engine records and verification of inbound non-entry commands |
| `engineRequiresAuthorization` | `boolean` | `false` | Enables RS256 JWT authorization for entry commands (`StartCommandDTO`, `AbortTriggerDTO`) |
| `rbacEnabled` | `boolean` | `false` | Reserved for future use |
| `trustedKeyIds` | `List<String>` | `[]` | Reserved compatibility surface |

### Publishing runtime configuration

```java
GlobalConfigurationDTO cfg = GlobalConfigurationDTO.builder()
    .signingEnabled(true)
    .engineRequiresAuthorization(true)
    .build();

client.publishGlobalConfig(cfg);
// Or statically (no running client needed):
TaktXClient.publishGlobalConfig(props, cfg);
```

### Runtime behaviour

- The engine and all running clients watch this topic continuously.
- A change to `signingEnabled` takes effect within one Kafka poll cycle — no restart needed.
- Workers adapt: when `signingEnabled` is toggled on, the worker re-registers its signing function and (re-)publishes its public key if not already published.

### Intentionally removed runtime toggles

These are **not** configurable at runtime (they were removed):

| Removed field | Rationale |
|---|---|
| `signingKeyId` | Engine key IDs are generated per node and resolved dynamically |
| `rejectExpired` | Expired JWT rejection is always enforced |
| `nonceCheckEnabled` | Replay protection is always enforced |

---

## Trust metadata on instance updates

`InstanceUpdateDTO` (and `ProcessInstanceTriggerDTO`) expose two trust provenance fields:

| Field | Meaning |
|---|---|
| `currentTrustMetadata` | Trust data for the command currently being processed |
| `originTrustMetadata` | Trust data for the original command that started the chain |

### Examples

| Event | `current` | `origin` |
|---|---|---|
| External JWT start command | `JWT` | `JWT` |
| Worker-signed external task response | `worker` | `worker` |
| Timer continuation after worker response | `engine` | `worker` |
| Engine-internal follow-up after JWT start | `engine` | `JWT` |

The `origin` field makes it possible to show in a console/audit log who originally triggered a chain, even after engine-internal rescheduling has taken over.

> The legacy accessor `commandTrustMetadata` resolves to `currentTrustMetadata` for compatibility. New consumers should prefer the explicit `current`/`origin` fields.

---

## Environment variable reference

### Engine — required

| Variable | Purpose |
|---|---|
| `KAFKA_BOOTSTRAP_SERVERS` | Kafka bootstrap connection string |
| `TAKTX_ENGINE_TENANT_ID` | Tenant prefix for topics and Kafka Streams application IDs |
| `TAKTX_ENGINE_NAMESPACE` | Namespace prefix for topics and Kafka Streams application IDs |

### Engine — signing identity source

| Variable | Purpose | Default |
|---|---|---|
| `TAKTX_SIGNING_IDENTITY_SOURCE` | `generated`, `env`, or `file` | `generated` |
| `TAKTX_SIGNING_KEY_ID` | Key ID when source is `env` | — |
| `TAKTX_SIGNING_PRIVATE_KEY` | Base64 PKCS#8 DER Ed25519 private key when source is `env` | — |
| `TAKTX_SIGNING_PUBLIC_KEY` | Base64 X.509 DER Ed25519 public key when source is `env` | — |
| `TAKTX_SIGNING_FILE_KEY_ID_PATH` | Path to `key-id` file when source is `file` | — |
| `TAKTX_SIGNING_FILE_PRIVATE_KEY_PATH` | Path to `private-key.b64` file when source is `file` | — |
| `TAKTX_SIGNING_FILE_PUBLIC_KEY_PATH` | Path to `public-key.b64` file when source is `file` | — |
| `TAKTX_SIGNING_FILE_REFRESH_INTERVAL_MS` | How often to re-read key files in ms | `1000` |

### Engine — anchored mode

| Variable | Purpose |
|---|---|
| `TAKTX_PLATFORM_PUBLIC_KEY` | Base64 X.509 DER RSA public key. Setting this activates anchored mode. |
| `TAKTX_ENGINE_KEY_REGISTRATION_SIGNATURE` | Base64 RSA/SHA-256 countersignature for the engine's own key. Required in anchored mode when using `file` or `env` source. |

### Worker / client — signing

| Variable | Purpose | Default |
|---|---|---|
| `TAKTX_SIGNING_IDENTITY_SOURCE` | `env`, `file`, or `generated` | `env` (if key material present) |
| `TAKTX_SIGNING_KEY_ID` | Key ID when source is `env` | — |
| `TAKTX_SIGNING_PRIVATE_KEY` | Base64 PKCS#8 DER Ed25519 private key | — |
| `TAKTX_SIGNING_PUBLIC_KEY` | Base64 X.509 DER Ed25519 public key | — |
| `TAKTX_SIGNING_OWNER` | Human-readable owner label for the published key. Falls back to app name then key ID. | — |
| `TAKTX_SIGNING_FILE_KEY_ID_PATH` | Path to `key-id` file | — |
| `TAKTX_SIGNING_FILE_PRIVATE_KEY_PATH` | Path to `private-key.b64` file | — |
| `TAKTX_SIGNING_FILE_PUBLIC_KEY_PATH` | Path to `public-key.b64` file | — |
| `TAKTX_SIGNING_FILE_REFRESH_INTERVAL_MS` | File refresh interval in ms | `1000` |
| `TAKTX_SIGNING_REGISTRATION_SIGNATURE` | Base64 RSA/SHA-256 countersignature for the worker key. Required in anchored mode. | — |

### Kafka auth / TLS (engine and workers)

| Variable | Purpose |
|---|---|
| `KAFKA_SECURITY_PROTOCOL` | e.g. `SASL_SSL`, `PLAINTEXT` |
| `KAFKA_SASL_MECHANISM` | e.g. `SCRAM-SHA-512` |
| `KAFKA_SASL_JAAS_CONFIG` | Full JAAS config string |
| `KAFKA_SSL_TRUSTSTORE_LOCATION` | Path to JKS/PKCS12 truststore |
| `KAFKA_SSL_TRUSTSTORE_PASSWORD` | Truststore password |
| `KAFKA_SSL_TRUSTSTORE_TYPE` | `JKS` or `PKCS12` |
| `KAFKA_SSL_ENDPOINT_IDENTIFICATION` | Set to empty to disable hostname verification |

---

## Key generation quick reference

### Ed25519 key pair (engine or worker)

```bash
# Generate via temporary PEM — works on macOS and Linux
openssl genpkey -algorithm Ed25519 -out /tmp/taktx-key.pem

# Export private key (PKCS#8 DER, base64)
openssl pkey -in /tmp/taktx-key.pem -outform DER \
  | base64 | tr -d '\n' > private-key.b64

# Export public key (X.509 DER, base64)
openssl pkey -in /tmp/taktx-key.pem -pubout -outform DER \
  | base64 | tr -d '\n' > public-key.b64

echo "my-key-id-1" > key-id

rm /tmp/taktx-key.pem
```

### Platform RSA root key pair

```bash
scripts/generate_trust_anchor.sh --init
# Outputs:
#   docker/signing/platform-private.pem  ← KEEP SECRET
#   docker/signing/platform-public.b64   ← TAKTX_PLATFORM_PUBLIC_KEY value
```

### Registration signature for a key

```bash
scripts/generate_trust_anchor.sh --sign \
  --key-dir <path-to-key-dir> \
  --owner <owner-name> \
  --role <ENGINE|CLIENT>
# Prints the env var name and value to copy
```

### Show the platform public key

```bash
scripts/generate_trust_anchor.sh --show-pubkey
```

---

## Operational runbook

### Enable signing and authorization on a running cluster

```java
TaktXClient.publishGlobalConfig(props,
    GlobalConfigurationDTO.builder()
        .signingEnabled(true)
        .engineRequiresAuthorization(true)
        .build());
```

No engine restart needed. Workers adapt within one Kafka poll cycle.

### Disable signing temporarily

```java
TaktXClient.publishGlobalConfig(props,
    GlobalConfigurationDTO.builder()
        .signingEnabled(false)
        .engineRequiresAuthorization(false)
        .build());
```

### Revoke a compromised key

```java
client.publishSigningKey(
    compromisedKeyId,
    existingPublicKeyBase64,
    owner,
    "Ed25519",
    KeyRole.CLIENT,
    null);
// Then immediately publish the replacement key
```

Or publish a `SigningKeyDTO` with `status=REVOKED` directly.

### Rotate the engine signing key (file source)

1. Generate new key files (new `key-id` value).
2. In anchored mode: run `generate_trust_anchor.sh --sign` for the new key and update `TAKTX_ENGINE_KEY_REGISTRATION_SIGNATURE`.
3. Write new files atomically (`cp` + `mv`).
4. The engine picks up the change within the refresh interval and retires the old key automatically.
5. No restart required.

### Rotate the platform root key

1. Run `generate_trust_anchor.sh --init` (confirm the overwrite prompt).
2. Re-sign **all** engine and worker keys with the new platform private key.
3. Update `TAKTX_PLATFORM_PUBLIC_KEY` and all `*_REGISTRATION_SIGNATURE` environment variables.
4. Rolling-restart the engine and all workers.

---

## Migration notes

### From the old engine model

Platform teams and ingester owners should note the following changes:

| Old behaviour | New behaviour |
|---|---|
| Engine `signingKeyId` was a required env var | Engine key ID is generated automatically (`engine-<uuid>`) or read from `file`/`env` source |
| `TAKTX_SECURITY_SIGNING_ENABLED` env var | Removed. Use `taktx-configuration` topic (`signingEnabled`) |
| `TAKTX_SECURITY_AUTHORIZATION_ENABLED` env var | Removed. Use `taktx-configuration` topic (`engineRequiresAuthorization`) |
| `TAKTX_SECURITY_REJECT_EXPIRED` | Removed. Expired JWT rejection is always on. |
| `TAKTX_SECURITY_NONCE_CHECK` | Removed. Replay protection is always on. |
| JWT keys looked up by a fixed `signingKeyId` | JWT `kid` header drives key lookup directly from `taktx-signing-keys` |
| `commandTrustMetadata` on `InstanceUpdateDTO` | Still works (resolves to `currentTrustMetadata`). Prefer `currentTrustMetadata` + `originTrustMetadata`. |

### Stable platform contract

These are the runtime-configurable fields the platform team should send:

```java
GlobalConfigurationDTO.builder()
    .signingEnabled(true)          // ← use this
    .engineRequiresAuthorization(true) // ← use this
    // .signingKeyId(...)          ← removed, do not send
    // .rejectExpired(...)         ← removed, always enforced
    // .nonceCheckEnabled(...)     ← removed, always enforced
    .build();
```
