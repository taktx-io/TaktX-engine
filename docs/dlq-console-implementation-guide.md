# TaktX Console Implementation Handoff — Security + DLQ

**Document Version:** 1.0  
**Date:** 2026-05-15  
**Status:** Implementation-ready handoff for the console team  
**Audience:** Console engineers, product owners, QA, platform operators  
**Authoritative inputs:**
- `docs/dlq-console-contract.md`
- `docs/dlq-engine-design.md`
- `docs/dlq-feature-matrix.md`
- `docs/dlq-retention-policy.md`
- `docs/security.md`
- `docs/security-threat-model.md`
- `docs/security-future-development-plan.md`
- Live DTOs and client APIs in `taktx-client/` and `taktx-shared/`

---

## 1. Purpose

This document is the **console-team handoff** for everything that is now live in TaktX related to:

1. **DLQ ingestion and triage**
2. **Security-related rejection visibility**
3. **Incident → DLQ correlation**
4. **Operator replay and dry-run flows**
5. **Security-aware UI/UX constraints**
6. **Operational observability around DLQ and excluded-topic failures**

The goal is to let the console team implement the required backend services and frontend UI **without having to reconstruct the behavior from multiple engine docs and code files**.

This guide does **not** replace the lower-level protocol documents. Instead, it translates them into an implementation plan for the console.

---

## 2. Executive summary

### What is already live in the engine

The engine already provides:

- A single append-only `<prefix>.dlq` topic for all DLQ captures
- A single `<prefix>.dlq.replay` topic for operator replay commands
- A single `<prefix>.dlq.replay-results` topic for replay outcomes
- Structured `DlqEnvelope`, `DlqReplayCommand`, and `DlqReplayResult` DTOs
- `taktx-client` APIs for consuming DLQ entries and replay results and for publishing replay commands
- Incident back-references from `ProcessInstanceUpdateDTO.incidentInfoDTO.dlqEntryRef`
- Engine-side safety enforcement for replay destination and replay signing
- Prometheus metrics and alert rules for DLQ and excluded-topic failures

### What the console team needs to build

The console should implement:

- A **DLQ Explorer** with filtering, search, deduplication, and detail views
- An **Incident → DLQ navigation path**
- A **Payload Inspector / Correction UI**
- A **Dry-run then live replay workflow**
- A **Replay result timeline / audit view**
- A **Lineage view** linking original failure, incident, replay attempt, and replay outcome
- A **Security-aware presentation layer** that masks sensitive headers, distinguishes critical reason codes, and respects engine-side trust boundaries
- Optional premium extensions such as batch replay, approvals workflow, and RBAC audit dashboards

### Most important implementation principle

The console is **not** a trust authority.

The engine remains authoritative for:

- destination topic safety
- schema compatibility enforcement
- replay signing (`tx-sig` is always replaced by the engine)
- trust-policy enforcement
- security validation of inbound messages

The console is authoritative for:

- operator workflow
- presentation
- local indexing / deduplication / search
- approval UX
- payload editing UX
- audit surfacing

---

## 3. Scope and non-scope

## 3.1 In scope for the console

### Required in this handoff

- Read DLQ entries from the unified `dlq` topic
- Read replay outcomes from `dlq.replay-results`
- Submit replay commands to `dlq.replay`
- Correlate incidents to DLQ using `incidentInfoDTO.dlqEntryRef`
- Expose security-related DLQ reason codes and severities
- Surface excluded-topic metrics/alerts for operator awareness
- Preserve lineage and audit history in the UI

### Target Premium console capabilities

These are aligned with `docs/dlq-feature-matrix.md` and should be treated as the full target feature set:

- DLQ explorer with rich filters
- Payload inspector with raw/decode views
- Correction UI
- Dry-run pre-flight visualization
- Replay approvals workflow
- Lineage visualization
- Batch replay
- RBAC audit dashboard

## 3.2 Explicitly out of scope

The console should **not** attempt to implement or simulate any of the following:

- Direct signing of replayed records
- Direct writes to `taktx-signing-keys`
- Direct writes to `taktx-configuration`
- Replay for DLQ-excluded topics such as `schedule-commands`
- Any new engine REST API surface
- Engine-side policy overrides beyond the documented `STRICT` and `OPERATOR_OVERRIDE` replay policies

