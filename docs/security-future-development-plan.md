# Security Future Development Plan

**Last updated:** 2026-05-26  
**Status:** Active roadmap — Workstreams 1, 2, 3, and 4 are complete for the current documented release scope  
**Audience:** Platform and security engineers planning upcoming hardening work

This document tracks planned security development beyond the implemented baseline.

The earlier task-level backlog document has been retired; this roadmap now carries the surviving
future-work summary directly.

**Related security documents:**
- Implemented controls reference: [`docs/security.md`](security.md)
- Namespace security control-plane handoff: [`docs/console-security-control-plane-handoff.md`](console-security-control-plane-handoff.md)
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
**Status:** ✅ Complete for the current external release slice — M1 resolved 2026-05-14; M2 implemented 2026-05-15

### Problem statement (resolved in the current release slice)

Before M2, durable replay protection was intentionally scoped to JWT-bearing entry commands via
`auditId`. That left signed non-entry and control-plane paths dependent on message semantics and
downstream idempotency rather than on a dedicated duplicate-detection layer.

That baseline was acceptable for the initial security rollout, but it was not a full replay-safety
story for production environments where duplicate signed external-task responses, duplicate signed
user-task responses, external topic-management requests, or other signed non-entry traffic could
still create duplicate work, load spikes, or repeated side effects.

### Design goals

- Preserve the current fail-closed trust model for signed messages
- Add lightweight duplicate detection without attempting global exactly-once semantics
- Keep retention windows short and operationally predictable
- Avoid unbounded state growth in replay / dedup stores
- Roll out by topic class so high-risk paths can be hardened first

### Chosen design direction

- Introduce a stable dedup identity for signed non-entry messages, likely via one of:
  - explicit `messageId` on all signed messages
  - derived hash of signature + payload bytes
- Apply short-lived dedup windows to selected high-risk topics / DTO classes first:
  - external-task and user-task responses
  - `topic-meta-requested`
  - additional external message / trigger ingress surfaces as they are productised
- Defer engine-internal paths until after the current console/DLQ adoption wave:
  - `schedule-commands`
  - engine continuations where duplicate load is operationally relevant
- Keep dedup state partition-local where that matches topic routing semantics
- Document clearly that this is duplicate suppression, not a general transactional guarantee

### Acceptance criteria

- Duplicate signed messages on the selected protected paths are rejected within the configured dedup window
- State size remains bounded and configurable
- Rotation / restart behavior is documented and tested
- `docs/security.md` is updated to describe protected and unprotected paths precisely

### Implementation status (current release slice)

The current release slice has delivered the planned external phase-1 scope:

- `messageId` is now available on `ExternalTaskResponseTriggerDTO`, `UserTaskResponseTriggerDTO`, and `TopicMetaDTO`
- `process-instance` response dedup now protects `ExternalTaskResponseTriggerDTO` and `UserTaskResponseTriggerDTO`
- `topic-meta-requested` ingress now performs authorization, duplicate suppression, and broker-admin handoff through the topology split recorded below
- real-broker integration coverage exists for all three protected paths

The engine-internal follow-on items remain deliberately deferred beyond this release slice:

- `schedule-commands` (`MessageScheduleDTO`)
- engine-internal continuations on `process-instance`

The backlog now tracks those deferred internal-only follow-ons explicitly as:

- `SEC-019` — `schedule-commands` dedup
- `SEC-023` — engine-internal continuation dedup on `process-instance`

### Resolved decisions

- ✅ Canonical dedup key: optional explicit `messageId` with a transition fallback to a derived signature + payload hash
- ✅ Phase-1 scope: external-task and user-task responses plus `topic-meta-requested`; engine-internal paths deferred
- ✅ Retention defaults: 10 minutes for external-task and user-task responses and 2 minutes for `topic-meta-requested` in the current release slice; `schedule-commands` keeps a 5 minute candidate default for a later internal-only phase
- ✅ Topic-specific observability model retained: `topic-meta-requested` stays on the existing DLQ/null-publication contract; engine-internal topics remain out of scope for this release slice

### M1 — Replay hardening decision record ✅ Resolved 2026-05-14

#### Context

The current durable replay store protects JWT-bearing entry commands only and keys them by `issuer + auditId`.
That leaves the signed non-entry paths already enforced by `MessageSecurityPolicyRegistry` — notably
`ExternalTaskResponseTriggerDTO`, `UserTaskResponseTriggerDTO`, `MessageScheduleDTO`, and
`TopicMetaDTO` — without a dedicated duplicate-suppression layer. Those paths are already signed and
authorised today, but a replayed valid record can still create duplicate work, extra scheduling load,
or repeated operational churn within the acceptance window of the receiving processor.

The decision record below resolves the three M1 design questions in a way that keeps M2 incremental,
backward-compatible, and aligned with the existing signing / DLQ model.

#### Decision 1 — Canonical dedup identity (SEC-013)

Use an optional explicit `messageId` field as the canonical dedup identity on the external phase-1 DTOs:

