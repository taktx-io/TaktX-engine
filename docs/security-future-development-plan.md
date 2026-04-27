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

1. DLQ architecture for security rejections
2. Structured security telemetry (logs and metrics)
3. Formal threat model publication

This roadmap does not re-open already completed baseline controls (signing, JWT authorization, replay protection, and anchored trust).

---

## Workstream 1 — Security DLQ architecture

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

## Workstream 2 — Structured security telemetry

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

## Workstream 3 — Threat model publication

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
| M1 - DLQ decision record | Next development cycle | Final DLQ topology, envelope, and retention decisions |
| M2 - DLQ implementation | Following cycle | Rejections routed to DLQ with tests and runbook |
| M3 - Telemetry completion | Following cycle | Structured logs + metrics exported and validated |
| M4 - Threat model publication | Following cycle | Public threat-model doc aligned with code and ops guidance |

---

## Recommended implementation order

1. Finalize DLQ architecture decisions (M1)
2. Implement DLQ publishing in rejection paths (M2)
3. Add telemetry for both rejections and DLQ publishing health (M3)
4. Publish threat model using the now-stable observability + recovery model (M4)

---

## Validation strategy

- Unit tests for DLQ envelope generation and reason-code mapping
- Integration tests for rejected message -> DLQ publication on each protected topic
- Negative tests for DLQ publishing failure visibility
- Documentation checks to keep `docs/security.md`, this roadmap, and threat model cross-links consistent

---

## Decision log

### 2026-04-27

- Selected roadmap-first approach before implementing DLQ code
- Prioritized DLQ ahead of observability metrics because telemetry semantics depend on final rejection handling model