---

## 4. Architecture the console must assume

## 4.1 Topic model

The console must assume exactly **three** namespace-scoped DLQ topics:

| Topic | Direction | Purpose |
|---|---|---|
| `<prefix>.dlq` | Engine → Console | Rejection capture stream |
| `<prefix>.dlq.replay` | Console → Engine | Operator replay commands |
| `<prefix>.dlq.replay-results` | Engine → Console | Replay outcome audit stream |

`<prefix>` is `[<tenantId>.]<namespace>` as produced by `TaktPropertiesHelper.getPrefixedTopicName()`.

There is **one physical DLQ topic**, not one per ingress surface. Surface routing is carried by `DlqEnvelope.sourceTopic`.

## 4.2 Included vs excluded surfaces

### Included DLQ ingress surfaces

The DLQ covers exactly these 8 external execution ingress surfaces:

- `process-instance`
- `message-event`
- `signals`
- `process-definition-activation`
- `dmn-definition-activation`
- `definitions`
- `dmn-definitions`
- `usertasks-response`

### DLQ-excluded topics

These are **not** part of the replayable DLQ console surface:

- `schedule-commands`
- `topic-meta-requested`
- `topic-meta-actual`
- `taktx-configuration`
- `taktx-signing-keys`
- projection/materialized-view topics such as `instance-update`, `usertasks`, `xml-by-*`

Important nuance:

- `topic-meta-requested` is security-sensitive and observable in the engine/metrics model, but it is outside the current console DLQ replay scope.
- `schedule-commands` remains engine-internal and is surfaced only through excluded-topic observability, not DLQ replay.

## 4.3 No engine REST API

The engine exposes no custom REST endpoints for this feature set.

The console must integrate through:

- Kafka topics
- `taktx-client`
- Prometheus/Grafana or other monitoring integrations

---

## 5. Recommended console architecture

## 5.1 Strong recommendation: backend service owns Kafka access

Do **not** connect the browser directly to Kafka.

Recommended split:

- **Console backend service**
    - uses `taktx-client`
    - consumes `dlq`, `dlq.replay-results`, and process-instance updates
    - persists/indexes data for query and search
    - publishes `DlqReplayCommand`
    - exposes UI-facing APIs/websockets
- **Console frontend**
    - renders explorer/detail/replay workflows
    - never handles raw Kafka access directly

## 5.2 Recommended backend responsibilities

- bootstrap and backfill DLQ history
- maintain a deduplicated logical DLQ store
- maintain raw event history where needed
- correlate replay results to replay attempts
- correlate incidents to DLQ entries
- redact sensitive values before returning them to low-privilege UI views
- gate replay actions behind console-side authentication/authorization

## 5.3 Recommended local persistence model

Use a database or search store with at least these logical entities:

### `dlq_entries`

Primary key: `dlq_entry_ref`

Suggested fields:

- `dlq_entry_ref`
- `source_topic`
- `source_partition`
- `source_offset`
- `source_timestamp_ms`
- `source_message_hash`
- `reason_code`
- `severity`
- `capture_stage`
- `message_type`
- `schema_version`
- `decoder_version`
- `schema_fingerprint`
- `rejection_timestamp_ms`
- `engine_instance_id`
- `decoded_summary_json`
- `additional_context_json`
- `headers_json`
- `key_bytes_base64`
- `value_bytes_base64`
- `duplicate_count`
- `first_seen_at`
- `last_seen_at`
- `latest_replay_status`
- `latest_correction_id`

### `dlq_replay_attempts`

Primary key: `correction_id`

Suggested fields:

- `correction_id`
- `dlq_entry_ref`
- `operator_id`
- `approved_at_ms`
- `submitted_at`
- `dry_run`
- `validation_policy`
- `override_reason`
- `changed_fields_json`
- `destination_topic`
- `expected_schema_version`
- `status`
- `outcome_text`
- `compatibility_decision`
- `replay_signer`
- `replay_signature_key_id`
- `replay_at_ms`

### `incident_links`

Suggested fields:

- `process_instance_id`
- `incident_timestamp`
- `incident_message`
- `dlq_entry_ref`
- `element_instance_id_path_json`

### Why store both logical and raw state

The engine is append-only and may emit duplicates under retry/rebalance conditions. The console should therefore:

- preserve the raw history when useful for audits
- present a **logical deduplicated view** to operators

---

## 6. Required `taktx-client` integration points

The live client APIs are:

- `TaktXClient.registerDlqEntryConsumer(...)`
- `TaktXClient.submitReplayCommand(...)`
- `TaktXClient.registerReplayResultConsumer(...)`
- `TaktXClient.registerProcessInstanceUpdateConsumer(...)`

### Backend bootstrap pattern

1. Create and start a `TaktXClient`
2. Register a DLQ consumer
3. Register a replay-result consumer
4. Register a process-instance update consumer
5. Backfill from earliest on first deployment
6. Resume from committed offsets on subsequent runs

### Recommended group IDs

Use separate, stable consumer groups per logical stream, for example:

- `console-dlq`
- `console-dlq-results`
- `console-process-updates`

If you operate multi-environment backends, include environment and tenant/namespace in the group ID.

---

## 7. DLQ ingestion contract

## 7.1 `DlqEnvelope`

Authoritative DTO: `taktx-shared/src/main/java/io/taktx/dto/DlqEnvelope.java`

| Field | Type | Console meaning |
|---|---|---|
| `sourceTopic` | `String` | Which of the 8 ingress surfaces the failure came from |
| `keyBytes` | `byte[]?` | Original Kafka key bytes; usually null for many surfaces |
| `valueBytes` | `byte[]?` | Original payload bytes; preserve exactly |
| `headers` | `Map<String,String>` | Snapshot of original Kafka headers |
| `reasonCode` | `DlqReasonCode` | Stable machine-readable classification |
| `reasonText` | `String` | Human-readable failure explanation |
| `severity` | `DlqSeverity` | UI priority, alert routing, color coding |
| `captureStage` | `DlqCaptureStage` | Whether failure happened during deserialization or processing |
| `rejectionTimestampMs` | `long` | Engine-side rejection time |
| `engineInstanceId` | `String` | Which engine instance rejected the message |
| `sourcePartition` | `Integer?` | Kafka partition if known |
| `sourceOffset` | `Long?` | Kafka offset if known |
| `sourceTimestampMs` | `Long?` | Original Kafka record timestamp if known |
| `sourceMessageHash` | `String?` | SHA-256-style source identity component |
| `messageType` | `String?` | Decoded message class if known |
| `schemaVersion` | `Integer?` | Version of decoded payload schema if known |
| `decoderVersion` | `String?` | Decoder/engine version that captured it |
| `schemaFingerprint` | `String?` | Fingerprint for schema mismatch hints |
| `decodedSummaryJson` | `String?` | Lightweight JSON summary for explorer/detail UI |
| `additionalContextJson` | `String?` | Failure-specific context |
| `lineage` | `DlqLineageDTO?` | Provenance information |
| `replaySigner` | `String?` | Set when replay provenance is involved |
| `replaySignatureKeyId` | `String?` | Engine key ID used in replay context |

## 7.2 Deduplication rule

The engine is append-only and may emit duplicate DLQ entries on retry.

The console must deduplicate using:

```text
<sourceTopic>:<sourcePartition>:<sourceOffset>:<sourceMessageHash>
```

Practical rules:

- if partition/offset/hash are null, normalize missing values to `?`
- present one logical row in the explorer
- keep `duplicate_count`
- keep raw duplicates only for audit/debug screens

## 7.3 Severity levels

Authoritative values:

- `LOW`
- `MEDIUM`
- `HIGH`
- `CRITICAL`

Recommended UI treatment:

- `CRITICAL`: immediate visual escalation, pin to top, red badges, default alert focus
- `HIGH`: prominent warning styling
- `MEDIUM`: standard operational issue styling
- `LOW`: informational styling

## 7.4 Capture stages

Authoritative values:

- `DESERIALIZER`
- `PROCESSOR`
- `ERROR_HANDLER`

Recommended operator copy:

- `DESERIALIZER`: “message could not be decoded / verified before normal processing”
- `PROCESSOR`: “message entered processing but failed validation or business handling”
- `ERROR_HANDLER`: reserved/future-facing; treat as engine-level capture

## 7.5 Reason codes

Authoritative values from `DlqReasonCode`:

| Reason code | Default severity | Meaning |
|---|---|---|
| `CBOR_DECODE_ERROR` | `MEDIUM` | Payload could not be decoded |
| `CBOR_TYPE_MISMATCH` | `MEDIUM` | Payload shape/type mismatch |
| `SIGNATURE_MISSING` | `HIGH` | Required Ed25519 signature missing |
| `SIGNATURE_MALFORMED` | `HIGH` | Signature header malformed |
| `SIGNATURE_KEY_UNKNOWN` | `HIGH` | Key ID not found / not trusted |
| `SIGNATURE_KEY_REVOKED` | `HIGH` | Key exists but is revoked |
| `SIGNATURE_VERIFICATION_FAILED` | `HIGH` | Signature does not verify |
| `JWT_MISSING` | `MEDIUM` | Required JWT missing |
| `JWT_MALFORMED` | `MEDIUM` | JWT structurally invalid |
| `JWT_SIGNATURE_INVALID` | `HIGH` | JWT signature invalid |
| `AUTHORIZATION_FAILED` | `MEDIUM` | Authz failed for another reason |
| `INSUFFICIENT_ROLE` | `MEDIUM` | Caller lacks required role |
| `INSUFFICIENT_SCOPE` | `MEDIUM` | JWT scope/claims insufficient |
| `REPLAY_DETECTED` | `CRITICAL` | Replay attack / duplicate security event detected |
| `PROCESSOR_EXCEPTION` | `MEDIUM` | Processor threw unexpectedly |
| `TOPIC_NOT_ALLOWED` | `MEDIUM` | Topic not allowed by policy |
| `UNKNOWN_REJECTION_REASON` | `LOW` | Fallback / uncategorized |

### UI filter requirement

The DLQ Explorer must at minimum filter by:

- severity
- reason code
- source topic
- capture stage
- rejection time range
- free-text search over `reasonText`, `decodedSummaryJson`, `additionalContextJson`

---

## 8. Incident correlation contract

## 8.1 Source of truth

Incidents link to DLQ through:

- `ProcessInstanceUpdateDTO.incidentInfoDTO.dlqEntryRef`

DTO field source:

- `taktx-shared/src/main/java/io/taktx/dto/IncidentInfoDTO.java`

## 8.2 Semantics

`dlqEntryRef` is populated **only** when the incident was caused by a message ingestion failure that also produced a DLQ entry.

It is null for incidents that do not correspond to a DLQ record.

## 8.3 Correlation rule

Treat `incidentInfoDTO.dlqEntryRef` as a direct key into your local DLQ store.

No transformation is required.

## 8.4 UI requirement

When a process-instance incident has a non-null `dlqEntryRef`, the console should:

- show a **“View matching DLQ entry”** action
- deep-link into the DLQ explorer detail view
- display the linked reason code and severity if already indexed locally

---

## 9. Replay contract

## 9.1 `DlqReplayCommand`

Authoritative DTO: `taktx-shared/src/main/java/io/taktx/dto/DlqReplayCommand.java`

The console should build commands through:

- `io.taktx.client.dlq.DlqReplayCommandBuilder`

### Core fields the console must understand

| Field | Required | Notes |
|---|---|---|
| `dlqEntryRef` | yes | Stable reference to original DLQ entry |
| `operatorId` | yes | Required audit identity |
| `approvedAtMs` | yes | Approval timestamp |
| `operatorNotes` | optional | Free text |
| `correctedValueBytes` | yes | Corrected payload bytes |
| `correctedKeyBytes` | optional | Only if keyed surface requires it |
| `correctedHeaders` | optional | Map of header name → string value; engine decodes/attaches |
| `destinationTopic` | yes | Bare topic name only, never prefixed |
| `validationPolicy` | yes | `STRICT` or `OPERATOR_OVERRIDE` |
| `lineage` | yes in practice | Auto-populated by builder from envelope |
| `overrideReason` | required for override workflow | Human justification |
| `changedFields` | optional but strongly recommended | Audit list of edits |
| `dryRun` | yes | Controls dry-run vs live replay |
| `expectedSchemaVersion` | optional | Used for compatibility checks |

