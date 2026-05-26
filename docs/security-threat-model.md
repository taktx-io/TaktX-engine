# TaktX — Security Threat Model

**Last updated:** 2026-05-26  
**Status:** Active reference — aligned with the implemented security baseline and current M2 external replay-hardening slice  
**Audience:** Platform operators, security reviewers, and maintainers assessing TaktX deployment risk

This document describes the security boundaries, trust assumptions, enforced controls, residual risks,
and compensating controls for the current TaktX codebase.

**Related security documents:**
- Implemented controls reference: [`docs/security.md`](security.md)
- Security roadmap and milestones: [`docs/security-future-development-plan.md`](security-future-development-plan.md)
- Namespace security control-plane handoff: [`docs/console-security-control-plane-handoff.md`](console-security-control-plane-handoff.md)
- Vulnerability reporting and support policy: [`SECURITY.md`](../SECURITY.md)

---

## 1. Security boundaries and trust assumptions

### Primary security boundary

TaktX is a Kafka-centric engine. Its effective ingress boundary is the Kafka cluster and the set of
principals allowed to publish to security-relevant topics. The engine exposes no custom REST API;
its only HTTP surface is the standard Quarkus health/readiness/liveness endpoints.

### Core trust assumptions

The current implementation assumes:

- Kafka is protected with TLS / SASL, ACLs, quotas, and operational monitoring
- only intended principals can publish to security-critical topics
- the platform root RSA private key is protected offline and outside application runtime
- engine, workers, and JWT issuers have reasonably synchronized clocks
- workers and platform publishers protect their own private signing keys
- downstream business handlers preserve their normal idempotency / convergence guarantees even when
  duplicate suppression windows expire

### Security design philosophy

TaktX intentionally combines multiple complementary trust layers rather than treating any single
mechanism as sufficient on its own. The security model is designed around the interaction of:

- infrastructure security at the Kafka/platform boundary (TLS / SASL, ACLs, quotas, auditability)
- cryptographic message trust (Ed25519 signatures plus the signing-keys registry)
- explicit authorization for externally authorized entry commands (RS256 JWT validation)
- replay resistance and short-window duplicate suppression where those controls are operationally justified
- structured rejection visibility and recovery controls through DLQ and excluded-topic observability

This is a zero-trust-aligned architecture, but not a claim that application-layer cryptography alone
replaces the broker, the platform perimeter, or operational controls. The design deliberately keeps
those responsibilities explicit so trust boundaries remain understandable during incident response,
key rotation, replay investigation, and day-2 operations.

### Security model summary

TaktX relies on two independent but cooperating trust layers:

1. **Message trust** via Ed25519 signatures and the `taktx-signing-keys` trust registry
2. **Entry-command authorization** via RS256 JWT validation for externally authorized process-entry
   commands

Replay hardening is layered on top of those trust checks:

- durable `auditId` replay protection for JWT-bearing entry commands
- fixed-window duplicate suppression for the current external phase-1 signed non-entry paths:
  `ExternalTaskResponseTriggerDTO`, `UserTaskResponseTriggerDTO`, and `TopicMetaDTO`

---

## 2. What is enforced in engine code

The current engine code actively enforces the following controls.

### Signature verification and signing policy

- inbound signed non-entry records are verified against trusted keys from `taktx-signing-keys`
- revoked keys are rejected
- missing, malformed, unknown, or untrusted signatures are rejected on protected paths
- outbound engine records are signed when signing is enabled and the engine key is published
- trust evaluation is fail-closed for unknown / revoked / insufficient-role keys on protected paths

### JWT authorization for entry commands

For `StartCommandDTO`, `AbortTriggerDTO`, and `SetVariableTriggerDTO`, the engine can enforce:

- RS256 JWT verification by `kid` lookup through `taktx-signing-keys`
- required claim validation
- command-to-claim consistency
- expiry (`exp`) checks
- replay detection keyed by `issuer + auditId`

### Replay and dedup enforcement

The code now distinguishes two mechanisms:

