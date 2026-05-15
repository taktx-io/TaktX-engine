# Security Implementation Backlog

**Document Version**: 1.0  
**Date**: May 14, 2026  
**Status**: Active  
**Audience**: Platform and security engineers working on TaktX hardening

This document is the task-level tracking companion to [`docs/security-future-development-plan.md`](security-future-development-plan.md).  
Each task has a stable ID (`SEC-NNN`), a status, and acceptance criteria.

**Related documents:**
- Implemented controls: [`docs/security.md`](security.md)
- Roadmap and workstreams: [`docs/security-future-development-plan.md`](security-future-development-plan.md)
- Vulnerability policy: [`SECURITY.md`](../SECURITY.md)

---

## Status legend

| Symbol | Meaning |
|---|---|
| ⏳ | Pending — not started |
| 🔄 | In progress |
| ✅ | Done |
| 🚫 | Blocked |
| 💬 | Decision needed |

---

## Phase 0 — Housekeeping (doc fixes, no code changes)

> Target: next available session. Low risk, always safe to do first.

### SEC-001 — Fix metric name discrepancy ✅

**Completed:** 2026-05-14

**File:** `docs/security-future-development-plan.md`, Workstream 3, line ~149

**Change made:** Corrected `taktx.excluded.topic.processing.failures{topic}` to `taktx.excluded.topic.failures{topic_group}` in the "implemented" metrics list, and added a clarifying note in the deferred section.

---

### SEC-002 — Update `security.md` last-updated date ✅

**Completed:** 2026-05-14

**Change made:** Updated `**Last updated:**` to `2026-05-14`.

---

### SEC-003 — Document internal epic labels in the roadmap ✅

**Completed:** 2026-05-14

**Change made:** "Internal epic label reference" table added to `security-future-development-plan.md` mapping Epic B2, D4, and H to their public workstream equivalents.

---

### SEC-004 — Investigate `cancelScheduledStartCommands` TODO ✅

**Completed:** 2026-05-14

**File:** `taktx-engine/src/main/java/io/taktx/engine/pd/ProcessDefinitionActivationProcessor.java`

**Change made:** Implemented `cancelScheduledStartCommands` to forward a tombstone record
(null value) for every `TimeBucket` (MINUTE, HOURLY, DAILY, WEEKLY, YEARLY) for each timer
event definition on the start event. This is the correct approach because the exact bucket
chosen at activation time is not tracked; for date-based timers the bucket depends on the
time remaining to the first execution, which can differ between activation and deactivation.
`BucketProcessor.process()` already handles null values as a store delete, and
`store.delete()` is a no-op for non-existent keys, so the fan-out over all buckets is safe.

**Test added:** `ProcessDefinitionActivationProcessorTest` — three test cases:
1. Deactivating a process definition with a timer start event forwards tombstones for **all**
   five `TimeBucket` values with the correct `processDefinitionKey` and `flowNodeId`.
2. Deactivating an already-INACTIVE definition is a no-op (early return).
3. Deactivating a process definition with a non-timer start event forwards no schedule
   tombstones.

---

## Phase 1 — Workstream 3: Security rejection visibility (Epic H)

> Target: self-contained session. No DTO changes needed; hooks already exist.  
> Prerequisite: Phase 0 complete (specifically SEC-003 to understand Epic H scope).
>
> **Architectural decision (2026-05-14):** The original plan was to add four separate Micrometer
> counters (SEC-005 to SEC-009). After analysis it was established that the `DlqReasonCode` enum
> already carries appropriate codes for every security failure category (`SIGNATURE_KEY_UNKNOWN`,
> `SIGNATURE_KEY_REVOKED`, `REPLAY_DETECTED` / `CRITICAL`, `AUTHORIZATION_FAILED`, etc.) and that
> routing the three silent-drop paths to the existing DLQ infrastructure gives more value than
> parallel counters alone (full payload inspection, single dashboard, existing alerting rules).
> SEC-005, SEC-006, and SEC-009 are superseded. SEC-007 and SEC-008 are revised to use DLQ routing.
> See decision log for full rationale.

---

### SEC-005 — Add `taktx.security.rejected.messages` counter ✅ Superseded

**Superseded:** 2026-05-14

**Reason:** `ProcessInstanceProcessor` already catches `AuthorizationTokenException` from
`EngineAuthorizationService.authorize()` and routes it to the DLQ with
`reason_code = AUTHORIZATION_FAILED`. The existing `taktx.dlq.entries{reason_code, source_topic}`
counter already provides per-rejection visibility for this path. No further code change needed.

---

### SEC-006 — Add `taktx.security.invalid.signatures` counter ✅ Superseded

**Superseded:** 2026-05-14

**Reason:** `DlqReasonCode` already defines `SIGNATURE_KEY_UNKNOWN`, `SIGNATURE_KEY_REVOKED`,
`SIGNATURE_VERIFICATION_FAILED`, `SIGNATURE_MISSING`, and `SIGNATURE_MALFORMED` — all with
`DlqSeverity.HIGH`. The two remaining silent-drop paths (replay and topic-meta) will be routed
to the DLQ via SEC-007 and SEC-008 respectively, exposing the same signal through the existing
`taktx.dlq.entries` counter. A standalone signature-failure counter adds no further diagnostic
value once DLQ routing is in place.

