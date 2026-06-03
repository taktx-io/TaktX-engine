# Changelog

All notable changes to TaktX Engine are documented in this file.

The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).
This project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

> **Beta notice:** Versions below `1.0.0` may contain breaking changes between minor releases.
> Breaking changes within a beta are noted explicitly.

---

## [Unreleased]

### ⚠ Breaking changes — security model simplification

The namespace security posture model has been replaced with a two-mode design. This is a
**hard break** from the previous OPEN / SECURED / ANCHORED_SECURED posture model.

**Removed:**

- `SecurityMode.SECURED` and `SecurityMode.ANCHORED_SECURED` — removed entirely
- `SecurityMode.MISCONFIGURED_SECURITY` — removed
- `NamespaceSecurityPolicyDTO` fields: `requiredSigning`, `requiredAuthorization`, `activationState`
- `TaktXClient.legacyGlobalSecurityConfigToNamespaceSecurityPolicy()` — removed
- Posture negotiation, capability exchange, `supportedPostures`, and the REQUESTED / VALIDATING /
  ACTIVE activation lifecycle
- Per-message-type signing / authorization flags (`clientCommands`, `workerResponses`, etc.)
- `AuthoritativeControlPlaneSecurityProperty.INTEGRITY_PROTECTION_REQUIRED_IN_SECURED_MODES`
  → renamed to `INTEGRITY_PROTECTION_REQUIRED_IN_ANCHORED_MODE`

**Migration from the old model:**

| Old concept | New equivalent |
|---|---|
| `OPEN` | `OPEN` — unchanged |
| `SECURED` or `ANCHORED_SECURED` | `ANCHORED` |
| `activationState` / activation lifecycle | removed — policy takes effect immediately |
| `requiredSigning.clientCommands = true` | `mode = ANCHORED` (all ingress is uniform) |
| `legacyGlobalSecurityConfigToNamespaceSecurityPolicy()` | Publish `ANCHORED` or `OPEN` policy directly |
| Generated in-memory identity for ANCHORED | `LocalPersistentSigningIdentitySource` (default) or `FileSigningIdentitySource` / env |

**New behaviour:**

- `OPEN` → infrastructure trust; no message-level verification; no trust registry required
- `ANCHORED` → every external runtime ingress record must be signed by a non-revoked key in
  `taktx-signing-keys`; engine fails closed if enforcement prerequisites are not met
- `TaktXClient` signs all outbound messages automatically in `ANCHORED` mode — callers do not
  choose signing per message type
- Worker signing identity defaults to `LocalPersistentSigningIdentitySource` (stable across
  restarts under `~/.taktx/signing/` or `TAKTX_SIGNING_LOCAL_DIRECTORY`)
- Security rejections route to `security-events`; they do not produce DLQ entries

---

### Added

- Explicit namespace-security-policy control-plane support across `taktx-engine`, `taktx-client`,
  and `taktx-shared`, including desired-vs-active policy identities and shared observability DTOs.
- Official `TaktXClient` helpers for authoritative namespace-security-policy publication and
  compacted-topic clear/tombstone semantics.
- Client-side namespace-security-policy observation and protected-runtime participation gating so
  protected client traffic is blocked unless the authoritative policy is `ACTIVE` and the client is
  locally `READY`.

### Changed

- Authoritative namespace-security-policy mutations now require the trusted writer path: explicit
  signature headers, trusted `PLATFORM` signer verification, and signed tombstone/clear semantics.
- Policy-required JWT/signature/trust-anchor enforcement now follows the authoritative active
  namespace policy directly instead of depending only on legacy global toggles.
- Quarkus and Spring runtime integrations now request worker topics through
  `TaktXClient.requestExternalTaskTopic(...)` rather than wiring equivalent lower-level topic
  publishers directly.

### Security

- Missing policy-required JWT, signature, or trust-anchor conditions now fail closed at protected
  runtime ingress.
- Break-glass downgrade handling requires privileged writer verification plus explicit actor/reason
  audit data.

### Compatibility notes