- `ExternalTaskResponseTriggerDTO`
- `UserTaskResponseTriggerDTO`
- `TopicMetaDTO`

The M2 engine logic should derive the durable dedup key as follows:

1. If `messageId` is present and non-blank, use it as the logical message identity.
2. If `messageId` is absent or blank, fall back to a derived hash of the exact signed record identity
   (`tx-sig` header value + payload bytes as consumed from Kafka) for transition
   compatibility with existing producers.
3. Prefix the stored key with a topic-class namespace so identical UUIDs on different protected
   paths do not collide.

This chooses the explicit-ID model for long-term operability while preserving a non-breaking upgrade
path for existing clients. The same mechanism can later be extended to engine-internal records such
as `MessageScheduleDTO` if a later release decides to harden those paths too.

#### Rationale

- `messageId` is human-readable, loggable, and can be copied into incident tickets and DLQ review
  workflows.
- `messageId` survives payload correction during DLQ replay; a hash of signature + payload does not.
- The current codebase has multiple external producer entry points, not a single builder-only API:
  `ProcessInstanceResponder` / responder helpers construct external-task and user-task responses directly,
  and `ExternalTaskTopicRequester` constructs `TopicMetaDTO` directly. An optional field plus
  producer-side auto-population fits that rollout model without breaking existing wire compatibility.
- The fallback hash remains useful during migration because the current signing serializers already
  bind the Ed25519 signature to the exact payload bytes that hit Kafka.

#### Transition and backward compatibility

- `messageId` remains optional for the whole M2 rollout.
- Producer-side helper code should auto-populate `UUID.randomUUID().toString()` when callers do not
  set `messageId` explicitly.
- Legacy records without `messageId` must still be accepted and deduplicated via the derived fallback.
- A corrected DLQ replay should preserve the original `messageId` unless the operator is explicitly
  creating a new logical action.
- The fallback hash can be deprecated only after all supported producers populate `messageId`
  consistently.

#### Decision 2 — Phase-1 protected scope (SEC-014)

M2 phase 1 will protect the following external signed non-entry paths first:

1. `ExternalTaskResponseTriggerDTO` and `UserTaskResponseTriggerDTO` on `process-instance`
2. `TopicMetaDTO` on `topic-meta-requested`

These are the first protected paths because they cover the highest-value non-entry surfaces already
present in the codebase:

- external-task and user-task responses are externally produced, security-sensitive, and can advance or
  re-drive business execution
- `topic-meta-requested` is operationally idempotent but externally supplied and vulnerable to burst
  replay noise without lightweight dedup

Deferred to a later phase:

- engine-internal `schedule-commands` (`MessageScheduleDTO`)
- engine-internal continuations on `process-instance`
  (`ContinueFlowElementTriggerDTO`, `StartFlowElementTriggerDTO`, `EventSignalTriggerDTO`)

Those internal paths are already restricted to trusted `ENGINE` signatures and usually converge
through process state. They remain worth hardening later, but including them in the current release
would broaden the topology and test matrix while the console team is still absorbing the already
large DLQ/security surface added in Workstreams 2 and 3. The backlog now tracks this deferred work
explicitly as `SEC-019` (`schedule-commands`) and `SEC-023` (engine-internal continuations on
`process-instance`).

#### Decision 3 — Retention defaults (SEC-015)

The default dedup windows for the current M2 release slice are:

| Topic class | Default window | Reasoning |
|---|---|---|
| External-task / user-task responses on `process-instance` | 10 minutes | Aligns with the existing default `replayProtectionRetentionMs = 600_000`, matches common task completion / retry latency, and keeps operator expectations simple |
| `topic-meta-requested` | 2 minutes | Requests are operationally idempotent and primarily need burst suppression, not long-lived duplicate quarantine |

Candidate default for a later internal-only phase:

| Topic class | Candidate window | Reasoning |
|---|---|---|
| `schedule-commands` | 5 minutes | Engine-generated records are short-lived and bursty; a shorter window suppresses near-term duplicates without retaining obsolete schedule keys longer than necessary |

These are defaults, not protocol guarantees. Duplicates outside the configured window are allowed to
flow again and must still be safe under normal processor semantics.

#### Architecture split for `topic-meta-requested`

To remove the current architectural awkwardness around `DynamicTopicManager`, the preferred M2 shape
is a split between ingress security handling in Kafka Streams and broker-admin side effects in a
small orchestration service.

Preferred target shape:

1. `topic-meta-requested` remains the external ingress topic.
2. Kafka Streams owns the intake pipeline for that topic:
   - deserialisation
   - signature / trust verification
   - duplicate suppression
   - rejection routing (DLQ + preserved `topic-meta-actual` null contract)
3. Accepted requests are forwarded to a thin engine-internal handoff stage (implementation may use
   either a dedicated internal topic or a dedicated side-effect processor boundary, whichever keeps
   ownership clear).
4. A slimmed-down topic-management service owns only broker-admin side effects:
   - create / resize / inspect topics via `AdminClient`
   - publish `topic-meta-actual`
   - maintain reconciliation / adaptation scans