---

### SEC-007 — Route replay-attack detections to DLQ ✅

**Completed:** 2026-05-14

**Replaces:** original "Add `taktx.security.replay.attempts` counter" task.

**Where:** `ReplayProtectionProcessor.process()` — the duplicate-`auditId` rejection branch
(currently logs `log.warn(…)` and silently drops).

**Change:** When a duplicate `auditId` is detected within the retention window, emit a DLQ entry
with `reason_code = REPLAY_DETECTED` (`DlqSeverity.CRITICAL`) in addition to the existing warn
log. The original (rejected) message payload and headers must be included in the entry so that
operators can inspect what was being replayed.

**Rate-gate:** Emit at most one DLQ entry per `replayKey` per retention window to prevent an
attacker from flooding the DLQ topic. The replay store already records
`replayKey → first-seen-timestamp`; on the first detection write an entry. On subsequent
detections within the same window, increment the `taktx.dlq.entries` counter is sufficient
(the earlier entry already alerted the operator). Use the existing store entry as the gate.

**Semantics boundary:** A blank `auditId` rejected in STRICT mode is **not** a replay attempt —
that path remains a `log.warn()` silent drop with no DLQ entry.

**DLQ wiring:** `ReplayProtectionProcessor` currently has no `DlqPublisher` reference. Route the
entry through the processor's `context.forward()` using the existing `DlqEntryDTO` pattern so
the toplogy branch in `setupProcessInstanceStream` picks it up (mirrors other DLQ emitters
in the process-instance stream).

**Acceptance criteria:**
- First duplicate-`auditId` detection within retention window produces a `REPLAY_DETECTED` /
  `CRITICAL` DLQ entry containing the original message.
- Subsequent duplicates within the same window for the same `replayKey` do **not** produce
  additional DLQ entries (rate-gate).
- Blank `auditId` rejection in STRICT mode produces no DLQ entry (existing behaviour preserved).
- Unit test covering all three cases (first replay → DLQ, second replay → no DLQ, blank auditId → no DLQ).

**Change made:** `ReplayProtectionProcessor` now emits a `ProcessInstanceDlqEntryDTO` with
`REPLAY_DETECTED` / `CRITICAL` on the first duplicate-`auditId` seen inside the retention window,
while retaining the existing warn log. The existing replay state store is used as a durable
rate-gate by storing a negated sentinel timestamp after the first DLQ emission for a given
`replayKey`, so subsequent duplicates within the same window do not publish additional DLQ entries.
`TopologyProducer.setupProcessInstanceStream()` now splits replay-protection output into the normal
process-instance branch and a DLQ branch. Blank `auditId` rejection in STRICT mode remains a silent
drop with no DLQ entry.

**Tests added/updated:** `ReplayProtectionProcessorTest` covers all three acceptance cases; the two
`ReplayProtectionRestorationIntegrationTest` topologies were also updated to account for the mixed
`Object,Object` output type introduced by the DLQ branch.

---

### SEC-008 — Route `topic-meta-requested` failures to DLQ; counter for `schedule-commands` ✅

**Completed:** 2026-05-14

**Replaces:** original "Add `taktx.security.topic.requests.rejected` counter" task.

#### Part A — `topic-meta-requested` (external topic, DLQ routing)

**Where:** `DynamicTopicManager` — `authorizeTopicMetaRequest()` catch block
(currently logs `log.warn(…)` and calls `publishRejectedRequestedTopic()`).

**Change:** In addition to the existing `publishRejectedRequestedTopic()` call (which writes a
`null` to `topic-meta-actual` — this worker contract signal **must be preserved**), also emit a
DLQ entry. Map the `AuthorizationTokenException` message prefix to a specific `DlqReasonCode`:

| Exception cause | `DlqReasonCode` |
|---|---|
| Missing `X-TaktX-Signature` header | `SIGNATURE_MISSING` |
| Unknown key ID | `SIGNATURE_KEY_UNKNOWN` |
| Revoked key | `SIGNATURE_KEY_REVOKED` |
| Trust policy rejected | `AUTHORIZATION_FAILED` |
| (fallback) | `AUTHORIZATION_FAILED` |

**DLQ wiring:** `DynamicTopicManager` is a CDI bean called from the Kafka Streams topology via
`TopologyProducer`. The existing pattern for emitting DLQ entries from within a non-processor bean
is to have the bean return a result that the calling processor forwards. Alternatively, inject
`DlqPublisher` into `DynamicTopicManager` and forward via a shared `ProcessorContext` reference
(see how `DlqPublisher` is used elsewhere). Follow whichever pattern is already established.

**Acceptance criteria:**
- `authorizeTopicMetaRequest()` failure emits a DLQ entry with the correct `DlqReasonCode` and
  `DlqSeverity.HIGH`.
- `publishRejectedRequestedTopic()` is still called (worker contract preserved).
- Unit test: auth failure → DLQ entry emitted with correct reason code AND
  `publishRejectedRequestedTopic()` still called.

#### Part B — `schedule-commands` (engine-internal, stays DLQ-excluded)

