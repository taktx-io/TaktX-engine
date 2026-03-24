# TaktX Engine — Security Overview

**Last updated:** March 24, 2026  
**Status:** Implemented — all features described here are live in the current codebase

---

## Contents

1. [High-level model](#high-level-model)
2. [Command authorization](#command-authorization)
3. [Engine event signing](#engine-event-signing)
4. [Signing keys topic](#signing-keys-topic)
5. [Root trust chain (anchored mode)](#root-trust-chain-anchored-mode)
6. [Configuration topic](#configuration-topic)
7. [Environment variables](#environment-variables)
8. [Operational notes](#operational-notes)

---

## High-level model

TaktX has two separate security mechanisms:

1. **RS256 JWT command authorization**
   - Used for external entry commands: `StartCommandDTO` and `AbortTriggerDTO`.
   - Guarded at runtime by `GlobalConfigurationDTO.engineRequiresAuthorization`.
   - JWT expiry is always enforced.
   - JWT replay protection via `auditId` nonce tracking is always enforced.

2. **Ed25519 message signing**
   - Used for engine outbound records and signed non-entry process-instance commands such as worker responses and engine-internal continuations.
   - Guarded at runtime by `GlobalConfigurationDTO.signingEnabled`.
   - The engine uses a configurable signing-identity source; default is a generated in-memory key.
   - Workers use a configurable signing-identity source; default is env-based.

The shared trust registry is the compacted Kafka topic `taktx-signing-keys`.

---

## Command authorization

### Header

External entry commands are authorized through the `X-TaktX-Authorization` header.

This applies to:

- `StartCommandDTO`
- `AbortTriggerDTO`

### Validation steps

When `engineRequiresAuthorization=true` in the latest config-topic record, the engine validates JWTs for `StartCommandDTO` and `AbortTriggerDTO` only:

1. JWT signature
2. required claims
3. token expiry (`exp`) — **always enforced**
4. command-to-claim match
5. replay protection using `auditId` — **always enforced**

If authorization is disabled in the config topic, the engine accepts commands without JWT validation.

### Non-entry process-instance commands

Other `ProcessInstanceTriggerDTO` subclasses do **not** require JWT just because `engineRequiresAuthorization=true`.

Examples include:

- `SetVariableTriggerDTO`
- `ContinueFlowElementTriggerDTO`
- `ExternalTaskResponseTriggerDTO`
- engine-internal `StartCommandDTO` follow-ups that already carry embedded trust metadata and/or signatures

Their handling is:

1. if `X-TaktX-Signature` is present, the engine verifies the Ed25519 signature and key status
2. if `signingEnabled=true`, unsigned non-entry commands are rejected
3. if `signingEnabled=false`, signed and unsigned non-entry commands are both accepted

This means worker responses remain valid in JWT-only deployments where signing is disabled.

When the engine later re-emits scheduled work such as timer firings or external-task timeout events,
those follow-up commands are treated as newly engine-originated commands. They are therefore signed
and attributed to the engine on re-entry instead of retaining the original worker signer metadata.

### Current vs origin trust metadata

`ProcessInstanceTriggerDTO` and `InstanceUpdateDTO` now expose two trust fields:

- `currentTrustMetadata` — who is trusted for the command currently being processed
- `originTrustMetadata` — who originally initiated the command chain

Examples:

- external JWT start command → `current=JWT`, `origin=JWT`
- worker-signed external task response → `current=worker`, `origin=worker`
- timer continuation after that worker response → `current=engine`, `origin=worker`

This split avoids the old ambiguity where engine-rescheduled work could appear to still be owned by
the previous worker simply because that provenance had been copied into the next command.

For compatibility, the old `commandTrustMetadata` accessor still resolves to `currentTrustMetadata`,
but new consumers should prefer the explicit current/origin fields.

### Key lookup

JWT public keys are resolved by `kid` from the `taktx-signing-keys` KTable.
The engine first reads the JWT `kid` header, loads the matching RSA public key from Kafka state, then verifies the JWT signature and claims. The `kid` value is therefore only a lookup key, not proof by itself.

The engine therefore follows key rotation through Kafka state rather than a restart-time toggle.

---

## Engine event signing

### What the engine signs

When `signingEnabled=true` in the latest config-topic record, the engine signs outbound records such as:

- `instance-update`
- external-task trigger records
- engine-internal process-instance trigger records

The same key registry is also used to verify signed inbound non-entry commands such as worker responses.

### Engine key lifecycle

Each engine node resolves its active signing identity from the configured source:

- `generated` (default) — generate an Ed25519 key pair at startup with key ID `engine-<uuid>`
- `env` — read key material from `TAKTX_SIGNING_*`
- `file` — read key material from mounted files via `TAKTX_SIGNING_FILE_*_PATH`

Once an identity is available, the engine publishes the public key to `taktx-signing-keys` and retries until the topic is available.

### Signing gate

The engine only signs when all of the following are true:

1. the active license allows event signing
2. `GlobalConfigurationDTO.signingEnabled == true`
3. the engine public key has already been published successfully

If any condition is false, the record is produced unsigned.

---

## Signing keys topic

`SigningKeyDTO` records are stored in the compacted topic `taktx-signing-keys`.

### Uses

- JWT `kid` lookup for command authorization
- Ed25519 public-key lookup for worker response verification
- Ed25519 public-key lookup for engine-signed record verification

### Engine behaviour

Engine authorization no longer has a special-case shortcut for an injected engine signing key ID.
All Ed25519 key IDs are resolved uniformly from the signing-keys KTable.

### Worker behaviour

Workers still publish their own public key to `taktx-signing-keys` and sign responses with their configured private key.

If `signingEnabled=false`, worker responses may still be accepted unsigned. If `signingEnabled=true`, unsigned worker responses are rejected.

---

## Root trust chain (anchored mode)

### Overview

By default, TaktX operates in **community mode**: the declared role on a key in
`taktx-signing-keys` is accepted at face value (`OpenKeyTrustPolicy`). Security in this mode
depends entirely on Kafka ACLs preventing unauthorized writes to `taktx-signing-keys`.

**Anchored mode** closes this gap. When `TAKTX_PLATFORM_PUBLIC_KEY` is set on the engine, the
engine switches to `AnchoredKeyTrustPolicy`. Every key entry in `taktx-signing-keys` — both
`ENGINE`-role (engine) and `CLIENT`-role (worker) — must carry a cryptographic countersignature
from the platform root private key. A key without a valid countersignature is rejected at the
authorization gate, even if the declared role looks correct.

| Mode | `TAKTX_PLATFORM_PUBLIC_KEY` | Policy | Enforcement |
|---|---|---|---|
| Community | Not set | `OpenKeyTrustPolicy` | Declared role is trusted. Kafka ACLs are the security boundary. |
| Anchored | Set | `AnchoredKeyTrustPolicy` | Every key must be countersigned by the platform root key. |

### Canonical payload and signature format

The registration signature is `SHA256withRSA` (PKCS#1 v1.5) over the pipe-delimited UTF-8 string:

```
keyId|publicKeyBase64|algorithm|owner|role
```

Produced by the platform root private key. Stored as `SigningKeyDTO.registrationSignature`
(base64-encoded). The Java reference implementation is
`SigningKeyRegistrar.computeCanonicalPayload(SigningKeyDTO)`.

Shell equivalent:

```bash
PAYLOAD=$(printf '%s|%s|%s|%s|%s' "$KEY_ID" "$PUBLIC_KEY_BASE64" "Ed25519" "$OWNER" "$ROLE")
REGISTRATION_SIGNATURE=$(printf '%s' "$PAYLOAD" \
  | openssl dgst -sha256 -sign platform-private.pem \
  | base64)
```

### New environment variables

| Env var | Set on | Purpose |
|---|---|---|
| `TAKTX_PLATFORM_PUBLIC_KEY` | Engine | Activates anchored mode. Base64 DER X.509 RSA public key (minimum 2048-bit). |
| `TAKTX_ENGINE_KEY_REGISTRATION_SIGNATURE` | Engine | Countersignature for the engine's Ed25519 key. Required in anchored mode when using `file` or `env` signing source. |
| `TAKTX_SIGNING_REGISTRATION_SIGNATURE` | Worker / Client | Countersignature for the worker's Ed25519 key. Required in anchored mode. Read from `taktx.signing.registration-signature` property or the env var. |

### Deployment shapes

#### Standalone / Docker Compose (community mode)

No additional configuration needed. Omit `TAKTX_PLATFORM_PUBLIC_KEY`. Use the `generated`,
`env`, or `file` signing source for the engine. Workers sign their responses; the engine
accepts any non-revoked key at face value.

#### Docker Compose (anchored mode)

1. Run `scripts/generate_trust_anchor.sh --init` to generate the platform RSA root key pair.
2. Run `scripts/generate_trust_anchor.sh --sign` for each engine and worker key to produce
   their registration signatures.
3. Set the environment variables in `docker-compose-full.yaml` using the `x-taktx-anchored-*`
   YAML anchors. See `docker/signing/README.md` for the complete step-by-step workflow.

#### Cloud / Kubernetes

The same environment variables apply. Typical setup:

```yaml
# Kubernetes Secret (base64 the values below before storing)
TAKTX_PLATFORM_PUBLIC_KEY: <base64 DER RSA public key>
TAKTX_ENGINE_KEY_REGISTRATION_SIGNATURE: <base64 RSA/SHA-256 signature>

# Worker Deployment
TAKTX_SIGNING_REGISTRATION_SIGNATURE: <base64 RSA/SHA-256 signature>
```

Use a secrets manager (AWS Secrets Manager, GCP Secret Manager, HashiCorp Vault, Kubernetes
Secrets) to manage `TAKTX_PLATFORM_PUBLIC_KEY` and all registration signatures. The platform
root private key (`platform-private.pem`) must never leave the secure signing workstation or
secrets manager — it only needs to be accessible when generating new registration signatures.

#### Platform service integration

When a Platform Service issues JWTs, it publishes its RSA public key to `taktx-signing-keys`
under its `kid`. In anchored mode, this RSA key entry also needs a `registrationSignature`.
The Platform Service must call `TaktXClient.publishSigningKey(..., registrationSignature)` with
its own countersignature, or be pre-registered by the deployment tooling.

### Key rotation in anchored mode

When rotating an engine or worker key (new Ed25519 key files):

1. Generate new key files.
2. Run `scripts/generate_trust_anchor.sh --sign` for the new key to get a new
   `REGISTRATION_SIGNATURE`.
3. Update the environment variable in the container/deployment.
4. Restart the process. The old key is automatically retired to `TRUSTED` status by
   `MessageSigningService.retirePreviousKey()`.

---

## Configuration topic

The compacted topic `taktx-configuration` carries `ConfigurationEventDTO` records under key `"config"`.

The current `GlobalConfigurationDTO` fields are:

| Field | Meaning |
|---|---|
| `signingEnabled` | Enables outbound engine Ed25519 signing |
| `engineRequiresAuthorization` | Enables JWT command authorization |
| `rbacEnabled` | Reserved for future use |
| `trustedKeyIds` | Reserved compatibility surface |

### Fields removed from the engine config model

These are intentionally no longer runtime-configurable for the engine:

- `signingKeyId`
- `rejectExpired`
- `nonceCheckEnabled`

Rationale:

- engine key IDs are generated per node
- expired JWT rejection must always happen
- replay protection must always happen

---

## Environment variables

## Engine

### Required bootstrap settings

| Env var | Purpose |
|---|---|
| `KAFKA_BOOTSTRAP_SERVERS` | Kafka bootstrap connection |
| `TAKTX_ENGINE_TENANT_ID` | Tenant prefix for topics and application IDs |
| `TAKTX_ENGINE_NAMESPACE` | Namespace prefix for topics and application IDs |

### Optional trust material

| Env var | Purpose |
|---|---|
| `TAKTX_PLATFORM_PUBLIC_KEY` | Optional deployment trust anchor for authorization-related setups |

### Optional engine signing source selection

| Env var | Purpose |
|---|---|
| `TAKTX_SIGNING_IDENTITY_SOURCE` | Signing source for this process: engine defaults to `generated`, workers default to `env`; explicit values are `generated`, `env`, or `file` |
| `TAKTX_SIGNING_FILE_KEY_ID_PATH` | File-backed key ID path when `file` is selected |
| `TAKTX_SIGNING_FILE_PRIVATE_KEY_PATH` | File-backed private key path when `file` is selected |
| `TAKTX_SIGNING_FILE_PUBLIC_KEY_PATH` | File-backed public key path when `file` is selected |
| `TAKTX_SIGNING_FILE_REFRESH_INTERVAL_MS` | How often the process re-checks signing files for changes; default `1000` ms |

### Kafka auth / TLS

These remain environment-driven:

- `KAFKA_SECURITY_PROTOCOL`
- `KAFKA_SASL_MECHANISM`
- `KAFKA_SASL_JAAS_CONFIG`
- `KAFKA_SSL_TRUSTSTORE_LOCATION`
- `KAFKA_SSL_TRUSTSTORE_PASSWORD`
- related broker credentials

### Removed engine runtime toggles

These old runtime toggle env vars are removed:

- `TAKTX_SECURITY_SIGNING_ENABLED`
- `TAKTX_SECURITY_AUTHORIZATION_ENABLED`
- `TAKTX_SECURITY_REJECT_EXPIRED`
- `TAKTX_SECURITY_NONCE_CHECK`

Runtime signing and authorization now come from `taktx-configuration` updates.

## Worker / client signing

Worker-side signing still uses explicit key material because workers are not generating engine-managed keys.

| Env var | Purpose |
|---|---|
| `TAKTX_SIGNING_IDENTITY_SOURCE` | Worker signing source: `env` (default), `file`, or `generated` |
| `TAKTX_SIGNING_KEY_ID` | Worker signing key ID for `env` |
| `TAKTX_SIGNING_OWNER` | Optional human-readable owner label published alongside the worker public key; falls back to app name or key ID |
| `TAKTX_SIGNING_PUBLIC_KEY` | Worker Ed25519 public key for `env` |
| `TAKTX_SIGNING_FILE_KEY_ID_PATH` | Worker signing key ID file path for `file` |
| `TAKTX_SIGNING_FILE_PRIVATE_KEY_PATH` | Worker private key file path for `file` |
| `TAKTX_SIGNING_FILE_PUBLIC_KEY_PATH` | Worker public key file path for `file` |
| `TAKTX_SIGNING_FILE_REFRESH_INTERVAL_MS` | File refresh interval in milliseconds for `file`; default `1000` |

These are consumed by worker/client-side signing identity sources.
---

If `TAKTX_SIGNING_OWNER` is not provided, the client falls back to framework app name (`quarkus.application.name`, `spring.application.name`, or `application.name`) and finally to the signing `keyId`.

## Operational notes

### Safe defaults

If no `"config"` record has been received yet, the engine defaults to:

- `signingEnabled = false`
- `engineRequiresAuthorization = false`
- `rbacEnabled = false`
- `trustedKeyIds = []`

This means BPMN processing still runs normally on a fresh deployment with no config-topic record.

### Runtime toggling

Because signing and authorization are driven by the config topic, operators can enable or disable them without restarting the engine.

### Fixed security rules

These rules are not configurable:

- expired JWTs are always rejected
- replayed `auditId` values are always rejected
- when `engineRequiresAuthorization=true`, JWT is required for external start/abort entry commands
- when `signingEnabled=true`, unsigned non-entry process-instance commands are rejected
- engine signing defaults to generated keys but can be switched to env/file sources
- engine public-key publication retries until the signing-keys topic is available

### Suggested platform-team contract

The platform team should treat these as the stable runtime config fields:

- `signingEnabled`
- `engineRequiresAuthorization`
- `rbacEnabled`
- `trustedKeyIds`

And should no longer send or expect:

- `signingKeyId`
- `rejectExpired`
- `nonceCheckEnabled`
