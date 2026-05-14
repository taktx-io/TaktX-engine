# Security Future Development Plan

**Last updated:** 2026-05-14  
**Status:** Active roadmap — Workstreams 1 and 4 remain pending; Workstreams 2 and 3 are complete  
**Audience:** Platform and security engineers planning upcoming hardening work

This document tracks planned security development beyond the implemented baseline.

**Related security documents:**
- Implemented controls reference: [`docs/security.md`](security.md)
- Task-level tracking backlog: [`docs/security-implementation-backlog.md`](security-implementation-backlog.md)
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
**Status: ✅ Complete — implemented 2026-05-01 to 2026-05-07 (Epics E1–E5)**

> All deliverables in this workstream have been implemented and tested. The final DLQ topology differs from the original proposal below (per-surface `security-dlq.*` topics were superseded by a single unified `dlq` topic with per-envelope `sourceTopic` routing). See the authoritative implementation docs:
> - `docs/dlq-engine-design.md` — final topology and design decisions
> - `docs/dlq-implementation-backlog.md` — full implementation history
> - `docs/dlq-console-contract.md` — engine-console contract
> - `docs/dlq-feature-matrix.md` — Community vs Premium split

### Problem statement (resolved)

Rejected records on all 8 external execution ingress surfaces now route to the unified `dlq` topic rather than being silently dropped. `DlqEnvelope` carries full forensic context; `DlqReplayCommand` drives controlled operator replay via `dlq.replay`; results are audited on `dlq.replay-results`.

### Original proposed design (superseded)

> The items below were the original proposal and are preserved here for historical reference only. The actual implementation uses a single unified namespace-scoped `dlq` topic — not per-surface `security-dlq.*` topics.

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

### Acceptance criteria (all met)

- ✅ Rejection paths publish a DLQ record per rejected input record
- ✅ DLQ write failures are visible in logs/metrics and do not silently drop rejection context
- ✅ Operators can inspect and filter DLQ records by reason code and source topic
- ✅ No code path auto-forwards DLQ payloads back to protected hot topics

### Open decisions (all resolved)

- ✅ One shared `dlq` topic chosen (not per-source-topic DLQs) — `DlqEnvelope.sourceTopic` provides routing
- ✅ Payload storage model: full payload bytes in `valueBytes` + decoded summary in `decodedSummaryJson`
- ✅ Retention: DELETE policy, per-environment retention matrix in `docs/dlq-retention-policy.md`
- ✅ Manual replay via `taktx-client` `DlqReplayCommandBuilder` + `submitReplayCommand()`

---

## Workstream 3 — Structured security rejection visibility

**Priority:** Medium  
**Status: ✅ Complete — implemented 2026-05-14 via DLQ routing for external rejection paths plus excluded-topic metrics for engine-internal `schedule-commands` failures (SEC-007, SEC-008, SEC-010)**

### Goal

Make security failures measurable and queryable without requiring log scraping only.

### Deliverables — implemented visibility model

The final implementation uses the existing DLQ and excluded-topic observability surfaces rather than four new standalone security counters:

- `process-instance` authorization failures already route to the DLQ with `reason_code = AUTHORIZATION_FAILED`
- replay detections on JWT entry commands now route the first duplicate in-window event to the DLQ with `reason_code = REPLAY_DETECTED` / `DlqSeverity.CRITICAL`; subsequent duplicates for the same replay key are rate-gated
- `topic-meta-requested` authorization failures now route to the DLQ with mapped reason codes (`SIGNATURE_MISSING`, `SIGNATURE_KEY_UNKNOWN`, `SIGNATURE_KEY_REVOKED`, `AUTHORIZATION_FAILED`) while preserving the existing `topic-meta-actual` null publication contract
- `schedule-commands` authorization failures now increment `taktx.excluded.topic.failures{topic_group=schedule-commands}` and remain DLQ-excluded because the topic is engine-internal

The supporting Micrometer counters are live as of 2026-05-14:

- `taktx.dlq.entries{severity, reason_code, source_topic, capture_stage}` — counter per DLQ capture
- `taktx.dlq.replay.outcomes{status}` — replay outcome per attempt (SUCCESS / FAILED / DRY_RUN_PASSED)
- `taktx.excluded.topic.deserialization.errors{topic}` — excluded-topic poison records skipped (`ContinueOnDeserializationErrorHandler`)
- `taktx.excluded.topic.failures{topic_group}` — engine-internal processing exceptions for excluded topics (`DlqObservabilityService.recordExcludedTopicFailure`)

Structured audit logs are emitted by `DlqObservabilityService` for every DLQ entry and replay outcome. See `docs/dlq-console-contract.md` for the full Prometheus metric reference and `docker/prometheus-dlq-alerts.yaml` for alert rules.

### Architectural decision

The original plan proposed four standalone Micrometer counters for security rejections. That approach was superseded in favour of DLQ routing for the external rejection paths plus the existing excluded-topic counter for `schedule-commands`. This provides better operator value than parallel counters alone: full payload inspection, reason-code tagging, a single dashboard/alerting surface, and safe replay controls through the DLQ workflow.

> **Note on excluded-topic metric naming:** The live implementation uses `taktx.excluded.topic.failures{topic_group}` (in `DlqObservabilityService`) and `taktx.excluded.topic.deserialization.errors{topic}` (in `ContinueOnDeserializationErrorHandler`). Earlier drafts of this document referred to the former as `taktx.excluded.topic.processing.failures` — the live name is authoritative.

### Acceptance criteria

- DLQ entries or excluded-topic counters increment from real rejection and trust-failure code paths
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

| Milestone | Target | Outcome | Status |
|---|---|---|---|
| M1 - Replay hardening decision record | Next development cycle | Final dedup identity and phase-1 protected topics selected (SEC-013 – SEC-015) | ⏳ Pending |
| M2 - Replay hardening implementation | Following cycle | Selected signed paths protected with tests and operational guidance (SEC-016 – SEC-022) | ⏳ Pending |
| M3 - DLQ decision record | Following cycle | Final DLQ topology, envelope, and retention decisions | ✅ Done (2026-05-01) |
| M4 - DLQ + telemetry completion | Following cycle | Rejections routed to DLQ and exported with structured logs / metrics | ✅ Done (2026-05-07) |
| M5 - Threat model publication | Following cycle | Public threat-model doc aligned with code and ops guidance (SEC-011 – SEC-012) | ⏳ Pending |

---

## Internal epic label reference

Source code in `taktx-engine` uses short internal labels in Javadoc. This table maps them to publicly tracked workstreams:

| Code label | Public workstream / task |
|---|---|
| Epic B2 | Adoption of `VerificationCore` as the single verification seam for any future topic consumers added beyond the current three (`process-instance`, `schedule-commands`, `topic-meta-requested`) |
| Epic D4 | Durable dedup enforcement on signed non-entry message paths — maps to Workstream 1, M2 (SEC-016 – SEC-022) |
| Epic H | Structured security rejection visibility — maps to Workstream 3 (SEC-005 – SEC-010) |

---

## Recommended implementation order

1. Finalize replay-hardening decisions and phase-1 scope (M1)
2. Implement replay hardening on selected signed paths (M2)
3. ~~Finalize DLQ architecture decisions (M3)~~ — **Done**
4. ~~Implement DLQ publishing and telemetry together so reason codes and observability stay aligned (M4)~~ — **Done**
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