**Where:** `ScheduleProcessor` — `authorizeScheduleCommand()` catch block (lines ~105–113;
currently `log.warn(…); return`).

**Change:** On `AuthorizationTokenException`, call the existing
`dlqObservabilityService.recordExcludedTopicFailure("schedule-commands")`. This counter is
already wired in `ScheduleProcessor` for the lower processing-exception catch block but is
**not currently called on auth failures**. No DLQ routing: `schedule-commands` is an
engine-internal topic whose records should never be operator-replayed.

**Acceptance criteria:**
- `AuthorizationTokenException` on `schedule-commands` increments
  `taktx.excluded.topic.failures{topic_group=schedule-commands}`.
- No DLQ entry is produced.
- Unit test.

**Change made:** `DynamicTopicManager` now preserves the existing
`publishRejectedRequestedTopic()` null publication to `topic-meta-actual` and additionally emits a
DLQ entry for rejected `topic-meta-requested` records. Authorization failure messages are mapped to
`SIGNATURE_MISSING`, `SIGNATURE_KEY_UNKNOWN`, `SIGNATURE_KEY_REVOKED`, or
`AUTHORIZATION_FAILED`, and the rejected `TopicMetaDTO` payload plus headers are preserved in a new
`TopicMetaDlqEntryDTO`. `ScheduleProcessor` now increments
`dlqObservabilityService.recordExcludedTopicFailure("schedule-commands")` on
`AuthorizationTokenException` and still produces no DLQ entry for that engine-internal topic.

**Tests added/updated:** `DynamicTopicManagerTest`, `DlqPublisherTest`, and
`ScheduleProcessorExcludedTopicTest`.

---

### SEC-009 — Register counters at startup ✅ Superseded

**Superseded:** 2026-05-14

**Reason:** `DlqObservabilityService.init()` already pre-registers `taktx.dlq.entries` for all
`DlqSeverity` values (including `CRITICAL`, which covers `REPLAY_DETECTED`) and
`taktx.excluded.topic.failures`. No additional pre-registration work is required.

---

### SEC-010 — Update `security-future-development-plan.md` Workstream 3 status ✅

**Completed:** 2026-05-14

**Prerequisite:** SEC-007 and SEC-008 complete.

**Change:** Update the Workstream 3 section to reflect the revised approach — DLQ routing for
external paths plus the excluded-topic counter for `schedule-commands` — rather than four
standalone security counters. Mark Workstream 3 as ✅ Complete.

**Acceptance criteria:**
- Workstream 3 status updated to reflect the actual implementation.
- The architectural decision (DLQ routing over parallel counters) is noted in the roadmap.

**Change made:** `docs/security-future-development-plan.md` now marks Workstream 3 as complete,
describes the final implementation model (DLQ routing for external rejection paths plus the
excluded-topic counter for `schedule-commands`), and records the architectural decision that
superseded the original standalone counter proposal.

---

## Phase 2 — Milestone M5: Threat model publication

> Target: doc-only session, can run in parallel with Phase 1 or after it.  
> No code changes required.

### SEC-011 — Write `docs/security-threat-model.md` ✅

**Completed:** 2026-05-15

**Required sections** (from `security-future-development-plan.md` Workstream 4):

1. Security boundaries and trust assumptions
2. What is enforced in engine code
3. What still depends on Kafka ACLs and platform controls
4. Anchored mode guarantees and limitations
5. Community mode limitations and explicit non-goals
6. Security-critical topics and data flows
7. Residual risks and compensating controls

**Acceptance criteria:**
- File exists at `docs/security-threat-model.md`.
- All seven required sections present.
- Cross-linked from `docs/security.md` (Future security roadmap section) and `SECURITY.md`.
- Consistent with runtime behaviour and current configuration options.

**Change made:** Added `docs/security-threat-model.md` as the public threat-boundary reference for
TaktX. The document covers all seven planned sections: security boundaries and trust assumptions;
what the engine enforces in code; what still depends on Kafka ACLs and platform controls; anchored
mode guarantees and limitations; community mode limitations and explicit non-goals; security-critical
topics and data flows; and residual risks plus compensating controls. The content is aligned with the
implemented security baseline in `docs/security.md` and the completed external replay-hardening slice.

---

### SEC-012 — Cross-link threat model from existing docs ✅

**Completed:** 2026-05-15

**Prerequisite:** SEC-011 complete.

**Files:** `docs/security.md` (Future security roadmap section), `SECURITY.md`

**Acceptance criteria:**
- Both files contain a link to `docs/security-threat-model.md`.
- `security-future-development-plan.md` M5 marked ✅ Done.

**Change made:** `docs/security.md` and `SECURITY.md` now both link to
`docs/security-threat-model.md`, and `docs/security-future-development-plan.md` now marks Workstream 4 /
M5 as complete.

---

## Phase 3 — Milestone M1: Replay hardening decision record

> Target: design session. Output is a decision record, not code.  
> Must complete before Phase 4.

### SEC-013 — Decide dedup identity approach ✅

**Completed:** 2026-05-14