## 9.2 Allowed destination topics

The engine only accepts these bare topic names for replay:

- `process-instance`
- `message-event`
- `signals`
- `process-definition-activation`
- `dmn-definition-activation`
- `definitions`
- `dmn-definitions`
- `usertasks-response`

The console should present these as a locked whitelist. In most cases, default to `envelope.sourceTopic` and do not encourage changing it.

## 9.3 Validation policies

Authoritative enum: `ReplayValidationPolicy`

- `STRICT`
- `OPERATOR_OVERRIDE`

### UX rules

#### `STRICT`

Use by default.

When schema version mismatches the engine-supported version, replay fails.

#### `OPERATOR_OVERRIDE`

Use only when an operator intentionally approves a schema mismatch.

Required UX behavior:

- require explicit justification text
- present a warning banner
- record changed fields
- surface override in audit history and replay result detail

## 9.4 Dry-run pattern

Recommended default flow:

1. Operator edits payload/headers
2. Operator runs dry-run
3. Console waits for replay result
4. If `DRY_RUN_PASSED`, operator may submit live replay
5. Console shows final `SUCCESS` or `FAILED`

This should be the primary replay UX. Do not default to direct live replay.

## 9.5 Live engine behavior for replay result statuses

### Currently emitted by engine

The live engine behavior supports these statuses in practice:

- `SUCCESS`
- `DRY_RUN_PASSED`
- `FAILED`

### Reserved / future-facing status

The observability layer pre-registers `DRY_RUN_FAILED`, but the current replay processor still emits `FAILED` for dry-run failures that occur before the success path.

### Console guidance

The console should:

- treat `SUCCESS` as forwarded replay
- treat `DRY_RUN_PASSED` as pre-flight success with no forward
- treat `FAILED` as any failed replay attempt, including dry-run failures
- tolerate `DRY_RUN_FAILED` if it appears in a future engine version

---

## 10. Replay result contract

## 10.1 `DlqReplayResult`

Authoritative DTO: `taktx-shared/src/main/java/io/taktx/dto/DlqReplayResult.java`

| Field | Meaning |
|---|---|
| `dlqEntryRef` | Links back to original DLQ entry |
| `operatorId` | Who triggered the replay |
| `replayAtMs` | When engine processed the replay attempt |
| `status` | `SUCCESS`, `DRY_RUN_PASSED`, or `FAILED` in current engine behavior |
| `outcomeText` | Human-readable engine decision |
| `failureReasonCode` | Optional failure classification |
| `replaySigner` | Engine instance identity used for replay provenance |
| `replaySignatureKeyId` | Engine signing key ID |
| `compatibilityDecision` | e.g. `COMPATIBLE` or override acceptance marker |
| `overrideReason` | Copied from command when override used |
| `dryRun` | Mirrors originating command |
| `lineageRef` | Same logical reference as `dlqEntryRef` |
| `correctionId` | Unique replay attempt ID |

## 10.2 Correlation rules

Use:

- `dlqEntryRef` to group replay attempts under a DLQ entry
- `correctionId` as the attempt identifier

## 10.3 UI requirement

Each DLQ entry detail page should show a **Replay attempts** section with:

- attempt time
- operator ID
- dry-run/live indicator
- status
- compatibility decision
- override reason
- outcome text
- correction ID

---

## 11. Lineage and forwarded-record semantics

When replay succeeds, the engine forwards a **new** record, not an edited re-send of the old record.

### Forwarded-record headers added by the engine

| Header | Meaning |
|---|---|
| `dlq-lin` | Original `dlqEntryRef` |
| `dlq-cid` | Replay attempt ID |
| `dlq-off` | Original source offset |
| `tx-sig` | Fresh engine-generated signature |

### Critical implementation rule

The console must never imply that it is preserving or resending the original signature.

The engine always generates a new signature for the replayed record.

