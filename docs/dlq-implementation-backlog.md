# DLQ Implementation Backlog

Date: 2026-05-01  
Source: `docs/dlq-engine-design.md` (including Part 6D disposition)

This backlog is structured for sprint/Jira tracking and follows the agreed constraints:
- append-only per-surface DLQ topics
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
| DLQ-001 | P0 | Todo | Replace compacted `DLQ/DLQ_REPLAY` with per-surface append-only topics in `Topics` and `Stores`. | - | High |
| DLQ-002 | P0 | Todo | Introduce `DlqEnvelope` + metadata (lineage, source identity, severity, schema, captureStage) in `taktx-shared`. | DLQ-001 | Medium |
| DLQ-003 | P0 | Todo | Implement `DlqPublisher` and wire topic routing/serde usage in topology. | DLQ-001, DLQ-002 | High |
| DLQ-004 | P0 | Todo | Remove legacy `DlqEntryDTO` runtime path and fix remaining compile blockers in `ProcessInstanceProcessor`. | DLQ-003 | Medium |

### Acceptance Criteria (E1)
- [ ] `DLQ` is append-only per surface; no compacted global DLQ path remains in runtime flow.
- [ ] Envelope includes `sourceTopic/sourcePartition/sourceOffset/sourceMessageHash`.
- [ ] Envelope includes `schemaVersion/decoderVersion` and `severity`.
- [ ] Publisher emits reason-coded entries without silent drop paths.

---

## Epic E2 - Rejection Capture Coverage

| ID | Pri | Status | Task | Depends On | Risk |
|---|---|---|---|---|---|
| DLQ-005 | P0 | Todo | Capture process-instance decode/signature/auth failures into DLQ. | DLQ-003 | High |
| DLQ-006 | P0 | Todo | Capture schedule-command deserialization/signature failures into DLQ. | DLQ-003 | High |
| DLQ-007 | P1 | Todo | Capture topic-meta-requested rejections (authz/validation) into DLQ. | DLQ-003 | Medium |
| DLQ-008 | P1 | Todo | Ensure deserializer/error-handler records include `captureStage` and duplicate semantics docs/fields. | DLQ-005, DLQ-006 | Medium |

### Acceptance Criteria (E2)
- [ ] All three ingress surfaces emit DLQ entries with stable reason codes.
- [ ] `captureStage` is present where relevant (`DESERIALIZER`, `PROCESSOR`, `ERROR_HANDLER`).
- [ ] Documented behavior: DLQ append-only may contain duplicates; dedup is logical in tooling.

---

## Epic E3 - Replay Pipeline and Safety

| ID | Pri | Status | Task | Depends On | Risk |
|---|---|---|---|---|---|
| DLQ-009 | P0 | Todo | Add `DlqReplayCommand`/`DlqReplayResult` with `STRICT` and `OPERATOR_OVERRIDE`. | DLQ-002 | High |
| DLQ-010 | P0 | Todo | Implement per-surface replay processors (replace empty replay processor path). | DLQ-009 | High |
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
| DLQ-015 | P1 | Todo | Implement reason->severity mapping and include severity in logs/metrics. | DLQ-005..DLQ-007 | Medium |
| DLQ-016 | P1 | Todo | Implement alert policy: `CRITICAL` immediate page, `HIGH` threshold alert, `MEDIUM/LOW` dashboard. | DLQ-015 | Medium |
| DLQ-017 | P1 | Todo | Emit replay-result audit details (compatibility decision, signer, override reason). | DLQ-010, DLQ-013 | Medium |
| DLQ-018 | P2 | Todo | Document retention and storage policy (enforced retention + future cold archive/tiered storage). | DLQ-015 | Low |

### Acceptance Criteria (E4)
- [ ] Alert behavior matches severity policy.
- [ ] Replay audit records include signer and schema compatibility outcome.
- [ ] Retention policy is explicit per environment.

---

## Epic E5 - Console Contract and Premium Split

| ID | Pri | Status | Task | Depends On | Risk |
|---|---|---|---|---|---|
| DLQ-019 | P1 | Todo | Finalize engine-console contract for explorer, payload inspector, correction UI, dry-run, lineage view. | DLQ-014, DLQ-017 | Medium |
| DLQ-020 | P2 | Todo | Split feature matrix for Community vs Premium ops features and docs. | DLQ-019 | Low |

### Acceptance Criteria (E5)
- [ ] Console integration contract includes dry-run and lineage visualization.
- [ ] Community vs Premium boundary is documented and consistent with security model.

---

## Test Backlog (Track Separately)

| ID | Pri | Status | Test Scope | Depends On |
|---|---|---|---|---|
| DLQ-T01 | P0 | Todo | Envelope serde + reason/severity mapping + source identity composition | DLQ-002 |
| DLQ-T02 | P0 | Todo | Rejection capture tests for process-instance/schedule/topic-meta | DLQ-005..DLQ-007 |
| DLQ-T03 | P0 | Todo | Replay policy tests (`STRICT`, `OPERATOR_OVERRIDE`) | DLQ-009..DLQ-010 |
| DLQ-T04 | P0 | Todo | Destination safety and engine signing provenance tests | DLQ-011, DLQ-012 |
| DLQ-T05 | P1 | Todo | Schema compatibility tests (`STRICT` fail, override warn/audit) | DLQ-013 |
| DLQ-T06 | P1 | Todo | Dry-run no-side-effects test coverage | DLQ-014 |
| DLQ-T07 | P1 | Todo | Alerting behavior by severity | DLQ-016 |

---

## Suggested Milestones

- Milestone A (P0 core): `DLQ-001`..`DLQ-006`, `DLQ-009`..`DLQ-012`, `DLQ-T01`..`DLQ-T04`
- Milestone B (safety and UX hardening): `DLQ-013`..`DLQ-017`, `DLQ-T05`..`DLQ-T07`
- Milestone C (ops packaging): `DLQ-018`..`DLQ-020`

---

## Definition of Done (Global)

- [ ] Code merged with green CI (unit + integration)
- [ ] Replay safety checks and signing guarantees validated
- [ ] Alerting policies deployed and tested in staging
- [ ] Docs updated in `docs/dlq-engine-design.md` and runbook docs
- [ ] Canary rollout completed without unresolved `CRITICAL` incidents

