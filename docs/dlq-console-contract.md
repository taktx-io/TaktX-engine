# TaktX DLQ Console Integration Contract

**Document Version**: 1.1  
**Date**: May 7, 2026  
**Status**: Active  
**Depends on**: DLQ-014, DLQ-017  

---

## Overview

This document defines the engine-console integration contract for the TaktX Dead Letter Queue (DLQ). All console interactions are mediated through the `taktx-client` library, which provides a Kafka-native API over three unified, namespace-scoped topics.

---

## Topic Contract

| Topic | Direction | Cleanup | Purpose |
|---|---|---|---|
| `<prefix>.dlq` | Engine → Console | DELETE | Append-only rejection audit trail |
| `<prefix>.dlq.replay` | Console → Engine | DELETE | Operator replay commands |
| `<prefix>.dlq.replay-results` | Engine → Console | DELETE | Replay outcome records |

The `<prefix>` is `[<tenantId>.]<namespace>` as produced by `TaktPropertiesHelper.getPrefixedTopicName()`.

Topics use **DELETE** cleanup (time-bounded retention) — see `docs/dlq-retention-policy.md` for configuration guidance.

---

## Message Formats

### `dlq` — `DlqEnvelope` (engine output)

```json
{
  "sourceTopic":        "process-instance",
  "keyBytes":           null,
  "valueBytes":         "<base64-encoded raw CBOR payload>",
  "headers": {
    "tx-sig": "<base64>",
    "Authorization":     "Bearer <jwt>"
  },
  "reasonCode":         "SIGNATURE_VERIFICATION_FAILED",
  "reasonText":         "Ed25519 signature verification failed for keyId=worker-key-1",
  "severity":           "HIGH",
  "captureStage":       "PROCESSOR",
  "rejectionTimestampMs": 1714550000000,
  "engineInstanceId":   "tenant.namespace",
  "sourcePartition":    0,
  "sourceOffset":       12345,
  "sourceTimestampMs":  1714549999000,
  "sourceMessageHash":  "sha256:abc123...",
  "messageType":        "StartCommandDTO",
  "schemaVersion":      1,
  "decoderVersion":     "taktx-0.5.1-beta",
  "schemaFingerprint":  "sha256:def456...",
  "decodedSummaryJson": "{\"processDefinitionId\":\"loan-application\"}",
  "additionalContextJson": null,
  "lineage":            null,
  "replaySigner":       null,
  "replaySignatureKeyId": null
}
```

**Dedup key**: `sourceTopic + ":" + sourcePartition + ":" + sourceOffset + ":" + sourceMessageHash`

The `dlq` topic is append-only; the engine may produce duplicate entries when Kafka Streams retries a failed record. Deduplication is the console's responsibility using the dedup key above.

#### `severity` values
| Value | Meaning |
|---|---|
| `CRITICAL` | Replay attacks or systemic anomalies — page immediately |
| `HIGH` | Signature or trust failures — alert on threshold |
| `MEDIUM` | Business/data issues — dashboard monitoring |
| `LOW` | Benign validation noise — informational |

#### `captureStage` values
| Value | Meaning |
|---|---|
| `DESERIALIZER` | Value was `null` at processor entry — decode failure |
| `PROCESSOR` | Exception or business-rule violation inside `process()` |
| `ERROR_HANDLER` | Caught at engine-level stream error handler (reserved) |

---

### `dlq.replay` — `DlqReplayCommand` (console input)

```json
{
  "dlqEntryRef":        "process-instance:0:12345:sha256:abc123",
  "operatorId":         "ops-user@example.com",
  "approvedAtMs":       1714550100000,
  "operatorNotes":      "Fixed JWT expiry in Authorization header",
  "correctedValueBytes": "<base64-encoded corrected CBOR payload>",
  "correctedKeyBytes":  null,
  "correctedHeaders": {
    "Authorization": "<base64-encoded new JWT Bearer token>"
  },
  "destinationTopic":   "process-instance",
  "validationPolicy":   "STRICT",
  "lineage": {
    "sourceTopic":        "process-instance",
    "sourcePartition":    0,
    "sourceOffset":       12345,
    "sourceTimestampMs":  1714549999000,
    "sourceMessageHash":  "sha256:abc123...",
    "sourceSignatureKeyId": "worker-key-1",
    "sourceSignature":    "<base64>"
  },
  "overrideReason":     null,
  "changedFields":      ["headers.Authorization"],
  "dryRun":             false,
  "expectedSchemaVersion": 1
}
```