### Lineage graph nodes the console should model

1. Original source message identity
2. DLQ entry
3. Incident link (when present)
4. Replay command / operator correction
5. Replay result
6. Forwarded replacement record metadata

---

## 12. Security-specific console requirements

## 12.1 Treat headers and payloads as sensitive material

The console must assume DLQ content can contain:

- JWTs
- signatures
- tenant identifiers
- request IDs
- business payload data
- PII depending on workload

### Required handling

- mask sensitive headers by default
- require explicit reveal for privileged operators
- never log full JWTs or full signatures in browser analytics or frontend telemetry
- redact or hash secrets in backend application logs
- store raw bytes only in trusted backend storage

## 12.2 Known security headers

The live codebase uses:

- `tx-auth` for JWT authorization
- `tx-sig` for Ed25519 signatures

Some older examples use `Authorization` in sample payloads. The console should therefore:

- treat headers generically
- specifically recognize and protect `tx-auth`
- specifically recognize and protect `tx-sig`
- not hardcode `Authorization` as the only auth header name

## 12.3 Replay header editing rule

The operator may edit auth-related headers if replay requires correction, but:

- the console must not require or encourage editing `tx-sig`
- if the operator includes `tx-sig`, the engine replaces it anyway
- the UI should either hide that field from editing or mark it read-only/ignored

## 12.4 Operator identity

`operatorId` is currently a plain string in the base/community model.

### Required behavior now

- always populate it
- source it from the console-authenticated user identity
- keep it stable and human-auditable (email or service-account style)

### Future-proofing requirement

A future Premium extension is expected to require JWT/OIDC-backed operator identity. Design the console so `operatorId` is derived from the authenticated principal rather than typed manually.

## 12.5 Trust mode awareness

Security docs distinguish:

- **community/open trust mode**: no platform root key configured
- **anchored mode**: platform public key configured; signing keys require countersignatures

There is no dedicated engine API for trust-mode discovery in this feature set.

### Console recommendation

If your deployment metadata already knows the environment mode, expose it in the UI as an environment badge:

- `Community trust mode`
- `Anchored trust mode`

If you cannot determine mode programmatically, make this an admin-configured environment property in the console.

## 12.6 Kafka ACL recommendations for console backend

The console backend should have:

### Read access

- `<prefix>.dlq`
- `<prefix>.dlq.replay-results`
- process instance update stream needed for incident correlation
- Prometheus/Grafana data source access if dashboards are embedded

### Write access

- `<prefix>.dlq.replay`

### Explicitly avoid granting unless separately justified

- writes to `process-instance` or other ingress topics directly
- writes to `taktx-signing-keys`
- writes to `taktx-configuration`
- writes to engine-internal topics

---

## 13. UI requirements by feature area

## 13.1 DLQ Explorer

### Required columns

- rejection time
- severity
- reason code
- source topic
- capture stage
- message type
- schema version
- engine instance ID
- duplicate count
- replay status summary

### Required filters

- severity
- reason code
- source topic
- capture stage
- time range
- replay status
- full-text search

### Recommended quick filters

- `CRITICAL only`
- `HIGH only`
- `Replay detected`
- `Signature failures`
- `JWT failures`
- `Needs attention` (no successful replay yet)

## 13.2 DLQ detail page

The detail page should contain these sections:

1. **Summary**
    - reason code
    - reason text
    - severity
    - capture stage
    - timestamps
2. **Original message identity**
    - topic, partition, offset, message hash
3. **Decoded context**
    - message type
    - schema version
    - decoder version
    - schema fingerprint
    - decoded summary JSON
4. **Headers**
    - secure masked display
5. **Raw data**
    - payload bytes as base64/hex
    - key bytes if present
6. **Additional context**
    - parsed JSON if valid
7. **Replay attempts**
8. **Incident links**

## 13.3 Payload Inspector / Correction UI

Recommended capabilities:

- raw base64 view
- hex view
- decoded JSON view when decodable by console-side tooling
- schema mismatch warnings based on envelope metadata
- editable payload and headers
- diff preview against original
- changed-field list builder

### Important note

Console-side decode/edit tooling is an operator convenience only. The engine remains authoritative for replay validation.