This keeps the security-critical intake path aligned with the rest of the Streams topology while
preserving a clean boundary around non-deterministic broker-admin operations.

#### Implementation notes that directly unblock M2

- M2 should treat this as duplicate suppression, not exactly-once delivery.
- `messageId` should be surfaced in logs / DLQ summaries where available; when the fallback path is
  used, operators should still be able to tell that no explicit `messageId` was present.
- The external-task / user-task response scope covers both `ExternalTaskResponseTriggerDTO` and
  `UserTaskResponseTriggerDTO` even though they share the `process-instance` topic; dedup keys must
  therefore include a DTO/topic-class namespace.
- `topic-meta-requested` dedup should be implemented in the topology-owned ingress stage so
  duplicate requests are suppressed before topic-management side effects and before DLQ reason
  mapping for unrelated auth failures.
- Engine-internal paths (`schedule-commands`, internal continuations) are deliberately deferred until
  after the console team has adapted to the current DLQ/security release surface.
- The chosen defaults and architecture split unblock the external-facing part of SEC-016 through
  SEC-022 without requiring a breaking protocol change or a single all-at-once producer migration.

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
**Status:** ✅ Complete — implemented 2026-05-15 (SEC-011, SEC-012)

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
| M1 - Replay hardening decision record | Next development cycle | Final dedup identity, phase-1 protected topics, and retention defaults selected (SEC-013 – SEC-015) | ✅ Done (2026-05-14) |
| M2 - Replay hardening implementation | Following cycle | Selected external signed paths protected with tests and operational guidance; engine-internal paths deferred to a later slice (`SEC-019`, `SEC-023`) | ✅ Done (2026-05-15) |
| M3 - DLQ decision record | Following cycle | Final DLQ topology, envelope, and retention decisions | ✅ Done (2026-05-01) |
| M4 - DLQ + telemetry completion | Following cycle | Rejections routed to DLQ and exported with structured logs / metrics | ✅ Done (2026-05-07) |
| M5 - Threat model publication | Following cycle | Public threat-model doc aligned with code and ops guidance (SEC-011 – SEC-012) | ✅ Done (2026-05-15) |

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

1. ~~Finalize replay-hardening decisions and phase-1 scope (M1)~~ — **Done**
2. ~~Implement replay hardening on selected signed paths (M2)~~ — **Done**
3. ~~Finalize DLQ architecture decisions (M3)~~ — **Done**
4. ~~Implement DLQ publishing and telemetry together so reason codes and observability stay aligned (M4)~~ — **Done**
5. ~~Publish threat model using the now-stable replay / observability / recovery model (M5)~~ — **Done**

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

### 2026-05-15

- Deferred internal-only replay-hardening work is now tracked explicitly in the backlog as
  `SEC-019` (`schedule-commands`) and `SEC-023` (engine-internal continuations on
  `process-instance`).
- M5 implemented: `docs/security-threat-model.md` now documents security boundaries, trust assumptions,
  enforced controls, Kafka/platform dependencies, anchored/community mode limitations, security-critical
  topics, residual risks, and compensating controls.
- `docs/security.md` and `SECURITY.md` now cross-link the threat model so public-facing security docs
  point to the same trust-boundary reference.
- M2 implemented for the current external replay-hardening slice.
- Phase-1 dedup is now live on `ExternalTaskResponseTriggerDTO`, `UserTaskResponseTriggerDTO`, and
  `TopicMetaDTO`, with 10 minute defaults for the `process-instance` response paths and a 2 minute
  default for `topic-meta-requested`.
- `docs/security.md` was updated so the public replay-protection scope matches the live code paths
  and distinguishes JWT `auditId` replay enforcement from the newer fixed-window signed-message dedup.
- Engine-internal `schedule-commands` and internal continuations remain deferred follow-on work.

### 2026-05-14

- M1 resolved for Workstream 1.
- Canonical dedup identity chosen: optional explicit `messageId` on phase-1 DTOs, with a transition
  fallback to a derived `signature + payload` hash for legacy producers.
- M2 release scope narrowed to externally originated signed non-entry paths: external-task and
  user-task responses plus `topic-meta-requested`.
- Default retention windows fixed at 10 minutes for external-task and user-task responses and 2 minutes for
  `topic-meta-requested`; `schedule-commands` retains a 5 minute candidate default for a later phase.
- Engine-internal paths (`schedule-commands`, continuations) deferred to a later phase because they
  are already `ENGINE`-signed and the current release already contains a large console-facing
  DLQ/security change set.
- Preferred `topic-meta-requested` architecture split recorded: topology-owned ingress security and
  dedup, separate broker-admin orchestration service for side effects.

### 2026-04-27

- Selected roadmap-first approach before implementing DLQ code
- Prioritized DLQ ahead of observability metrics because telemetry semantics depend on final rejection handling model
- Expanded the roadmap to explicitly include replay hardening beyond JWT-bearing entry commands