**Key notes**:
- `destinationTopic` must be a **bare** topic name (no prefix) from the 8 allowed ingress surfaces — the engine enforces this whitelist.
- `correctedHeaders` values are base64-encoded; the engine decodes them before attaching to the forwarded record.
- `tx-sig` must **not** be included in `correctedHeaders` — the engine always replaces it with a fresh ENGINE-signed value.
- `dryRun: true` runs all validation without forwarding the record; use for pre-flight checks.
- `validationPolicy: "OPERATOR_OVERRIDE"` allows schema version mismatch with an explicit `overrideReason`.

#### Allowed `destinationTopic` values
```
process-instance
message-event
signals
process-definition-activation
dmn-definition-activation
definitions
dmn-definitions
usertasks-response
```

---

### `dlq.replay-results` — `DlqReplayResult` (engine output)

```json
{
  "dlqEntryRef":         "process-instance:0:12345:sha256:abc123",
  "operatorId":          "ops-user@example.com",
  "replayAtMs":          1714550105000,
  "status":              "SUCCESS",
  "outcomeText":         "Replay forwarded to <prefix>.process-instance with correctionId=<uuid>",
  "failureReasonCode":   null,
  "replaySigner":        "tenant.namespace",
  "replaySignatureKeyId": "engine-key-2026-05-01",
  "compatibilityDecision": "COMPATIBLE",
  "overrideReason":      null,
  "dryRun":              false,
  "lineageRef":          "process-instance:0:12345:sha256:abc123",
  "correctionId":        "<uuid>"
}
```

#### `status` values
| Value | Meaning |
|---|---|
| `SUCCESS` | Record forwarded to target ingress topic |
| `DRY_RUN_PASSED` | Dry-run validation passed; no record forwarded |
| `FAILED` | Validation failed; see `outcomeText` and `failureReasonCode` |

**Correlation**: Use `correctionId` to link the result back to the forwarded record. The engine stamps `X-DLQ-Correction-Id: <correctionId>` on the forwarded record's headers.

---

## Lineage Headers (on forwarded records)

Replayed records carry these Kafka headers:

| Header | Value |
|---|---|
| `dlq-lin` | `dlqEntryRef` of the originating DLQ entry |
| `dlq-cid` | UUID matching `DlqReplayResult.correctionId` |
| `dlq-off` | Kafka offset of the original failed record |
| `tx-sig` | Fresh ENGINE Ed25519 signature (replaces any previous signature) |

---

## Client API (Community Tier)

All DLQ console interactions flow through `TaktXClient` in `taktx-client`:

```java
TaktXClient client = TaktXClient.newClientBuilder()
    .withProperties(properties)
    .build();
client.start();

// 1. Read DLQ entries from the dlq topic
client.registerDlqEntryConsumer("my-console-group", envelope -> {
    // Handle DlqEnvelope — display in explorer, store for lineage
});

// 2. Build and submit a replay command
DlqReplayCommand cmd = DlqReplayCommandBuilder.from(envelope)
    .operatorId("ops@example.com")
    .correctedPayload(correctedBytes)
    .correctedHeaders(Map.of("Authorization", base64NewJwt))
    .dryRun()         // run dry-run first
    .build();
client.submitReplayCommand(cmd);

// 3. Monitor replay results
client.registerReplayResultConsumer("my-console-results-group", result -> {
    if ("DRY_RUN_PASSED".equals(result.getStatus())) {
        // Now submit live
        DlqReplayCommand liveCmd = DlqReplayCommandBuilder.from(envelope)
            .operatorId("ops@example.com")
            .correctedPayload(correctedBytes)
            .correctedHeaders(Map.of("Authorization", base64NewJwt))
            .build();
        client.submitReplayCommand(liveCmd);
    }
});
```