## 13.4 Replay action panel

Minimum controls:

- `operatorId` display
- `destinationTopic` display/select from whitelist
- `STRICT` / `OPERATOR_OVERRIDE`
- override reason field
- operator notes field
- dry-run button
- live replay button

### Required guardrails

- disable live replay until operator acknowledges risk
- require override reason for `OPERATOR_OVERRIDE`
- default to dry-run first
- show result status inline after each attempt

## 13.5 Incident integration

Wherever the console displays an incidented process instance, include:

- DLQ badge if `incidentInfoDTO.dlqEntryRef != null`
- direct navigation to matching DLQ entry
- reason/severity preview if indexed

## 13.6 Security and operations dashboard

Recommended panels:

- DLQ entries over time by severity
- DLQ entries by reason code
- DLQ entries by source topic
- replay outcome counts by status
- critical DLQ entries in last hour
- excluded-topic deserialization errors
- excluded-topic failures for `schedule-commands`

---

## 14. Observability integration

## 14.1 Metrics to surface

Authoritative metrics emitted by engine:

| Metric | Tags | Meaning |
|---|---|---|
| `taktx.dlq.entries` | `severity`, `reason_code`, `source_topic`, `capture_stage` | DLQ capture counter |
| `taktx.dlq.replay.outcomes` | `status` | Replay outcomes |
| `taktx.excluded.topic.deserialization.errors` | `topic` | Excluded-topic poison records |
| `taktx.excluded.topic.failures` | `topic_group` | Engine-internal excluded-topic failures |

## 14.2 Alerting semantics

From `docker/prometheus-dlq-alerts.yaml`:

- `CRITICAL` DLQ entries should page immediately
- `HIGH` DLQ entries should alert on threshold breach
- excluded-topic deserialization errors should warn
- `schedule-commands` failures should warn as engine defects

## 14.3 Console behavior recommendation

The console should visually distinguish:

- **replayable DLQ issues**
- **non-replayable excluded-topic issues**

Do not mislead operators into thinking excluded-topic failures can be resolved by replay from the console.

---

## 15. End-to-end console flows

## 15.1 First-run bootstrap

1. Start backend service
2. Create `TaktXClient`
3. Register `dlq` consumer with `startFromEarliest = true`
4. Build local dedup/index store
5. Register replay-result consumer
6. Register process-instance update consumer
7. Switch to resume mode on future restarts

## 15.2 Incident → DLQ triage flow

1. Operator opens incidented process instance
2. Console sees `incidentInfoDTO.dlqEntryRef`
3. Backend resolves matching DLQ entry
4. UI shows linked DLQ reason/severity
5. Operator navigates to DLQ detail

## 15.3 Dry-run replay flow

1. Operator opens DLQ detail
2. Reviews decoded summary and headers
3. Makes corrections
4. Console builds `DlqReplayCommand` using `DlqReplayCommandBuilder.from(envelope)`
5. Console sets `dryRun = true`
6. Backend submits command
7. Console waits for matching `DlqReplayResult`
8. If `DRY_RUN_PASSED`, UI enables live replay

## 15.4 Live replay flow

1. Operator confirms replay
2. Backend submits live command (`dryRun = false`)
3. Engine signs forwarded record and emits result
4. Console shows `SUCCESS` with `correctionId`
5. Entry status becomes resolved/replayed in the explorer

## 15.5 Override replay flow

1. Operator selects `OPERATOR_OVERRIDE`
2. Console requires explicit `overrideReason`
3. Dry-run first
4. Live replay only after operator confirmation
5. Replay result highlights compatibility override

## 15.6 Failed replay flow

When result is `FAILED`:

- keep entry unresolved
- show engine outcome text prominently
- keep full attempt history
- let operator edit and retry
- never auto-loop retries

---

## 16. Acceptance criteria for the console team

A release should not be considered complete until all of the following are true.

### Data ingestion

- [ ] Console backend consumes unified `dlq` successfully
- [ ] Console backend deduplicates by `dlqEntryRef`
- [ ] Console backend consumes `dlq.replay-results`
- [ ] Console backend consumes process-instance updates for incident correlation

### UI

