# TaktX Engine — Security Overview

**Last updated:** March 2026  
**Status:** Implemented — all features described here are live in the current codebase

---

## Contents

1. [High-level model](#high-level-model)
2. [Command authorization](#command-authorization)
3. [Engine event signing](#engine-event-signing)
4. [Signing keys topic](#signing-keys-topic)
5. [Configuration topic](#configuration-topic)
6. [Environment variables](#environment-variables)
7. [Operational notes](#operational-notes)

---

## High-level model

TaktX has two separate security mechanisms:

1. **RS256 JWT command authorization**
   - Used for external commands such as process start and abort.
   - Guarded at runtime by `GlobalConfigurationDTO.engineRequiresAuthorization`.
   - JWT expiry is always enforced.
   - JWT replay protection via `auditId` nonce tracking is always enforced.

2. **Ed25519 message signing**
   - Used for engine outbound records and worker responses.
   - Guarded at runtime by `GlobalConfigurationDTO.signingEnabled`.
   - The engine uses a configurable signing-identity source; default is a generated in-memory key.
   - Workers use a configurable signing-identity source; default is env-based.

The shared trust registry is the compacted Kafka topic `taktx-signing-keys`.

---

## Command authorization

### Header

External commands are authorized through the `X-TaktX-Authorization` header.

### Validation steps

When `engineRequiresAuthorization=true` in the latest config-topic record, the engine validates:

1. JWT signature
2. required claims
3. token expiry (`exp`) — **always enforced**
4. command-to-claim match
5. replay protection using `auditId` — **always enforced**

If authorization is disabled in the config topic, the engine accepts commands without JWT validation.

### Key lookup

JWT public keys are resolved by `kid` from the `taktx-signing-keys` KTable.
The engine therefore follows key rotation through Kafka state rather than a restart-time toggle.

---

## Engine event signing

### What the engine signs

When `signingEnabled=true` in the latest config-topic record, the engine signs outbound records such as:

- `instance-update`
- external-task trigger records
- engine-internal process-instance trigger records

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
| `TAKTX_SIGNING_PRIVATE_KEY` | Worker Ed25519 private key for `env` |
| `TAKTX_SIGNING_PUBLIC_KEY` | Worker Ed25519 public key for `env` |
| `TAKTX_SIGNING_FILE_KEY_ID_PATH` | Worker signing key ID file path for `file` |
| `TAKTX_SIGNING_FILE_PRIVATE_KEY_PATH` | Worker private key file path for `file` |
| `TAKTX_SIGNING_FILE_PUBLIC_KEY_PATH` | Worker public key file path for `file` |
| `TAKTX_SIGNING_FILE_REFRESH_INTERVAL_MS` | File refresh interval in milliseconds for `file`; default `1000` |

These are consumed by worker/client-side signing identity sources.

---

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