### Helper: `DlqReplayCommandBuilder`

Located in `io.taktx.client.dlq.DlqReplayCommandBuilder`. Constructs a well-formed `DlqReplayCommand` from a `DlqEnvelope`:

| Method | Description |
|---|---|
| `from(DlqEnvelope)` | Pre-populates `dlqEntryRef`, `destinationTopic`, `lineage`, `correctedValueBytes` |
| `operatorId(String)` | **Required** — operator identity for audit trail |
| `correctedPayload(byte[])` | Override raw payload bytes |
| `correctedHeaders(Map)` | Override Kafka headers (values base64-encoded) |
| `validationPolicy(…)` | `STRICT` (default) or `OPERATOR_OVERRIDE` |
| `overrideReason(String)` | Required with `OPERATOR_OVERRIDE` |
| `changedFields(List)` | Audit list of changed field paths |
| `dryRun()` | Enable dry-run mode |
| `expectedSchemaVersion(int)` | Override schema version check |
| `build()` | Validates required fields and returns `DlqReplayCommand` |

---

## Dry-Run Flow

The dry-run pattern for console payload validation:

```
Console                         Engine (dlq.replay processor)
  │                                │
  │ submit { dryRun: true }        │
  │──────────────────────────────→│
  │                                │ run: destination safety ✓
  │                                │ run: schema compat ✓
  │                                │ run: ENGINE signing (computed only)
  │                                │ skip: forward to ingress topic
  │                                │
  │ ←──────────────────────────── │ emit DlqReplayResult { status: "DRY_RUN_PASSED" }
  │                                │
  │ (if DRY_RUN_PASSED)            │
  │ submit { dryRun: false }       │
  │──────────────────────────────→│
  │                                │ run: all validation ✓
  │                                │ sign with ENGINE key
  │                                │ forward → target ingress topic
  │                                │ stamp X-DLQ-Lineage-Ref, X-DLQ-Correction-Id
  │ ←──────────────────────────── │ emit DlqReplayResult { status: "SUCCESS" }
```

---

## Lineage Visualization Data Points

To build a full lineage graph in the console, chain these records:

```
Original record
  └─ source: sourceTopic + sourcePartition + sourceOffset
  └─ identity: sourceMessageHash, sourceSignatureKeyId

DlqEnvelope (on dlq topic)
  └─ dlqEntryRef = "sourceTopic:partition:offset:hash"
  └─ captureStage, reasonCode, severity

ProcessInstanceUpdateDTO (on process-instance-updates topic)  ← NEW
  └─ incidentInfo.dlqEntryRef → links directly to DlqEnvelope above
  └─ incidentInfo.message, incidentInfo.elementInstanceIdPath
  └─ only present when the incident was caused by a message failure captured in DLQ

DlqReplayCommand (on dlq.replay topic)
  └─ dlqEntryRef → links to DlqEnvelope
  └─ operatorId, changedFields, overrideReason
  └─ approvedAtMs

DlqReplayResult (on dlq.replay-results topic)
  └─ lineageRef = dlqEntryRef
  └─ correctionId → links to forwarded record
  └─ replaySigner, replaySignatureKeyId, compatibilityDecision

Forwarded record (on target ingress topic)
  └─ X-DLQ-Lineage-Ref = dlqEntryRef
  └─ X-DLQ-Correction-Id = correctionId
  └─ X-DLQ-Source-Offset = original offset
```

---

## Engine vs Console Responsibility Split

