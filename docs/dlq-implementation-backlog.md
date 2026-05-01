# DLQ Implementation Backlog

Date: 2026-05-01  
Source: `docs/dlq-engine-design.md` (including Part 6D disposition)

## Scope Baseline (Final)

DLQ applies only to **external execution ingress** topics that drive BPMN/DMN execution and can be safely corrected/replayed:
- `process-instance`
- `message-event`
- `signals`
- `process-definition-activation`
- `dmn-definition-activation`
- `definitions`
- `dmn-definitions`
- `usertasks-response`

Excluded from DLQ:
- engine-internal topics such as `schedule-commands`
- control-plane/security topics such as `topic-meta-*`, `taktx-configuration`, `taktx-signing-keys`
- projections/materialized views such as `instance-update`, `usertasks`, `xml-by-*`

Excluded topics are handled via incident/alerting, structured logs, audit events, or rebuild/regeneration rather than replay.

## Progress Snapshot

- 2026-05-01 (checkpoint 1):
  - Added per-surface DLQ/replay topic constants in `Topics` and store constants in `Stores`.
  - Added new shared DTO/enums: `DlqEnvelope`, `DlqLineageDTO`, `DlqReplayCommand`, `DlqReplayResult`, `DlqReasonCode`, `DlqSeverity`, `DlqCaptureStage`, `ReplayValidationPolicy`.
  - Fixed engine compile blocker in `ProcessInstanceProcessor` (incomplete DLQ constructor).
  - Ran fast unit tests and engine compile successfully.
- 2026-05-01 (checkpoint 2):
  - Aligned scope with final DLQ decision.
  - Removed schedule/topic-meta specific DLQ constants from `Topics`/`Stores`.
  - Re-prioritized backlog away from schedule/topic-meta replay.
- 2026-05-01 (checkpoint 3):
  - Re-verified compile + shared tests after scope-alignment edits — `BUILD SUCCESSFUL`.
  - Confirmed `DlqEnvelope`, `DlqLineageDTO`, `DlqReasonCode`, `DlqSeverity`, `DlqCaptureStage`, `DlqReplayCommand`, `DlqReplayResult`, `ReplayValidationPolicy` all exist with passing tests → **DLQ-002 closed**.
  - Remaining gap in DLQ-001: only `process-instance` DLQ/replay/results topic constants added; the other 7 final-scope surfaces (`message-event`, `signals`, `usertasks-response`, `definitions`, `process-definition-activation`, `dmn-definitions`, `dmn-definition-activation`) still need DLQ topic constants.
  - Legacy `DlqEntryDTO`, `DlqEntryKey` classes still present (not yet removed) → DLQ-004 stays `In Progress`.
