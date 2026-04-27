# Security Future Development Plan

**Last updated:** 2026-04-27
**Status:** Active roadmap
**Audience:** Platform and security engineers planning upcoming hardening work

This document tracks planned security development beyond the implemented baseline.

**Related security documents:**
- Implemented controls reference: [`docs/security.md`](security.md)
- Vulnerability reporting and support policy: [`SECURITY.md`](../SECURITY.md)

---

## Purpose

Define the next security development slice after completion of the hardening baseline documented in [`docs/security.md`](security.md).

---

## Scope

This roadmap covers:

1. Replay hardening beyond JWT entry commands
2. DLQ architecture for security rejections
3. Structured security telemetry (logs and metrics)
4. Formal threat model publication

This roadmap builds on the completed baseline controls (signing, JWT authorization, replay protection, and anchored trust) and tracks the next hardening steps needed for stronger production deployments.

---

## Workstream 1 — Replay hardening for signed non-entry messages

**Priority:** High

### Problem statement

Current durable replay protection is intentionally scoped to JWT-bearing entry commands via `auditId`. That leaves signed non-entry and control-plane paths dependent on message semantics and downstream idempotency rather than on a dedicated duplicate-detection layer.

This is acceptable as a baseline, but it is not a full replay-safety story for production environments where duplicate signed worker responses, schedule commands, or internal continuation traffic may still create duplicate work, load spikes, or repeated side effects.

### Design goals

- Preserve the current fail-closed trust model for signed messages
- Add lightweight duplicate detection without attempting global exactly-once semantics
- Keep retention windows short and operationally predictable
- Avoid unbounded state growth in replay / dedup stores
- Roll out by topic class so high-risk paths can be hardened first

### Proposed design direction

- Introduce a stable dedup identity for signed non-entry messages, likely via one of:
  - explicit `messageId` on all signed messages
  - derived hash of signature + payload bytes
- Apply short-lived dedup windows to selected high-risk topics / DTO classes first:
  - worker responses
  - `schedule-commands`
  - `topic-meta-requested`
  - engine continuations where duplicate load is operationally relevant
- Keep dedup state partition-local where that matches topic routing semantics
- Document clearly that this is duplicate suppression, not a general transactional guarantee

### Acceptance criteria

- Duplicate signed messages on the selected protected paths are rejected within the configured dedup window
- State size remains bounded and configurable
- Rotation / restart behavior is documented and tested
- `docs/security.md` is updated to describe protected and unprotected paths precisely

### Open decisions

- Canonical dedup key: explicit `messageId` versus derived hash
- Which topics are hardened in phase 1 versus later phases
- Retention defaults per topic class
- Whether schedule / control-plane dedup should feed DLQ or fail closed with log-only handling first

---

## Workstream 2 — Security DLQ architecture

**Priority:** High

### Problem statement

Current hardened paths reject unauthorized or malformed records, but there is no single recovery-oriented sink for rejected messages. Operators need a deterministic place to inspect, triage, and optionally replay valid remediated payloads.

### Design goals

- Preserve security: no automatic replay from DLQ back to hot topics
- Preserve forensic value: keep enough metadata to explain rejection reason
- Preserve operability: support incident triage and controlled remediation workflows

### Proposed design

- Introduce dedicated DLQ topics with tenant/namespace prefixing
- Use separate DLQs for distinct risk surfaces when needed:
  - `<tenant>.<namespace>.security-dlq.process-instance`
  - `<tenant>.<namespace>.security-dlq.schedule-commands`
  - `<tenant>.<namespace>.security-dlq.topic-meta-requested`
- Write a structured DLQ envelope containing:
  - original topic/partition/offset
  - message key (as base64 if binary)
  - message payload (as base64)
  - headers snapshot
  - rejection timestamp
  - rejection reason code
  - short human-readable reason
  - engine instance ID / application ID
- Configure DLQ retention and ACLs explicitly; DLQ topics are operational evidence and must be write-restricted from non-engine principals.

### Acceptance criteria

- Rejection paths publish a DLQ record exactly once per rejected input record
- DLQ write failures are visible in logs/metrics and do not silently drop rejection context
- Operators can inspect and filter DLQ records by reason code and source topic
- No code path auto-forwards DLQ payloads back to protected hot topics

### Open decisions

- One shared `security-dlq` topic versus per-source-topic DLQs
- Payload storage model: full payload bytes versus metadata-only mode
- Retention defaults and compaction policy
- Whether to introduce a manual replay tool in-repo or keep replay external

---

## Workstream 3 — Structured security telemetry

**Priority:** Medium

### Goal

Make security failures measurable and queryable without requiring log scraping only.

### Deliverables

- Structured rejection logs with stable fields:
  - `event=security_rejection`
  - `topic`, `messageType`, `keyId`, `derivedRole`, `reasonCode`, `auditId`, `outcome`
- Micrometer counters:
  - `taktx.security.rejected.messages`
  - `taktx.security.invalid.signatures`
  - `taktx.security.replay.attempts`
  - `taktx.security.topic.requests.rejected`
  - `taktx.security.dlq.publish.failures`
- Optional timer/histogram for verification latency on protected paths

### Acceptance criteria

- Counters increment from real rejection and trust-failure code paths
- Log fields are consistent across `process-instance`, `schedule-commands`, and `topic-meta-requested`
- Metrics are visible through existing Prometheus export

---

## Workstream 4 — Threat model publication

**Priority:** Medium

### Goal

Publish a concise threat model that aligns with implemented controls and deployment assumptions.

### Required sections

- Security boundaries and trust assumptions
- What is enforced in engine code
- What still depends on Kafka ACLs and platform controls
- Anchored mode guarantees and limitations
- Community mode limitations
- Security-critical topics and data flows
- Residual risks and compensating controls

### Acceptance criteria

- Published as `docs/security-threat-model.md`
- Cross-linked from `docs/security.md` and `SECURITY.md`
- Reviewed for consistency with runtime behavior and configuration options

---

## Milestones

| Milestone | Target | Outcome |
|---|---|---|
| M1 - Replay hardening decision record | Next development cycle | Final dedup identity and phase-1 protected topics selected |
| M2 - Replay hardening implementation | Following cycle | Selected signed paths protected with tests and operational guidance |
| M3 - DLQ decision record | Following cycle | Final DLQ topology, envelope, and retention decisions |
| M4 - DLQ + telemetry completion | Following cycle | Rejections routed to DLQ and exported with structured logs / metrics |
| M5 - Threat model publication | Following cycle | Public threat-model doc aligned with code and ops guidance |

---

## Recommended implementation order

1. Finalize replay-hardening decisions and phase-1 scope (M1)
2. Implement replay hardening on selected signed paths (M2)
3. Finalize DLQ architecture decisions (M3)
4. Implement DLQ publishing and telemetry together so reason codes and observability stay aligned (M4)
5. Publish threat model using the now-stable replay / observability / recovery model (M5)

---

## Validation strategy

- Unit tests for dedup identity generation and replay-window behavior
- Integration tests for duplicate signed message rejection on each phase-1 protected topic
- Unit tests for DLQ envelope generation and reason-code mapping
- Integration tests for rejected message -> DLQ publication on each protected topic
- Negative tests for DLQ publishing failure visibility
- Documentation checks to keep `docs/security.md`, this roadmap, and threat model cross-links consistent

---

## Decision log

### 2026-04-27

- Selected roadmap-first approach before implementing DLQ code
- Prioritized DLQ ahead of observability metrics because telemetry semantics depend on final rejection handling model
- Expanded the roadmap to explicitly include replay hardening beyond JWT-bearing entry commands