**Decision:** Use an optional explicit `messageId` field as the canonical dedup identity on the
phase-1 signed non-entry DTOs (`ExternalTaskResponseTriggerDTO`, `UserTaskResponseTriggerDTO`,
`MessageScheduleDTO`, `TopicMetaDTO`). When `messageId` is absent or blank, M2 falls back to a
derived hash of the exact signed record identity (`X-TaktX-Signature` header value + payload
bytes as consumed from Kafka) for backward compatibility with existing producers. Stored dedup
keys must be topic-class namespaced to prevent collisions across protected paths.

**Open question:** For Workstream 1 replay hardening of signed non-entry messages, which dedup identity to use?

| Option | Pros | Cons |
|---|---|---|
| Explicit `messageId` field on signed DTOs | Human-readable, survives payload correction in DLQ replay, traceable in logs | Requires DTO changes across `ExternalTaskResponseTriggerDTO`, `UserTaskResponseTriggerDTO`, `MessageScheduleDTO`, `TopicMetaDTO`; client-side generation needed |
| Derived hash of `signature + payload bytes` | Zero DTO changes, works with existing wire format immediately | Cannot survive DLQ payload correction (corrected message gets a new hash); opaque in logs |

**Recommendation:** Prefer explicit `messageId`. The DTO additions are small and the observability and DLQ replay compatibility benefits outweigh the migration cost. A `messageId` can be optional with fallback to hash-based dedup during a transition period.

**Acceptance criteria:**
- Decision recorded in `security-future-development-plan.md` under M1 as a resolved ADR.
- Chosen approach documented with rationale and transition plan if applicable.

---

### SEC-014 — Decide phase-1 topic scope ✅

**Completed:** 2026-05-14

**Decision:** M2 phase 1 will protect the signed non-entry paths with the highest externally
reachable or operationally meaningful replay risk that are also externally originated: external-task
and user-task responses on `process-instance` (`ExternalTaskResponseTriggerDTO`,
`UserTaskResponseTriggerDTO`) and `TopicMetaDTO` on `topic-meta-requested`. Engine-internal paths
(`MessageScheduleDTO` on `schedule-commands` and continuation messages on `process-instance`) are
deferred to a later phase because they are already restricted to trusted `ENGINE` signatures and
the current release already contains a large console-facing DLQ/security change set.

**Open question:** Which signed non-entry paths to harden first in M2?

**Recommended phase-1 scope (highest risk first):**
1. `ExternalTaskResponseTriggerDTO` and `UserTaskResponseTriggerDTO` — can cause re-execution of business tasks if replayed
2. `TopicMetaDTO` on `topic-meta-requested` — operationally idempotent today but volume-sensitive, and currently architecturally awkward enough that the ingress split should be cleaned up before dedup is added

**Deferred to phase-2:**
- Engine-internal `schedule-commands` (`MessageScheduleDTO`) — useful hardening, but not part of the current external-facing release slice
- Engine-internal non-entry continuations (`ContinueFlowElementTriggerDTO`, `StartFlowElementTriggerDTO`, `EventSignalTriggerDTO`) — trusted ENGINE-only paths with natural idempotency via process state

**Acceptance criteria:**
- Phase-1 scope recorded in `security-future-development-plan.md` under M1.
- Phase-2 deferred items listed with rationale.

---

### SEC-015 — Decide retention defaults per topic class ✅

**Completed:** 2026-05-14

**Decision:** Use the following default dedup windows in the current M2 release slice: 10 minutes
for external-task / user-task responses on `process-instance` and 2 minutes for
`topic-meta-requested`. The response-dedup default intentionally aligns with the existing
`replayProtectionRetentionMs = 600_000` baseline; the shorter `topic-meta-requested` window keeps
state growth bounded for short-lived operational traffic. `schedule-commands` retains a 5 minute
candidate default for a later internal-only phase.

**Open question:** What dedup window to use per topic class?

**Starting point:**
| Topic class | Suggested window | Rationale |
|---|---|---|
| External-task / user-task responses | 10 minutes | Matches typical task completion SLAs |
| `topic-meta-requested` | 2 minutes | Idempotent; short window enough to suppress burst replays |

**Deferred candidate default:**
| Topic class | Candidate window | Rationale |
|---|---|---|
| `schedule-commands` | 5 minutes | Engine-generated, short-lived window sufficient |

**Acceptance criteria:**
- Retention defaults recorded alongside phase-1 scope in M1 decision record.

---

## Phase 4 — Milestone M2: Replay hardening implementation

> Target: implementation session. Requires Phase 3 complete.  
> Largest engineering effort in this backlog.

### SEC-016 — Add `messageId` field to phase-1 signed DTOs ✅

**Completed:** 2026-05-14

**Prerequisite:** SEC-013 decided in favour of explicit `messageId`.

**Files:**
- `taktx-shared/src/main/java/io/taktx/dto/ExternalTaskResponseTriggerDTO.java`
- `taktx-shared/src/main/java/io/taktx/dto/UserTaskResponseTriggerDTO.java`
- `taktx-shared/src/main/java/io/taktx/dto/TopicMetaDTO.java`