- Console/runtime integrations should migrate from legacy namespace-security booleans to explicit
  `NamespaceSecurityPolicyDTO` payloads.
- Status and security events are observability signals, not an authority source.
- Secured authoritative policy publication now requires an explicit configured signing identity.
- Protected runtime traffic may remain blocked during `REQUESTED` / `VALIDATING` until the
  authoritative policy becomes `ACTIVE` and participants are `READY`.

---

## [0.3.0-beta-1] — 2026-03-28

### 🎉 Highlights

- **Full open source release.** All five modules (`taktx-engine`, `taktx-client`,
  `taktx-client-quarkus`, `taktx-client-spring`, `taktx-shared`) are now published under
  the **Apache License 2.0** with no partition or feature restrictions.
- Spring Boot client module (`taktx-client-spring`) promoted from experimental to supported.

### Added

- `taktx-client-spring`: Spring Boot auto-configuration wrapper for `TaktXClient` — zero-boilerplate
  bean creation, lifecycle management, and `@TaktDeployment` class scanning.
- `taktx-client`: `CustomHeaders` annotation and `HeadersParameterResolver` for attaching custom
  Kafka record headers to worker completions.
- Ed25519 worker signing: `TaktXClient` can now publish Ed25519 signing keys and sign all outbound
  commands via a configurable `SigningKeyProvider`.
- RS256 JWT command authorization: pluggable `AuthorizationTokenProvider` for attaching Bearer tokens
  to every engine command.
- Root trust chain / anchored mode: full chain-of-trust verification for inbound signed records.

### Changed

- All source files migrated from TaktX Business Source License v1.0 to Apache License 2.0.
- `taktx-engine` partition budget is now unlimited in the community build; a license file pushed
  via the `taktx-configuration` topic can still extend or restrict limits for managed deployments.
- Docker image published to `ghcr.io/taktx-io/taktx-engine` (multi-arch: `amd64` + `arm64`).
- `VERSION.txt` no longer references BSL change dates.

### Fixed

- macOS-incompatible `date -d` syntax in Docker build scripts — now uses portable fallback.

---

## [0.2.0-beta-1] — 2026-01-24

### Added

- `taktx-client-quarkus`: Quarkus / MicroProfile CDI auto-configuration wrapper — singleton
  `TaktXClient` bean produced from Quarkus config properties.
- Instance update consumers: `subscribeInstanceUpdate()` for reacting to process state changes.
- Message events and signals: `correlateMessage()`, `sendSignal()` via Kafka topics.
- Publishing runtime configuration via `taktx-configuration` topic.
- Trust metadata on instance update events.

### Changed

- Engine Kafka Streams topology refactored for lower end-to-end latency on task completion.
- Testcontainers-based integration test suite extended to cover Quarkus client lifecycle.

---

## [0.1.0-beta-1] — 2025-11-15

### Added

- Core BPMN 2.0 execution engine on Apache Kafka Streams + RocksDB.
- `taktx-client`: framework-agnostic Java client (`TaktXClient`) for starting process instances,
  completing external tasks, completing user tasks, and deploying process definitions.
- `taktx-shared`: shared DTOs, BPMN 2.0 JAXB model classes, and Kafka message definitions.
- Multi-tenant, multi-namespace topic prefixing.
- JaCoCo coverage reporting with auto-generated SVG badges.
- GitHub Actions CI pipeline (build, test, coverage) and release pipeline (Docker + Maven Central).
- Dependabot configuration for automated dependency updates.

---

## [0.0.9-alpha-3] — 2025-09-01 *(internal / pre-release)*

Initial internal alpha — not publicly released.

---

[0.3.0-beta-1]: https://github.com/taktx-io/TaktX-engine/compare/v0.2.0-beta-1...v0.3.0-beta-1
[0.2.0-beta-1]: https://github.com/taktx-io/TaktX-engine/compare/v0.1.0-beta-1...v0.2.0-beta-1
[0.1.0-beta-1]: https://github.com/taktx-io/TaktX-engine/releases/tag/v0.1.0-beta-1