- [ ] Explorer filters by severity, reason code, topic, and time range
- [ ] Detail page shows raw and structured context
- [ ] Incident panel links to DLQ entry when `dlqEntryRef` exists
- [ ] Replay attempt history is visible per DLQ entry

### Replay

- [ ] Dry-run command submission works
- [ ] `DRY_RUN_PASSED` is shown correctly
- [ ] Live replay submission works
- [ ] `SUCCESS` and `FAILED` are shown correctly
- [ ] `OPERATOR_OVERRIDE` requires justification
- [ ] `tx-sig` is not operator-editable as a meaningful input

### Security UX

- [ ] Sensitive headers are masked by default
- [ ] `operatorId` is always populated from authenticated identity
- [ ] Console backend has least-privilege Kafka ACLs
- [ ] Excluded-topic failures are shown as non-replayable

### Observability

- [ ] DLQ metrics are visible in dashboard panels
- [ ] Critical and high-severity states are visually distinct
- [ ] Replay outcome trends are visible

---

## 17. Suggested QA scenarios

## 17.1 DLQ ingestion and rendering

- signature verification failure on `process-instance`
- JWT missing on entry command
- CBOR decode failure with null/unknown source metadata
- duplicate DLQ emits for same source record

## 17.2 Incident correlation

- process instance update with non-null `incidentInfoDTO.dlqEntryRef`
- incident with null `dlqEntryRef`

## 17.3 Replay flows

- dry-run success
- dry-run failure because destination invalid
- strict schema mismatch failure
- override success with explicit reason
- successful live replay after dry-run

## 17.4 Security UX

- masked rendering of `tx-auth`
- hidden/ignored `tx-sig` in correction UI
- low-privilege user blocked from replay action

## 17.5 Observability

- critical `REPLAY_DETECTED` row gets escalated styling
- excluded-topic metric event appears in non-replayable operations panel

---

## 18. Recommended delivery phases

## Phase A — operational baseline

Implement first:

- backend consumers
- dedup store
- explorer + detail view
- incident → DLQ linking
- dry-run/live replay basics
- replay history

## Phase B — security-aware operator UX

Add:

- masked headers
- structured diff view
- override workflow polish
- metrics dashboard panels
- critical/high severity prioritization

## Phase C — premium workflow depth

Add:

- multi-step approvals
- batch replay
- lineage graph
- RBAC audit dashboard
- federated multi-environment view

---

## 19. Future-proofing notes

The console should be designed so future engine/security roadmap items do not force major rework.

### Likely future extensions

- operator JWT / OIDC-backed replay approval
- SLA alerts for aged DLQ entries
- cold-archive replay from object storage
- federated cross-environment DLQ views
- broader replay-hardening for deferred internal topics

### Design guidance

- derive `operatorId` from auth context, not free text
- keep replay status handling tolerant of new status values
- keep reason-code rendering tolerant of enum growth
- keep lineage model extensible
- keep raw DLQ storage separate from UI projection so new fields can be indexed later

---

## 20. Final implementation checklist for handoff

If the console team only reads one section, it should be this one.

### Must build now

- [ ] Backend Kafka integration through `taktx-client`
- [ ] Deduplicated unified DLQ store keyed by `dlqEntryRef`
- [ ] Incident → DLQ linking via `incidentInfoDTO.dlqEntryRef`
- [ ] Explorer + detail + replay-history UI
- [ ] Dry-run-first replay workflow
- [ ] Security masking for JWT/signature headers
- [ ] Metrics/alerts surface for DLQ and excluded-topic failures

### Must not do

- [ ] Do not sign replayed records in the console
- [ ] Do not bypass engine replay validation
- [ ] Do not offer replay for excluded topics
- [ ] Do not require a REST API from the engine for this feature set
- [ ] Do not store secrets in frontend logs or analytics

### Authoritative fallback documents

If any ambiguity remains, the console team should defer to:

1. `docs/dlq-console-contract.md` for Kafka contract and examples
2. `docs/security.md` for trust and header semantics
3. `docs/security-threat-model.md` for boundary and ACL assumptions
4. `taktx-client` / `taktx-shared` DTOs for live field definitions