| Responsibility | Engine | Console |
|---|---|---|
| DLQ topic creation + retention | ✅ | ❌ |
| `DlqEnvelope` capture + publish | ✅ | ❌ |
| Destination topic safety enforcement | ✅ | Advisory |
| ENGINE signing of replayed records | ✅ | ❌ |
| Schema compatibility check | ✅ | Advisory |
| Lineage header stamping (`X-DLQ-*`) | ✅ | ❌ |
| Replay result audit emit | ✅ | ❌ |
| DLQ explorer / filter UI | ❌ | ✅ (Premium) |
| Payload decode / correction UI | ❌ | ✅ (Premium) |
| Dry-run pre-flight UI | ❌ | ✅ (Premium / Community API) |
| Replay approval workflow | ❌ | ✅ (Premium) |
| Lineage visualization graph | ❌ | ✅ (Premium) |
| Batch replay | ❌ | ✅ (Premium) |
| RBAC audit dashboard | ❌ | ✅ (Premium) |
| Deduplication of DLQ entries | ❌ (append-only) | ✅ (logical, by dedup key) |

---

## Prometheus Metrics

The engine emits these metrics observable from the console:

| Metric | Tags | Description |
|---|---|---|
| `taktx.dlq.entries` | `severity`, `reason_code`, `source_topic`, `capture_stage` | Counter per DLQ entry |
| `taktx.dlq.replay.outcomes` | `status` | Replay outcome (SUCCESS / FAILED / DRY_RUN_PASSED) |
| `taktx.excluded.topic.deserialization.errors` | `topic` | Excluded-topic poison records skipped |
| `taktx.excluded.topic.processing.failures` | `topic` | Engine-internal processing exceptions |

See `docker/prometheus-dlq-alerts.yaml` for alert rules.

---

## Incident → DLQ Link

When a process instance enters **incident state** due to a message ingestion failure (decode error, unhandled BPMN error event), the engine co-produces both a `DlqEnvelope` on the `dlq` topic and a `ProcessInstanceUpdateDTO` on the `process-instance-updates` topic. The update carries a direct back-reference to the DLQ entry.

### `IncidentInfoDTO.dlqEntryRef`

| Field | Type | When populated |
|---|---|---|
| `dlqEntryRef` | `String` (nullable) | When the incident was caused by a message failure that also produced a DLQ entry |

**Format**: `sourceTopic:partition:offset:sha256:hash`  
If the SHA-256 hash is unavailable (e.g. empty payload), the hash segment is `?`: `process-instance:0:42:?`

**When it is `null`**: Engine-internal BPMN exceptions (`doWhileCatching`) and exceptions without a corresponding DLQ entry do not set this field.

### Correlating an incident to its DLQ entry

```java
client.registerProcessInstanceUpdateConsumer("console-group", update -> {
    if (update instanceof ProcessInstanceUpdateDTO piUpdate
            && piUpdate.getIncidentInfoDTO() != null
            && piUpdate.getIncidentInfoDTO().getDlqEntryRef() != null) {

        String dlqRef = piUpdate.getIncidentInfoDTO().getDlqEntryRef();
        // Look up the matching DlqEnvelope in your local DLQ store
        // Key: dlqRef == envelope.getSourceTopic() + ":" + partition + ":" + offset + ":" + hash
        DlqEnvelope match = dlqStore.findByRef(dlqRef);
    }
});
```

The `dlqEntryRef` in `IncidentInfoDTO` uses the same format as `DlqEnvelope`'s dedup key, so a direct map lookup works without any transformation.

---

## Console Implementation Guide

This section consolidates all the touch points needed to implement the full DLQ console feature set using the `taktx-client` library.

### 1. Bootstrap

```java
TaktXClient client = TaktXClient.newClientBuilder()
    .withProperties(kafkaProperties) // standard Kafka consumer/producer config
    .build();
client.start();
```

### 2. DLQ Explorer — reading entries

```java
// startFromEarliest=true on first run to replay history; false thereafter
client.registerDlqEntryConsumer("console-dlq-group", envelope -> {
    // Store envelope keyed by dlqEntryRef for O(1) incident correlation lookups
    String ref = envelope.getSourceTopic()
        + ":" + envelope.getSourcePartition()
        + ":" + envelope.getSourceOffset()
        + ":" + envelope.getSourceMessageHash();
    dlqStore.put(ref, envelope);
}, /* startFromEarliest= */ true);
```