**Semantics:** Optional `String messageId` field. Producer-generated (UUID recommended). Engine
falls back to signature-hash dedup if absent (transition compatibility). Auto-population should be
implemented at the existing producer entry points already present in the codebase:
- `ProcessInstanceResponder` / `ExternalTaskInstanceResponder` / `UserTaskInstanceResponder` for
  external-task and user-task responses
- `ExternalTaskTopicRequester` (and equivalent topic-meta helper APIs) for `TopicMetaDTO`

**Deferred note:** If engine-internal `schedule-commands` is hardened in a later phase, add the
same optional `messageId` field to `MessageScheduleDTO` at that time rather than pulling it into
the current release scope.

**Change made:** Optional `messageId` fields added to
`ExternalTaskResponseTriggerDTO`, `UserTaskResponseTriggerDTO`, and `TopicMetaDTO`. Existing DTO
constructors remain source-compatible; explicit-messageId overloads were added for the two trigger
DTOs, and `TopicMetaDTO` now provides both 4-argument and 5-argument constructors. External helper
APIs now auto-populate `messageId` via `UUID.randomUUID().toString()` in
`ExternalTaskInstanceResponder`, `UserTaskInstanceResponder`, and `ExternalTaskTopicRequester`.

**Acceptance criteria:**
- Field added and CBOR-serializable.
- Producer-side helper APIs auto-populate `messageId` via `UUID.randomUUID()` when not set.
- Serialization round-trip test.

**Tests added/updated:** `MessageIdSerializationTest`, `ExternalTaskTopicRequesterTest`,
`UserTaskInstanceResponderTest`, and `ExternalTaskInstanceResponderTest`.

---

### SEC-017 — Add dedup state store to Kafka Streams topology ✅

**Completed:** 2026-05-15

**Prerequisite:** SEC-014 (phase-1 scope decided).

**Where:** `taktx-engine/src/main/java/io/taktx/engine/pd/Stores.java` and `TopologyProducer.java`

**Semantics:** For the current release slice, add a persistent `KeyValueStore<String, Long>` per
protected external topic class:
- external-task / user-task responses on `process-instance`
- `topic-meta-requested`

Keep the stores separate rather than shared so retention windows and purge logic stay simple.
Partitioned locally where topic routing allows.

**Expiry:** Periodic punctuator (mirrors `ReplayProtectionProcessor.purgeExpiredEntries` pattern) removes entries older than the configured retention window.

**Progress so far:**
- `Stores` enum now includes dedicated store names for `PROCESS_INSTANCE_RESPONSE_DEDUP` and
  `TOPIC_META_REQUEST_DEDUP`.
- `TopologyProducer.setupProcessInstanceStream()` now registers the persistent
  `PROCESS_INSTANCE_RESPONSE_DEDUP` state store.
- `TopologyProducer.setupTopicMetaRequestStream()` now registers the persistent
  `TOPIC_META_REQUEST_DEDUP` state store.
- Shared TTL cleanup helper `DedupStoreSupport.purgeExpiredEntries(...)` added and wired into
  `ReplayProtectionProcessor` so the future external dedup processors can reuse the same purge
  semantics.
- `DedupStoreSupportTest` added.

**Acceptance criteria:**
- Store registered in topology.
- Store name follows existing `Stores` enum pattern.
- Purge punctuator unit-tested.

---

### SEC-018 — Implement dedup processor for external-task and user-task responses ✅

**Completed:** 2026-05-14

**Prerequisite:** SEC-016, SEC-017.

**Where:** New `ProcessInstanceResponseDedupProcessor` (or extend `ReplayProtectionProcessor` to cover non-JWT paths).

**Semantics:**
- Extracts dedup key from `messageId` (or derived hash if absent).
- Checks dedup store; if present and within window → reject (log + optional DLQ).
- If absent → store key with current timestamp, forward record.

**Applies to:** `ExternalTaskResponseTriggerDTO`, `UserTaskResponseTriggerDTO`

**Change made:** Added `ProcessInstanceResponseDedupProcessor` and wired it into
`TopologyProducer.setupProcessInstanceStream()` on a dedicated external-task / user-task response branch before normal
`ProcessInstanceProcessor` handling. The dedup key is namespaced by DTO class and
`processInstanceId`, then uses `messageId` when present or a fallback SHA-256 hash of
`X-TaktX-Signature + payload bytes` when `messageId` is absent. Because external-task / user-task responses
already arrive on the UUID-keyed `process-instance` topic and the dedup key includes
`processInstanceId`, no extra repartition topic is needed in the current release slice. Duplicate
responses are logged and dropped; first-seen or expired entries pass through unchanged.

**Acceptance criteria:**
- Duplicate external-task or user-task response within retention window is rejected.
- First occurrence passes through unchanged.
- Duplicate outside the window is accepted (retention expired).
- Unit tests covering all three cases.

**Tests added/updated:** `ProcessInstanceResponseDedupProcessorTest`; `ReplayProtectionProcessorTest` also
continues to pass with the shared purge helper introduced under SEC-017.

---

### SEC-019 — Implement dedup for `schedule-commands` ⏳

**Status note (2026-05-14):** Deferred from the current release slice. Keep this task for the
next internal-only hardening phase after the console team has adapted to the current DLQ/security
surface.

**Prerequisite:** SEC-016, SEC-017.