- 2026-05-01 (checkpoint 4):
  - Added DLQ/replay/replay-results topic constants for all 7 remaining ingress surfaces in `Topics.java`.
  - Added `dlqTopics_allHaveDeleteCleanupPolicy` test — all 24 new DLQ topic constants verified as DELETE policy.
  - Engine compile + all shared tests pass → **DLQ-001 closed**.
  - Noted `DLQ_PROCESS_INSTANCE` in `Stores.java` is unused (append-only topics don't need state stores); will be removed as part of DLQ-004 cleanup.
  - `DlqReplayCommand`, `DlqReplayResult`, `ReplayValidationPolicy` (STRICT/OPERATOR_OVERRIDE) were created in DLQ-002 → **DLQ-009 closed**.
- 2026-05-01 (checkpoint 5):
  - Simplified DLQ topology: replaced 24 per-surface topic constants with 3 unified namespace-scoped constants (`dlq`, `dlq.replay`, `dlq.replay-results`).
  - Rationale: `DlqEnvelope.sourceTopic` carries per-surface routing; separate topics add overhead with no functional benefit at current scale (future split criteria documented).
  - Removed `DLQ_PROCESS_INSTANCE` and legacy `DLQ` store from `Stores.java`; replaced broken legacy global-table `setupDlq` in `TopologyProducer` with a TODO stub.
  - Updated `TopicsTest` — unified topic name + DELETE policy assertions pass.
  - `BUILD SUCCESSFUL` — compile + shared tests green.
- 2026-05-01 (checkpoint 6):
  - Implemented `taktx-engine/src/main/java/io/taktx/engine/dlq/DlqPublisher.java` to map legacy `DlqEntryDTO` values to unified `DlqEnvelope` records (source topic, reason code, severity, capture stage, headers, message hash).
  - Wired all three legacy DLQ branches in `TopologyProducer` (`setupNewDefinitionStream`, `setupProcessInstanceStream`, `setupMessageStream`) to publish `String` key + `DlqEnvelope` value to `Topics.DLQ` using `DLQ_ENVELOPE_SERDE`.
  - Added engine instance id tagging helper in `TopologyProducer` and removed legacy `DlqReplayProcessor`/global-table DLQ setup dependency.
  - Added focused unit test `taktx-engine/src/test/java/io/taktx/engine/dlq/DlqPublisherTest.java`.
  - Verification: `:taktx-engine:compileJava` and `:taktx-shared:test --tests io.taktx.TopicsTest` are green.
  - Known baseline issue: `:taktx-engine:compileTestJava` fails in pre-existing unrelated security tests (`ProcessInstanceTriggerEnvelope` constructor mismatches), which currently blocks execution of the new engine unit test in CI/local test task.
- 2026-05-01 (checkpoint 7):
  - Continued DLQ-004 cleanup after unified-topic migration:
    - removed dead `setupDlq(...)` scaffolding and legacy `DLQ_KEY_SERDE` from `taktx-engine/src/main/java/io/taktx/engine/generic/TopologyProducer.java`.
    - switched all three DLQ branches to route by `value instanceof DlqEntryDTO`.
    - updated `taktx-engine/src/main/java/io/taktx/engine/pi/Forwarder.java` to emit legacy DLQ values with `null` key (no `DlqEntryKey`).
    - deleted unused `taktx-engine/src/main/java/io/taktx/engine/pd/DlqReplayProcessor.java`.
  - Validation now includes test compilation and DLQ unit test execution:
    - `:taktx-engine:compileJava`
    - `:taktx-engine:compileTestJava`
    - `:taktx-engine:test --tests io.taktx.engine.dlq.DlqPublisherTest`
    - `:taktx-shared:test --tests io.taktx.TopicsTest`
    - all successful.
  - Previous baseline blocker (`ProcessInstanceTriggerEnvelope` test-constructor mismatches) is resolved.
- 2026-05-01 (checkpoint 8):
  - Removed fully-unused legacy class `taktx-shared/src/main/java/io/taktx/dto/DlqEntryKey.java`.
  - Re-validated cleanup path with:
    - `:taktx-shared:compileJava`
    - `:taktx-engine:compileJava`
    - `:taktx-engine:compileTestJava`
    - `:taktx-engine:test --tests io.taktx.engine.dlq.DlqPublisherTest`
    - all successful.
  - DLQ-004 remains in progress because `DlqEntryDTO` subclasses are still used as legacy runtime intermediates before full direct-`DlqEnvelope` capture in processors.

This backlog is structured for sprint/Jira tracking and follows the agreed constraints:
- append-only DLQ coverage for external execution ingress only
- lineage and immutable replay chain
- replay policies: `STRICT`, `OPERATOR_OVERRIDE`
- replay signing by ENGINE role key
- destination-topic safety checks enforced in engine
- logical dedup semantics (append-only may contain duplicates)

---

## Tracking Legend

- Priority: `P0` critical path, `P1` high value, `P2` follow-up
- Risk: `High`, `Medium`, `Low`
- Status: `Todo`, `In Progress`, `Blocked`, `Done`

---

## Epic E1 - Foundation (Topics, Envelope, Publisher)

| ID | Pri | Status | Task | Depends On | Risk |
|---|---|---|---|---|---|
| DLQ-001 | P0 | Done | Define namespace-scoped DLQ/replay/replay-results topics (`dlq`, `dlq.replay`, `dlq.replay-results`) with DELETE cleanup in `Topics`; remove legacy per-surface constants and unused DLQ stores. | - | High |
| DLQ-002 | P0 | Done | Introduce `DlqEnvelope` + metadata (lineage, source identity, severity, schema, captureStage) in `taktx-shared`. | DLQ-001 | Medium |
| DLQ-003 | P0 | Done | Implement `DlqPublisher` and wire unified `dlq` sink into topology; replace legacy `setupDlq` stub and `DlqEntryKey`/`DlqEntryDTO` branch paths. | DLQ-001, DLQ-002 | High |
| DLQ-004 | P0 | In Progress | Remove legacy `DlqEntryDTO` runtime path and fix remaining compile blockers in `ProcessInstanceProcessor`. | DLQ-003 | Medium |

### Acceptance Criteria (E1)
- [x] Three unified DLQ topics (`dlq`, `dlq.replay`, `dlq.replay-results`) with DELETE cleanup policy exist in `Topics`.
- [x] Publisher emits `DlqEnvelope`-typed records to `dlq` with no silent drop paths.
- [x] Envelope includes `sourceTopic/sourcePartition/sourceOffset/sourceMessageHash`.
- [x] Envelope includes `schemaVersion/decoderVersion` and `severity`.

---

## Epic E2 - Rejection Capture Coverage

| ID | Pri | Status | Task | Depends On | Risk |
|---|---|---|---|---|---|
| DLQ-005 | P0 | Todo | Capture `process-instance` decode/signature/auth failures into DLQ. | DLQ-003 | High |
| DLQ-006 | P0 | Todo | Capture external execution event ingress failures for `message-event`, `signals`, and `usertasks-response`. | DLQ-003 | High |
| DLQ-007 | P0 | Todo | Capture definition/deployment ingress failures for `definitions`, `process-definition-activation`, `dmn-definitions`, and `dmn-definition-activation`. | DLQ-003 | High |
| DLQ-008 | P1 | Todo | Ensure DLQ records include `captureStage` and document duplicate semantics for included ingress topics. | DLQ-005, DLQ-006, DLQ-007 | Medium |
| DLQ-008A | P1 | Todo | Define non-DLQ handling for excluded topics (`schedule-commands`, control-plane/security topics, projections). | DLQ-003 | Medium |

### Acceptance Criteria (E2)
- [ ] All included ingress surfaces emit DLQ entries with stable reason codes.
- [ ] `captureStage` is present where relevant (`DESERIALIZER`, `PROCESSOR`, `ERROR_HANDLER`).
- [ ] Documented behavior: DLQ append-only may contain duplicates; dedup is logical in tooling.
- [ ] Excluded topics have explicit incident/audit/rebuild handling.

---

## Epic E3 - Replay Pipeline and Safety

| ID | Pri | Status | Task | Depends On | Risk |
|---|---|---|---|---|---|
| DLQ-009 | P0 | Done | Add `DlqReplayCommand`/`DlqReplayResult` with `STRICT` and `OPERATOR_OVERRIDE`. | DLQ-002 | High |
| DLQ-010 | P0 | Todo | Implement single replay processor consuming `dlq.replay`; route to correct ingress surface using `DlqEnvelope.sourceTopic`. | DLQ-009 | High |
| DLQ-011 | P0 | Todo | Enforce destination topic safety in engine (surface + tenant/namespace + whitelist). | DLQ-010 | High |
| DLQ-012 | P0 | Todo | Enforce replay signing by ENGINE role key; include `replaySigner` and `replaySignatureKeyId`. | DLQ-010 | High |
| DLQ-013 | P1 | Todo | Implement schema compatibility behavior: `STRICT` fail, `OPERATOR_OVERRIDE` warning+audit. | DLQ-010 | Medium |
| DLQ-014 | P1 | Todo | Add dry-run replay execution path (validate decode/authz, no side effects). | DLQ-010 | Medium |

### Acceptance Criteria (E3)
- [ ] Replay output always forms lineage chain (`X-DLQ-Lineage-Ref`, `X-DLQ-Correction-Id`, `X-DLQ-Source-Offset`).
- [ ] Destination mismatch is rejected and audited.
- [ ] Replayed messages are newly signed with ENGINE key provenance.
- [ ] `STRICT` vs `OPERATOR_OVERRIDE` behavior is deterministic and tested.
- [ ] Dry-run returns pass/fail reasons without publish/execute.

---

## Epic E4 - Observability and Operations

| ID | Pri | Status | Task | Depends On | Risk |
|---|---|---|---|---|---|
| DLQ-015 | P1 | Todo | Implement reason->severity mapping and include severity in logs/metrics for included DLQ surfaces. | DLQ-005..DLQ-007 | Medium |
| DLQ-016 | P1 | Todo | Implement alert policy: `CRITICAL` immediate page, `HIGH` threshold alert, `MEDIUM/LOW` dashboard. | DLQ-015 | Medium |
| DLQ-017 | P1 | Todo | Emit replay-result audit details (compatibility decision, signer, override reason). | DLQ-010, DLQ-013 | Medium |
| DLQ-018 | P2 | Todo | Document retention and storage policy (enforced retention + future cold archive/tiered storage). | DLQ-015 | Low |
| DLQ-018A | P1 | Todo | Add structured logging/metrics/incident handling for excluded engine-internal and control-plane topics. | DLQ-008A | Medium |

### Acceptance Criteria (E4)
- [ ] Alert behavior matches severity policy.
- [ ] Replay audit records include signer and schema compatibility outcome.
- [ ] Retention policy is explicit per environment.
- [ ] Excluded topics have observable non-DLQ failure handling.

---

## Epic E5 - Console Contract and Premium Split

| ID | Pri | Status | Task | Depends On | Risk |
|---|---|---|---|---|---|
| DLQ-019 | P1 | Todo | Finalize engine-console contract for explorer, payload inspector, correction UI, dry-run, lineage view for included DLQ surfaces. | DLQ-014, DLQ-017 | Medium |
| DLQ-020 | P2 | Todo | Split feature matrix for Community vs Premium ops features and docs. | DLQ-019 | Low |

### Acceptance Criteria (E5)
- [ ] Console integration contract includes dry-run and lineage visualization.
- [ ] Community vs Premium boundary is documented and consistent with security model.

---

## Test Backlog (Track Separately)

| ID | Pri | Status | Test Scope | Depends On |
|---|---|---|---|---|
| DLQ-T01 | P0 | Done | Envelope/reason-severity baseline tests in `taktx-shared` | DLQ-002 |
| DLQ-T02 | P0 | Todo | Rejection capture tests for included DLQ ingress topics | DLQ-005..DLQ-007 |
| DLQ-T03 | P0 | Todo | Replay policy tests (`STRICT`, `OPERATOR_OVERRIDE`) | DLQ-009..DLQ-010 |
| DLQ-T04 | P0 | Todo | Destination safety and engine signing provenance tests | DLQ-011, DLQ-012 |
| DLQ-T05 | P1 | Todo | Schema compatibility tests (`STRICT` fail, override warn/audit) | DLQ-013 |
| DLQ-T06 | P1 | Todo | Dry-run no-side-effects test coverage | DLQ-014 |
| DLQ-T07 | P1 | Todo | Alerting behavior by severity | DLQ-016 |
| DLQ-T08 | P1 | Todo | Excluded-topic handling tests (incident/log/metric paths, no replay) | DLQ-008A, DLQ-018A |

---

## Suggested Milestones

- Milestone A (P0 core): `DLQ-001`..`DLQ-007`, `DLQ-009`..`DLQ-012`, `DLQ-T01`..`DLQ-T04`
- Milestone B (safety and UX hardening): `DLQ-008`, `DLQ-008A`, `DLQ-013`..`DLQ-018A`, `DLQ-T05`..`DLQ-T08`
- Milestone C (ops packaging): `DLQ-019`..`DLQ-020`

---

## Definition of Done (Global)

- [ ] Code merged with green CI (unit + integration)
- [ ] Replay safety checks and signing guarantees validated
- [ ] Alerting policies deployed and tested in staging
- [ ] Docs updated in `docs/dlq-engine-design.md` and runbook docs
- [ ] Canary rollout completed without unresolved `CRITICAL` incidents