- **JWT replay protection** for entry commands using `replayProtectionMode` and
  `replayProtectionRetentionMs`
- **fixed-window dedup** for externally originated signed non-entry phase-1 paths:
  - `ExternalTaskResponseTriggerDTO` on `process-instance` — 10 minute default window
  - `UserTaskResponseTriggerDTO` on `process-instance` — 10 minute default window
  - `TopicMetaDTO` on `topic-meta-requested` — 2 minute default window

For the phase-1 signed-message dedup paths, the identity model is:

1. `messageId` when present and non-blank
2. fallback to `X-TaktX-Signature + payload bytes` when `messageId` is absent

### DLQ and rejection handling

TaktX now exposes rejection visibility through the unified DLQ and excluded-topic metrics:

- external rejection paths produce structured DLQ records with reason codes and captured context
- replay detections on JWT entry commands emit rate-gated `REPLAY_DETECTED` DLQ entries
- `topic-meta-requested` authorization failures preserve the `topic-meta-actual` null-publication
  contract and also emit DLQ entries
- `schedule-commands` authorization failures remain DLQ-excluded but increment the excluded-topic
  failure counter

### Startup and trust-bootstrap protections

- signing-key consumers read the trust registry to end-of-topic before protected signed processing begins
- anchored mode validates registration signatures against the platform root RSA public key
- production mode can refuse startup when anchored trust prerequisites are not satisfied

---

## 3. What still depends on Kafka ACLs and platform controls

Some critical guarantees are intentionally outside engine code and must be provided by the Kafka
platform and deployment environment.

### Kafka and platform responsibilities

Operators must provide and maintain:

- least-privilege ACLs for all security-critical topics
- authentication and transport security (for example TLS / SASL)
- producer quotas, throttling, connection limits, and max-request-size controls
- audit logging and monitoring for topic writes and ACL changes
- secret handling for worker keys, engine keys, and the platform root RSA key
- safe key-rotation procedures and overlap windows
- clock synchronization across engine nodes, workers, and JWT issuers

### Why engine checks are not sufficient on their own

Even in anchored mode, a principal with write access to sensitive topics can still cause damage by:

- flooding topics to create load or operational churn
- publishing malformed or disruptive traffic that consumes resources before rejection
- abusing engine-internal operational topics if ACLs are too broad
- creating denial-of-service pressure through volume, timing, or topic-level abuse

The engine validates message trust and selected replay / dedup semantics, but it does not replace the
Kafka broker as the primary authorization perimeter.

---

## 4. Anchored mode guarantees and limitations

### Guarantees

When `TAKTX_PLATFORM_PUBLIC_KEY` is configured, anchored mode requires all trusted signing keys to
carry a valid RSA/SHA-256 registration signature rooted in the platform public key.

In anchored mode, the engine guarantees that:

- uncountersigned or incorrectly countersigned keys are rejected at trust evaluation time
- key role claims still have to satisfy the required role (`CLIENT`, `ENGINE`, `PLATFORM`)
- revoked keys are rejected
- production mode can fail startup if a stable engine identity and registration signature are missing

### Limitations

Anchored mode does **not**:

- eliminate the need for Kafka ACLs
- stop a write-capable attacker from creating noise, denial-of-service pressure, or registry churn
- protect against compromise of the platform root private key
- provide blanket replay safety for every signed/control-plane message path
- guarantee exactly-once delivery semantics

Anchored mode should therefore be treated as a cryptographic trust layer on top of Kafka security,
not as a substitute for broker authorization and operational controls.

---

## 5. Community mode limitations and explicit non-goals

### Community mode limitations

When `TAKTX_PLATFORM_PUBLIC_KEY` is absent, TaktX uses the open/community trust model.

In this mode:

- declared key roles are accepted at face value if the key record is otherwise trusted and not revoked
- any actor able to write relevant security topics can effectively impersonate trusted roles
- the real security perimeter becomes Kafka ACL quality rather than cryptographic root-of-trust

This makes community mode useful for local development, demos, and basic integration environments,
but it should be treated as insecure for production.