**Where:** `ScheduleProcessor.java` or a new upstream processor.

**Semantics:** Same dedup pattern as SEC-018, applied to `MessageScheduleDTO` on `schedule-commands`.

**Acceptance criteria (deferred phase):**
- Duplicate schedule command within window is rejected with excluded-topic failure metric increment.
- Unit test.

---

### SEC-020 — Implement dedup for `topic-meta-requested` ✅

**Completed:** 2026-05-15

**Prerequisite:** SEC-016, SEC-017.

**Where:** Upstream of `DynamicTopicManager` in `TopologyProducer`, using the architecture split
documented in `security-future-development-plan.md`.

**Architecture split:**
1. Kafka Streams owns `topic-meta-requested` ingress handling: verification, dedup, and reject/
   accept routing.
2. Accepted requests are handed off to a slim broker-admin orchestration service (a refactored
   `DynamicTopicManager` or successor) that owns only `AdminClient` side effects,
   `topic-meta-actual` publication, and reconciliation scans.
3. The existing null-publication contract to `topic-meta-actual` on rejected requests must be
   preserved.

**Semantics:** Same dedup pattern, applied to `TopicMetaDTO`.

**Change made:** Added `TopicMetaRequestIngressProcessor` and wired it into a new
`TopologyProducer.setupTopicMetaRequestStream()` stage ahead of `DynamicTopicManager` side effects.
Kafka Streams now owns `topic-meta-requested` ingress authorization, request-shape validation,
duplicate suppression, and auth-failure DLQ forwarding. Accepted requests are handed off to the
slimmed `DynamicTopicManager`, which now owns broker-admin side effects, `topic-meta-actual`
publication, reconciliation scans, and the preserved null-publication contract for rejected
requests. Topic-meta dedup uses the dedicated `TOPIC_META_REQUEST_DEDUP` store, a 2-minute default
window, `messageId` when present, and a fallback SHA-256 hash of `X-TaktX-Signature + payload`
when `messageId` is absent.

**Acceptance criteria:**
- Duplicate topic-meta request within window is rejected.
- Unit test.

**Tests added/updated:** `TopicMetaRequestIngressProcessorTest` and `DynamicTopicManagerTest`.

---

### SEC-021 — Integration tests for phase-1 dedup paths ✅

**Completed:** 2026-05-15

**Prerequisite:** SEC-018 through SEC-020.

**Scope:** One integration test per current release protected path demonstrating:
1. First message passes.
2. Duplicate within window is rejected.
3. After window expiry, message is re-accepted.

**Acceptance criteria:**
- Tests pass in `securityIntegrationTest` source set (mirrors `SecurityIntegrationTest` naming).
- Covers external-task and user-task responses plus `topic-meta-requested`; `schedule-commands` test remains deferred
  with SEC-019.

**Change made:** Added `PhaseOneDedupIntegrationTest` under the dedicated
`securityIntegrationTest` source set. The class runs against an isolated Kafka broker and verifies
all current release dedup paths end-to-end at the Kafka Streams layer:
1. `ExternalTaskResponseTriggerDTO` on `process-instance` — first response forwarded, duplicate
   inside the window dropped, same response accepted again after expiry.
2. `UserTaskResponseTriggerDTO` on `process-instance` — same pass / reject / re-accept sequence.
3. `TopicMetaDTO` on `topic-meta-requested` — first request handed off to
   `DynamicTopicManager`, duplicate inside the window suppressed, same request handed off again
   after expiry, with no DLQ entry on the happy path.

**Tests added/updated:** `PhaseOneDedupIntegrationTest`.

---

### SEC-022 — Update `docs/security.md` replay protection scope section ✅

**Completed:** 2026-05-15

**Prerequisite:** SEC-018 through SEC-021 complete.

**Change:** Update the "Replay protection scope" section to accurately reflect:
- Which externally originated paths now have dedup protection (current release list).
- Which paths remain outside dedup scope and why.
- Dedup window defaults per protected path.

**Acceptance criteria:**
- Section accurately describes protected and unprotected paths post-M2.
- `security-future-development-plan.md` M2 marked ✅ Done.

**Change made:** `docs/security.md` now distinguishes the two live protection layers: durable
JWT entry-command replay protection (`auditId`) and fixed-window phase-1 dedup for externally
originated signed non-entry paths. The replay-protection scope section now lists the current
protected paths (`ExternalTaskResponseTriggerDTO`, `UserTaskResponseTriggerDTO`, `TopicMetaDTO`),
their default windows (10 minutes for process-instance responses, 2 minutes for
`topic-meta-requested`), the `messageId` / signature+payload fallback identity model, and the
still-deferred internal paths (`schedule-commands`, engine-internal continuations). The roadmap in
`security-future-development-plan.md` now marks M2 done for the current release slice.

---

## Phase 5 — Deferred internal replay-hardening follow-on

> Target: later internal-only hardening session after the current external replay/DLQ slice has
> settled operationally.

### SEC-023 — Implement dedup for engine-internal continuations on `process-instance` ⏳

**Status note (2026-05-15):** This was intentionally deferred from the external-facing M2 slice,
but the remaining internal continuation paths should now be tracked explicitly rather than only in
roadmap prose.