Deduplicate by the dedup key (`sourceTopic:sourcePartition:sourceOffset:sourceMessageHash`) — the engine may re-emit an entry on Kafka Streams retry.

### 3. Process instance incident state — linking to DLQ

```java
client.registerProcessInstanceUpdateConsumer("console-pi-group", update -> {
    if (update instanceof ProcessInstanceUpdateDTO piUpdate) {
        IncidentInfoDTO incident = piUpdate.getIncidentInfoDTO();
        if (incident != null && incident.getDlqEntryRef() != null) {
            // Navigate console from incident panel directly to DLQ explorer entry
            DlqEnvelope linked = dlqStore.get(incident.getDlqEntryRef());
            consoleUI.showIncidentWithDlqLink(piUpdate, linked);
        }
    }
});
```

`ProcessInstanceUpdateDTO` arrives via the `registerProcessInstanceUpdateConsumer` API, not a separate topic subscription. The `incidentInfoDTO.dlqEntryRef` is populated **only** when:
- The incident was caused by a CBOR decode failure (`captureStage = DESERIALIZER`)
- The incident was caused by an unhandled BPMN error event (`captureStage = PROCESSOR`, DLQ entry has empty payload, `dlqEntryRef` hash segment = `?`)

### 4. Payload correction and replay

```java
// Step 1 — dry-run to validate before committing
DlqReplayCommand dryRun = DlqReplayCommandBuilder.from(envelope)
    .operatorId("ops@example.com")
    .correctedPayload(correctedCborBytes)
    .correctedHeaders(Map.of("Authorization", base64NewJwt))
    .dryRun()
    .build();
client.submitReplayCommand(dryRun);

// Step 2 — listen for result, then go live
client.registerReplayResultConsumer("console-results-group", result -> {
    if ("DRY_RUN_PASSED".equals(result.getStatus())) {
        DlqReplayCommand live = DlqReplayCommandBuilder.from(envelope)
            .operatorId("ops@example.com")
            .correctedPayload(correctedCborBytes)
            .correctedHeaders(Map.of("Authorization", base64NewJwt))
            .build(); // dryRun defaults to false
        client.submitReplayCommand(live);
    } else if ("FAILED".equals(result.getStatus())) {
        consoleUI.showReplayError(result.getOutcomeText(), result.getFailureReasonCode());
    }
});
```

### 5. Schema override (OPERATOR_OVERRIDE policy)

Use only when the operator has verified the payload is compatible with an older or newer schema:

```java
DlqReplayCommand override = DlqReplayCommandBuilder.from(envelope)
    .operatorId("ops@example.com")
    .correctedPayload(correctedBytes)
    .validationPolicy(DlqValidationPolicy.OPERATOR_OVERRIDE)
    .overrideReason("Schema v2→v3 migration: field 'amount' renamed to 'totalAmount'")
    .changedFields(List.of("totalAmount"))
    .build();
client.submitReplayCommand(override);
```

### 6. Correlation summary

| Console action | Data source | Key field |
|---|---|---|
| List DLQ entries | `dlq` topic via `registerDlqEntryConsumer` | `dlqEntryRef` (computed from envelope fields) |
| Detect incident with DLQ cause | `process-instance-updates` topic | `incidentInfoDTO.dlqEntryRef` |
| Navigate incident → DLQ entry | Local store lookup | `dlqEntryRef` equality |
| Submit replay | `dlq.replay` topic via `submitReplayCommand` | `dlqEntryRef` |
| Track replay outcome | `dlq.replay-results` topic via `registerReplayResultConsumer` | `dlqEntryRef` + `correctionId` |
| Track forwarded record | Target ingress topic headers | `dlq-lin`, `dlq-cid` |

### 7. Shutdown

```java
client.stop(); // stops all consumers and flushes the replay producer
```
