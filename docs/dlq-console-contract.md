# TaktX DLQ Console Integration Contract

**Document Version**: 1.0  
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
    "X-TaktX-Signature": "<base64>",
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
- `X-TaktX-Signature` must **not** be included in `correctedHeaders` — the engine always replaces it with a fresh ENGINE-signed value.
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
| `X-DLQ-Lineage-Ref` | `dlqEntryRef` of the originating DLQ entry |
| `X-DLQ-Correction-Id` | UUID matching `DlqReplayResult.correctionId` |
| `X-DLQ-Source-Offset` | Kafka offset of the original failed record |
| `X-TaktX-Signature` | Fresh ENGINE Ed25519 signature (replaces any previous signature) |

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