**Prerequisite:** Follow-on task after SEC-019; relies on the dedup-store and topology patterns
established by SEC-017 through SEC-018.

**Where:** `TopologyProducer.setupProcessInstanceStream()` on a new engine-internal continuation
branch upstream of `ProcessInstanceProcessor`, or a dedicated processor adjacent to the existing
response-dedup branch.

**Applies to:**
- `ContinueFlowElementTriggerDTO`
- `StartFlowElementTriggerDTO`
- `EventSignalTriggerDTO`

**Semantics:** Apply the same fixed-window duplicate-suppression model used in SEC-018, but scoped
to the trusted `ENGINE`-signed continuation messages that stay on `process-instance`.

**Identity model (proposed):**
- Stored dedup keys must be namespaced by DTO class and `processInstanceId`.
- For the first internal-only phase, use a derived hash of `X-TaktX-Signature + payload bytes` as
  the continuation identity.
- If later operational experience shows a need for human-readable continuity across replay / repair
  workflows, consider a separate explicit continuation/message ID ADR rather than blocking the
  initial internal-only hardening step on DTO changes.

**Why this remains separate from SEC-018:** These messages share the `process-instance` topic with
entry commands and external-task/user-task responses, but they represent engine-generated follow-on
execution rather than external ingress. They therefore need their own branching and test matrix even
if they reuse the same dedup-store support primitives.

**Acceptance criteria:**
- Duplicate engine-internal continuation within the configured window is rejected.
- First occurrence passes through unchanged.
- Duplicate outside the window is accepted again.
- Unit tests cover all three DTO classes (or a parameterized equivalent).
- `docs/security.md` replay-protection scope section updated if the deferred continuation paths move
  into protected status.

---

## Milestone tracker

| Milestone | Description | Tasks | Status |
|---|---|---|---|
| Phase 0 | Housekeeping | SEC-001 ✅ SEC-002 ✅ SEC-003 ✅ SEC-004 ✅ | ✅ Complete |
| Phase 1 | Security rejection visibility (Epic H / Workstream 3) | SEC-005 ✅ SEC-006 ✅ SEC-007 ✅ SEC-008 ✅ SEC-009 ✅ SEC-010 ✅ | ✅ Complete |
| Phase 2 | Threat model (M5) | SEC-011 ✅ SEC-012 ✅ | ✅ Complete |
| Phase 3 | Replay hardening decisions (M1) | SEC-013 ✅ SEC-014 ✅ SEC-015 ✅ | ✅ Complete |
| Phase 4 | Replay hardening implementation (M2) | SEC-016 ✅ SEC-017 ✅ SEC-018 ✅ SEC-019 ⏳ deferred SEC-020 ✅ SEC-021 ✅ SEC-022 ✅ | ✅ Complete for current external release slice |
| Phase 5 | Deferred internal replay hardening follow-on | SEC-023 ⏳ | ⏳ Pending |

---

## Decision log

### 2026-05-15 — SEC-023 added to make deferred continuation hardening explicit

- Added `SEC-023` so engine-internal continuation dedup on `process-instance` is tracked as an
  explicit follow-up task rather than only as deferred roadmap prose.
- The deferred continuation scope is now spelled out as `ContinueFlowElementTriggerDTO`,
  `StartFlowElementTriggerDTO`, and `EventSignalTriggerDTO`.
- The current recommendation is to reuse the existing fixed-window dedup pattern with DTO-class +
  `processInstanceId` namespacing and an initial `X-TaktX-Signature + payload` identity model.

### 2026-05-15 — SEC-011 and SEC-012 completed; M5 threat model published

- Added `docs/security-threat-model.md` as the public threat model for the current security baseline.
- The threat model now documents security boundaries, trust assumptions, enforced controls,
  Kafka/platform dependencies, anchored and community mode limitations, security-critical topics,
  residual risks, and compensating controls.
- `docs/security.md` and `SECURITY.md` now cross-link the threat model.
- `docs/security-future-development-plan.md` now marks Workstream 4 / M5 complete.

### 2026-05-15 — SEC-022 completed; M2 docs aligned with implementation

- `docs/security.md` now reflects the live replay-hardening model rather than the earlier
  entry-command-only scope.
- The public docs now distinguish durable JWT `auditId` replay protection from the newer
  fixed-window dedup for `ExternalTaskResponseTriggerDTO`, `UserTaskResponseTriggerDTO`, and
  `TopicMetaDTO`.
- Default windows are documented as 10 minutes for the external process-instance response paths and
  2 minutes for `topic-meta-requested`.
- `security-future-development-plan.md` now marks M2 complete for the current external release
  slice; `schedule-commands` remains intentionally deferred under SEC-019.

### 2026-05-15 — SEC-021 completed

- Added `PhaseOneDedupIntegrationTest` in `securityIntegrationTest`.
- The integration suite now covers external-task responses, user-task responses, and
  `topic-meta-requested` with the full first-pass / duplicate-reject / post-expiry re-accept
  sequence against a real Kafka broker.
- `schedule-commands` remains intentionally deferred with SEC-019 because the current release slice
  hardens only the external-facing phase-1 paths.