### Explicit non-goals in the current implementation

The current codebase does **not** attempt to provide:

- blanket duplicate suppression across all topics
- transactional exactly-once security guarantees across Kafka topics and downstream side effects
- automatic buffering/retry of messages rejected because a newly rotated key has not yet propagated
- protection against physical compromise of Kafka brokers or host systems
- replacement of platform IAM / ACL design with application-layer cryptography alone

---

## 6. Security-critical topics and data flows

The following topics are part of the live security model.

| Topic / flow | Security role | Primary protection |
|---|---|---|
| `taktx-signing-keys` | Live trust registry for Ed25519 and RSA verification keys | Kafka ACLs, anchored countersignatures, revocation checks |
| `taktx-configuration` | Runtime security configuration (`signingEnabled`, `engineRequiresAuthorization`, replay settings) | Kafka ACLs, operational change control |
| `process-instance` | Entry commands, worker responses, engine continuations | JWT auth for entry commands, signature verification, replay/dedup by path |
| `schedule-commands` | Engine-internal scheduling/control path | trusted `ENGINE` signatures, excluded-topic observability, Kafka ACLs |
| `topic-meta-requested` | External topic-management ingress | signature verification, dedup, DLQ/null-publication contract |
| `topic-meta-actual` | Topic-management outcome/contract topic | engine publication plus rejection null contract |
| external-task trigger topics | Worker-facing execution delivery | signing, topic scoping, Kafka ACLs |
| `dlq` / `dlq.replay` / `dlq.replay-results` | Operational evidence and controlled replay path | DLQ validation rules, replay policy, Kafka ACLs |

### High-level trusted data flow

1. Platform / worker / engine keys are published to `taktx-signing-keys`
2. Runtime config is published to `taktx-configuration`
3. External entry commands arrive on `process-instance` with JWTs
4. Worker responses and topic-management requests arrive signed and are verified against the trust registry
5. Rejected external records are surfaced through DLQ flows or excluded-topic observability
6. Accepted records advance process state, topic management, scheduling, or downstream work

---

## 7. Residual risks and compensating controls

### Residual risks

The current design still carries the following important risks:

- duplicates outside configured replay/dedup windows can still be accepted
- deferred internal paths such as `schedule-commands` and engine continuations do not yet have their
  own fixed-window dedup layer
- operational complexity remains a real risk surface for secure day-2 operation, especially around
  DLQ repair/replay flows, mixed-version clusters, schema evolution of signed payloads, and long-running
  process instances that outlive key-rotation or rollout windows
- community mode is not production-grade trust
- compromise of the platform root key defeats anchored trust
- Kafka write access to security-critical topics can still be abused for denial-of-service or
  operational disruption even when cryptographic checks reject the payloads
- key-rotation timing can still cause short-lived reject windows if consumers have not yet observed a
  newly published key
- application-layer trust checks cannot compensate for weak broker/network isolation

### Compensating controls

Operators should mitigate those risks by:

- enabling anchored mode for production deployments
- strictly limiting ACLs on `taktx-signing-keys`, `taktx-configuration`, `process-instance`,
  `schedule-commands`, `topic-meta-requested`, and DLQ replay topics
- using Kafka quotas and broker monitoring to detect abusive producers early
- synchronizing clocks with NTP or equivalent
- documenting and rehearsing safe key-rotation overlap procedures
- using staged rollouts, schema-compatibility checks for signed payloads, and explicit replay/repair
  runbooks for DLQ handling, mixed-version clusters, and long-running process instances
- keeping business-side handlers idempotent where possible
- monitoring DLQ reason codes, excluded-topic failure counters, and unusual signing-key churn
- treating the platform root private key as a high-sensitivity offline secret

---

## Review summary

This threat model is aligned with the implemented controls described in [`docs/security.md`](security.md),
the completed replay-hardening slice recorded in [`docs/security-future-development-plan.md`](security-future-development-plan.md),
and the active support/reporting policy in [`SECURITY.md`](../SECURITY.md).