### 2026-05-15 — SEC-017 completed and SEC-020 implemented

- `topic-meta-requested` ingress now runs through Kafka Streams before broker-admin side effects.
- New `TopicMetaRequestIngressProcessor` performs authorization, request validation, duplicate
  suppression, and auth-failure DLQ forwarding for `TopicMetaDTO` records.
- `DynamicTopicManager` was slimmed down to accepted-request side effects, reconciliation, and the
  preserved `topic-meta-actual` null-publication contract for rejections.
- `TopologyProducer` now registers the `TOPIC_META_REQUEST_DEDUP` state store and wires the new
  ingress processor with a 2-minute default dedup window.
- Focused tests added for auth rejection, validation rejection, dedup, fallback identity, and
  expiry handling on the `topic-meta-requested` path.

### 2026-05-14 — SEC-013, SEC-014, and SEC-015 implemented; Phase 3 complete

- Chosen dedup identity: optional explicit `messageId` on the phase-1 signed non-entry DTOs, with
  a transition fallback to a derived `X-TaktX-Signature + payload` hash for legacy producers.
- M2 release scope narrowed to externally originated signed non-entry paths: external-task and
  user-task responses plus `topic-meta-requested`; internal paths remain deferred.
- Default dedup windows fixed at 10 minutes for external-task and user-task responses and 2 minutes for
  `topic-meta-requested`; `schedule-commands` keeps a 5 minute candidate default for a later phase.
- `topic-meta-requested` will use a topology-ingress / broker-admin orchestration split so dedup and
  trust checks stay in Kafka Streams while admin side effects remain isolated.
- `security-future-development-plan.md` updated with the refined M1 ADR and architecture split;
  Phase 4 is now unblocked for the external-facing slice.

### 2026-05-14 — Phase 1 replanned: counters replaced with DLQ routing

**Decision:** Replace the four standalone Micrometer security counters (original SEC-005 to
SEC-009) with direct DLQ routing for the two silent-drop paths, plus a single excluded-topic
counter increment for the engine-internal `schedule-commands` path.

**Rationale:**
- Three of the four rejection paths never reach the DLQ today:
  `schedule-commands` auth failures, `topic-meta-requested` auth failures, and replay detections
  all `log.warn()` and silently drop.
- `DlqReasonCode` already defines appropriate codes for every failure category
  (`REPLAY_DETECTED` / `CRITICAL`, `SIGNATURE_KEY_UNKNOWN` / `HIGH`,
  `SIGNATURE_KEY_REVOKED` / `HIGH`, `AUTHORIZATION_FAILED` / `MEDIUM`).
- DLQ routing gives more operator value than counters alone: full payload inspection,
  single dashboard, existing alerting infrastructure, and replay-safety enforcement (DLQ-011)
  prevents accidental re-replay of detected attacks.
- The `process-instance` path (SEC-005) already routes to DLQ — nothing to change there.
- Counter pre-registration (SEC-009) is already handled by `DlqObservabilityService.init()`.

**Tasks closed as superseded:** SEC-005, SEC-006, SEC-009.  
**Tasks revised:** SEC-007 (replay → DLQ), SEC-008 (topic-meta → DLQ; schedule-commands → excluded-topic counter).  
**Tasks unchanged in that replanning step:** SEC-010 (roadmap doc update), SEC-011 through SEC-022.

### 2026-05-14 — SEC-004 implemented; Phase 0 complete

- `cancelScheduledStartCommands` implemented: tombstones fanned out over all `TimeBucket` values.
- Rationale: for date-based timers the bucket used at activation depends on remaining time to first
  execution and cannot be reliably recomputed at deactivation time; covering all buckets is safe
  because `BucketProcessor.store.delete()` is a no-op for non-existent keys.
- Three unit tests added in `ProcessDefinitionActivationProcessorTest`.
- Phase 0 fully complete. Next: Phase 1 — SEC-005 through SEC-010 (security rejection visibility).

### 2026-05-14 — SEC-007, SEC-008, and SEC-010 implemented; Phase 1 complete

- Replay detections on JWT entry commands now emit a rate-gated `REPLAY_DETECTED` / `CRITICAL` DLQ
  entry on first duplicate detection per retention window.
- `topic-meta-requested` authorization failures now emit DLQ entries with mapped signature /
  authorization reason codes while preserving the `topic-meta-actual` null publication contract.
- `schedule-commands` authorization failures now increment the excluded-topic failure counter and
  remain DLQ-excluded.
- `security-future-development-plan.md` updated to reflect the final Workstream 3 implementation
  model; Phase 1 is now complete.

### 2026-05-14 — Backlog created; Phase 0 partially completed

- Full code audit performed across `taktx-engine` and `taktx-shared` security and DLQ surfaces.
- DLQ Epics E1–E5 confirmed complete.
- Security baseline (Ed25519, JWT, replay protection, anchored trust) confirmed complete.
- Remaining work identified: Workstream 1 (M1/M2), Workstream 3 deferred counters, M5 threat model, and housekeeping items.
- This backlog created as the task-level tracking companion to `security-future-development-plan.md`.
- SEC-001 (metric name fix), SEC-002 (date update), SEC-003 (epic label mapping) completed in the same session.






