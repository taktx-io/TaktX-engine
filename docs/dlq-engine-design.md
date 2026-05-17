# TaktX Dead Letter Queue (DLQ) - Engine Design & Implementation Plan

**Document Version**: 1.0  
**Date**: May 1, 2026  
**Status**: Implementation Complete (2026-05-07)  
**Author**: GitHub Copilot (with guidance from Engineering Team)

> **Implementation status**: All five epics (E1–E5) are complete as of 2026-05-07.  
> See companion documents for the living implementation contracts:
> - [`docs/dlq-implementation-backlog.md`](./dlq-implementation-backlog.md) — full implementation history and task status
> - [`docs/dlq-console-contract.md`](./dlq-console-contract.md) — engine-console topic/DTO contract (DLQ-019)
> - [`docs/dlq-feature-matrix.md`](./dlq-feature-matrix.md) — Community vs Premium feature split (DLQ-020)
> - [`docs/dlq-retention-policy.md`](./dlq-retention-policy.md) — per-environment retention and storage guidance (DLQ-018)

## Final Scope Decision (Authoritative)

DLQ is used **only for external execution ingress topics** that:
- drive BPMN/DMN execution,
- have business significance,
- can be meaningfully inspected, corrected, and replayed.

### Topics WITH DLQ coverage
- `process-instance`
- `message-event`
- `signals`
- `process-definition-activation`
- `dmn-definition-activation`
- `definitions`
- `dmn-definitions`
- `usertasks-response`

### Topics WITHOUT DLQ coverage

**Engine-internal topics**
- `schedule-commands`

**Control-plane / security topics**
- `topic-meta-requested`
- `topic-meta-actual`
- `taktx-configuration`
- `taktx-signing-keys`

**Projections / materialized views**
- `xml-by-process-definition-id`
- `xml-by-dmn-definition-id`
- `instance-update`
- `usertasks`

### Handling for excluded topics
- Engine-internal failures: incident + structured logs + metrics + alerting.
- Control-plane/security failures: reject immediately + audit/security events + alerting.
- Projection/materialization failures: rebuild/regenerate from source rather than replay.

## Topology Decision: Single DLQ Topic Per Namespace (Authoritative)

**Decision date**: May 1, 2026

The DLQ uses **three namespace-scoped topics** shared across all ingress surfaces:

| Topic | Purpose | Cleanup |
|---|---|---|
| `dlq` | All rejection captures | DELETE |
| `dlq.replay` | Operator replay commands | DELETE |
| `dlq.replay-results` | Replay outcome records | DELETE |

Per-surface routing is carried entirely inside `DlqEnvelope.sourceTopic`.

**Rationale**: The `DlqEnvelope` already contains `sourceTopic`, `reasonCode`, `severity`, and full lineage, making separate per-surface topics redundant. Separate topics would have required 24 topic constants (8 surfaces × 3), adding operational overhead (ACLs, retention config, monitoring rules) with no functional benefit at current scale.

**Future split criteria**: Per-surface topics may be introduced later if there is a demonstrated need for:
- different retention policies per surface,
- separate ACL/RBAC boundaries per surface, or
- distinct scaling/throughput characteristics per surface.

> This section is authoritative and supersedes earlier examples in this document that still mention broader DLQ coverage during design exploration.

## Executive Summary

This document formalizes the architecture, design decisions, and implementation roadmap for the TaktX Engine Dead Letter Queue (DLQ) feature. The DLQ captures failed message processing events for the approved external execution ingress topics, with sufficient metadata for forensic analysis, manual intervention, and controlled reprocessing.

### Problem Statement

Messages fail processing at multiple stages:
- **Pre-decode failures**: CBOR corruption, malformed payloads, truncated arrays
- **Signature verification failures**: Ed25519 signature mismatches, unknown/revoked key IDs, trust policy violations
- **Authorization failures**: JWT verification failures, replay violations, insufficient claims, scope mismatches
- **Business logic failures**: Unhandled error events, authorization scope violations, state conflicts
- **Runtime exceptions**: Processor errors, state store failures, downstream topic issues

**Current Gap (at design time)**: Most rejection points either log and skip (losing the message) or throw unhandled exceptions. No systematic capture, audit trail, or replay mechanism existed. The incomplete implementation used a fundamentally flawed compacted-topic design that collapsed all DLQ entries into a single compaction point, defeating the purpose.

> **Implementation status (2026-05-07)**: This gap has been fully closed. All five epics are complete. See `docs/dlq-implementation-backlog.md` for the full implementation history.

### Design Vision

- **Append-only audit trail**: All rejections preserved immutably with full context
- **Ingress-focused isolation**: DLQ coverage applies only to the 8 external execution ingress surfaces — not to control-plane or engine-internal topics. A single unified `dlq` topic carries all rejection captures; per-surface routing is provided by `DlqEnvelope.sourceTopic` (see the final Topology Decision above).
- **Structured envelope**: Unified `DlqEnvelope` carrying raw bytes, headers snapshot, reason code, human-readable explanation, and optional decoded summary
- **Operator-driven replay**: Operators investigate DLQ via console, approve corrected messages, and submit to explicit replay topic
- **Comprehensive observability**: Structured logging, metrics, and audit events for all rejection stages

---

## Part 1: Architecture & Design Decisions

### 1.1 Topic Strategy: Append-Only Per-Surface Topics vs. Compacted Global Store

#### Current Implementation (Problematic)

The current code uses a single compacted topic `dlq` with an empty `DlqEntryKey`:

```java
// Current code in Topics.java
DLQ("dlq", false, CleanupPolicy.COMPACT),  // Single compacted topic
DLQ_REPLAY("dlq-replay", false, CleanupPolicy.DELETE);

// DlqEntryKey.java - EMPTY (no identity fields)
public class DlqEntryKey {
    // No fields!
}
```

**Problems**:
1. Compacted topics retain only the latest value per key
2. `DlqEntryKey` is empty → all entries serialize to the same compaction point → only latest entry survives
3. Defeats DLQ purpose: should preserve all rejections for audit trail
4. Global store not queryable for operator-driven replay (read-only query interface)
5. No per-surface isolation: all sources mixed together
6. Uncontrolled growth: unbounded retention of all rejection types in single store

#### Recommended Approach: Append-Only Per-Surface Topics

**Topology Change**:

Replace the single compacted topic with append-only DLQ topics for included external execution ingress surfaces only:

```
<tenant>.<namespace>.dlq.process-instance          (CleanupPolicy.DELETE, retention=30d/90d/configurable)
<tenant>.<namespace>.dlq.message-event             (CleanupPolicy.DELETE, retention=30d/90d/configurable)
<tenant>.<namespace>.dlq.definitions               (CleanupPolicy.DELETE, retention=30d/90d/configurable)
... additional included execution-ingress DLQ topics follow the same pattern ...
```
Example trimmed for brevity.
Use adjacent phase/file bullets as the source of truth.
```
/**
 * Unified envelope for all DLQ entries.
 * Carries both raw bytes (for potential re-decode) and structured metadata (for immediate triage).
 * Serialized as JSON for console accessibility.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@RegisterForReflection
public class DlqEnvelope {
    
    /** Kafka topic from which message was rejected */
    private String sourceTopic;
    
    /** Raw key bytes from Kafka record (may be null for some topics) */
    @Nullable
    private byte[] keyBytes;
    
    /** Raw value bytes: CBOR payload always preserved, even if decode fails */
    private byte[] valueBytes;
    
    /** Snapshot of Kafka headers at time of rejection (preserves context for re-validation) */
    private Map<String, String> headers;
    
    /** Stable machine-readable reason code for classification and metrics */
    private String reasonCode;
    
    /** Human-readable explanation of rejection reason */
    private String reasonText;
    
    /** Timestamp when rejection occurred (engine time) */
    private long rejectionTimestampMs;
    
    /** Engine instance ID that rejected the message (for multi-node debugging) */
    private String engineInstanceId;
    
    /** 
     * Optional: For known message types that were partially decoded before failure,
     * a structured JSON summary to aid operator triage (e.g., processInstanceId, trigger type).
     * Null if decode failed before any structures were available.
     */
    @Nullable
    private String decodedSummaryJson;
    
    /** 
     * Optional: Additional context specific to rejection reason (e.g., failed keyId, expected issuer).
     * Serialized as JSON object.
     */
    @Nullable
    private String additionalContextJson;

    /**
     * Optional: Lineage information for replayed messages, including source message details.
     * Populated for replay commands to trace origin of DLQ entries.
     */
    @Nullable
    private Lineage lineage;
}

/**
 * Lineage information for tracing message origin and transformations.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@RegisterForReflection
public class Lineage {
    
    /** Original source topic */
    private String sourceTopic;
    
    /** Original source partition */
    private int sourcePartition;
    
    /** Original source offset */
    private long sourceOffset;
    
    /** Original message timestamp */
    private long sourceTimestampMs;
    
    /** Hash of the original message content */
    private String sourceMessageHash;
    
    /** ID of the key used for signing the original message */
    private String sourceSignatureKeyId;
    
    /** Signature of the original message */
    private String sourceSignature;
}
```

#### Reason Code Taxonomy

Reason codes are stable identifiers used for classification, metrics, and operator filtering:

```
Example trimmed for brevity.
Use adjacent phase/file bullets as the source of truth.
```

**Usage**:
- Operators filter DLQ by reason code: `reasonCode='SIGNATURE_VERIFICATION_FAILED' AND engineInstanceId='node-2'`
- Metrics dashboards track rejections by reason: `taktx.dlq.rejections_total{surface, reason_code}`
- Alerts on spikes: threshold on `reasonCode='REPLAY_DETECTED'` may indicate concurrency issue
- Console auto-suggests remediation based on reason code

#### Headers Snapshot Format

The `headers` field stores a snapshot of Kafka record headers as `Map<String, String>` (header name → base64-encoded value):

```json
{
  "headers": {
    "tx-sig": "base64_encoded_signature_bytes",
    "X-TaktX-Signature-KeyId": "key-2026-05-01-alpha-001",
    "Authorization": "Bearer eyJhbGc...",
    "X-Tenant-Id": "tenant-123",
    "X-Request-Id": "req-abc-def-ghi"
  }
}
```
Example trimmed for brevity.
Use adjacent phase/file bullets as the source of truth.
```
<tenant>.<namespace>.dlq.replay.process-instance          (input: operator-approved replay commands)
<tenant>.<namespace>.dlq.replay.message-event             (input: operator-approved replay commands)
<tenant>.<namespace>.dlq.replay.definitions               (input: operator-approved replay commands)
<tenant>.<namespace>.dlq.replay-results.process-instance  (output: replay success/failure audit)
<tenant>.<namespace>.dlq.replay-results.message-event     (output: replay success/failure audit)
<tenant>.<namespace>.dlq.replay-results.definitions       (output: replay success/failure audit)
```

**Option B: Unified Replay Topic with Source Hint**
```
<tenant>.<namespace>.dlq.replay-commands                  (input: operator-approved commands with sourceTopic hint)
<tenant>.<namespace>.dlq.replay-results                   (output: replay results)
```

**Recommendation**: Use **Option A** for clarity and independent scaling.

#### Replay Command Envelope

Operators consume from DLQ topics, prepare correction metadata and payload, and submit a replay command:

```java
Example trimmed for brevity.
Use adjacent phase/file bullets as the source of truth.
```

#### Replay Processor Logic

```java
Example trimmed for brevity.
Use adjacent phase/file bullets as the source of truth.
```

#### Console-Side Replay Contract

The console needs to:
1. **Read DLQ topics** via Kafka consumer to display rejected messages
2. **Deserialize DlqEnvelope** (JSON format accessible)
3. **Display rejection reason code and context** to operator
4. **Allow operator to**:
   - View raw payload (hex/base64)
   - Attempt to re-decode with schema selector (show as JSON if successful)
   - Modify payload (edit JSON, re-encode to CBOR)
   - Modify headers (change Authorization, add notes)
   - Select validation policy (STRICT, OPERATOR_OVERRIDE)
5. **Dry-run replay**: validate payload, deserialization, and authorization without execution/publish
6. **Serialize DlqReplayCommand** and publish to replay topic
7. **Monitor replay-results topic** for success/failure feedback

**Engine Responsibility**: Provide reliable replay topics, enforce validation policies, enforce destination-topic constraints, sign replayed messages with ENGINE role keys, and publish audit results.

**Console Responsibility**: UI, schema resolution (optional; can ask engine API), approvals workflow, corrections, audit logging of operator actions.

---

### 1.5 Endpoint Choice: Compacted vs. Append-Only

| Aspect | Compacted Topic | Append-Only Topic |
|--------|-----------------|-------------------|
| **Audit trail** | ❌ Lost (only latest per key) | ✅ Complete (all messages preserved) |
| **Immutability** | ❌ Rewritten during compaction | ✅ Append-only (immutable history) |
| **Queryability** | ❌ Global store only (read-only) | ✅ Offset-based queries easily |
| **Operator access** | ❌ Complex (requires API) | ✅ Direct topic consumer |
| **Multi-node consistency** | ✅ Single source of truth | ⚠️ Replication same as any topic |
| **Retention config** | ❌ Single policy for all | ✅ Independent per-surface config |
| **Replay mechanism** | ❌ State machine unclear | ✅ Explicit replay topics simple |
| **Storage footprint** | ✅ Smallest (compacted) | ⚠️ Larger (but configurable retention) |

**Decision (final, implemented)**: **Append-only unified topic** (`dlq`). Audit trail preservation and operator access are critical for DLQ utility. Per-surface routing is achieved via `DlqEnvelope.sourceTopic` — see the Topology Decision section above for the rationale.

---

## Part 2: Implementation Plan

### Phase Overview

The implementation is organized into 4 phases:

- **Phase 1**: Fix compilation errors and establish foundational DLQ infrastructure (reason codes, envelope, publishers)
- **Phase 2**: Wire included execution-ingress rejections to DLQ and define non-DLQ handling for excluded topics
- **Phase 3**: Implement replay mechanism (processor, command envelope, replay topics)
- **Phase 4**: Observability and operational tooling (metrics, logging, dashboards)

---

### Phase 1: Foundational Infrastructure

**Goal**: Establish DLQ envelope, reason codes, and basic publisher infrastructure. Fix compilation errors.

#### Phase 1.1: Fix Compilation Errors

**File**: `taktx-engine/src/main/java/io/taktx/engine/pi/ProcessInstanceProcessor.java`

- **Issue**: Line 589 has incomplete DLQ entry constructor
- **Fix**: Complete constructor call with all required arguments (processInstanceId, trigger, headers, data)
- **Affected method**: `processResultAndForward()` when handling unhandled error events
- **Effort**: <1 hour

**File**: `taktx-shared/src/main/java/io/taktx/dto/DlqEntryDTO.java`

- **Issue**: Abstract parent field `topicName` never assigned by subclasses
- **Fix**: Move `topicName` to concrete subclasses or pass as constructor argument
- **Effort**: <1 hour

**File**: `taktx-engine/src/main/java/io/taktx/engine/pd/DlqReplayProcessor.java`

- **Issue**: 15 unused imports, empty processor skeleton
- **Fix**: Remove unused imports/fields; implement basic structure (init complete, process as no-op for now)
- **Effort**: <1 hour

---

#### Phase 1.2: Create Unified DLQ Envelope

**New File**: `taktx-shared/src/main/java/io/taktx/dto/DlqEnvelope.java`

```java
Example trimmed for brevity.
Use adjacent phase/file bullets as the source of truth.
```

- Serialization: JSON (ConfigMapper with `ObjectMapper` factory)
- Keying: None (append-only topics don't need typed keys)
- Registration: `@RegisterForReflection` for Quarkus GraalVM

**Effort**: 2 hours (including tests)

---

#### Phase 1.3: Define Reason Codes Enumeration

**New File**: `taktx-shared/src/main/java/io/taktx/dto/DlqReasonCode.java`

```java
Example trimmed for brevity.
Use adjacent phase/file bullets as the source of truth.
```

**Effort**: 1 hour (including javadoc comments)

---

#### Phase 1.4: Create DLQ Publisher Service

**New File**: `taktx-engine/src/main/java/io/taktx/engine/dlq/DlqPublisher.java`

```java
Example trimmed for brevity.
Use adjacent phase/file bullets as the source of truth.
```

**Dependency Injection**: Service bean in engine, injected into deserializers and processors

**Effort**: 3 hours (including error handling, metrics integration)

---

#### Phase 1.5: Update Topics and Stores Configuration

**File**: `taktx-shared/src/main/java/io/taktx/Topics.java`

Add new DLQ topics:

```java
// Replace single compacted DLQ
// DLQ("dlq", false, CleanupPolicy.COMPACT),  // OLD - remove

// ADD new DLQ topics only for included execution-ingress surfaces
DLQ_PROCESS_INSTANCE("dlq.process-instance", false, CleanupPolicy.DELETE),
DLQ_MESSAGE_EVENT("dlq.message-event", false, CleanupPolicy.DELETE),
DLQ_DEFINITIONS("dlq.definitions", false, CleanupPolicy.DELETE),

// ADD replay topics
DLQ_REPLAY_PROCESS_INSTANCE("dlq.replay.process-instance", false, CleanupPolicy.DELETE),
DLQ_REPLAY_MESSAGE_EVENT("dlq.replay.message-event", false, CleanupPolicy.DELETE),
DLQ_REPLAY_DEFINITIONS("dlq.replay.definitions", false, CleanupPolicy.DELETE),

// ADD replay results topics
DLQ_REPLAY_RESULTS_PROCESS_INSTANCE("dlq.replay-results.process-instance", false, CleanupPolicy.DELETE),
DLQ_REPLAY_RESULTS_MESSAGE_EVENT("dlq.replay-results.message-event", false, CleanupPolicy.DELETE),
DLQ_REPLAY_RESULTS_DEFINITIONS("dlq.replay-results.definitions", false, CleanupPolicy.DELETE),

// REMOVE old topics
// DLQ_REPLAY("dlq-replay", false, CleanupPolicy.DELETE),
```

**File**: `taktx-engine/src/main/java/io/taktx/engine/pd/Stores.java`

Remove old DLQ store (or update to match new topics):

```java
// Remove: DLQ(Topics.DLQ.getTopicName()),
// ADD (if using state stores for DLQ indexing later):
// DLQ_PROCESS_INSTANCE(Topics.DLQ_PROCESS_INSTANCE.getTopicName()),
```

**Effort**: 1 hour

---

#### Phase 1.6: Update Topology Producer

**File**: `taktx-engine/src/main/java/io/taktx/engine/generic/TopologyProducer.java`

Replace `setupDlq()` method with new topology:

```java
Example trimmed for brevity.
Use adjacent phase/file bullets as the source of truth.
```

**Update Serdes**:

```java
public static final Serde<DlqEnvelope> DLQ_ENVELOPE_SERDE = 
    new JsonSerde<>(DlqEnvelope.class);
public static final Serde<DlqReplayCommand> DLQ_REPLAY_COMMAND_SERDE = 
    new JsonSerde<>(DlqReplayCommand.class);
```
Example trimmed for brevity.
Use adjacent phase/file bullets as the source of truth.
```
public ProcessInstanceTriggerEnvelope deserialize(String topic, Headers headers, byte[] data) {
    try {
        ProcessInstanceTriggerDTO trigger = decode(data);
        String replayRoutingKeyHint = extractReplayRoutingKeyHint(headers);
        
        // Signature verification logic...
        if (sigHeader == null || sigHeader.value() == null) {
            // No signature (may or may not be required)
            return new ProcessInstanceTriggerEnvelope(data, trigger, false, null)
                .withReplayRoutingKeyHint(replayRoutingKeyHint);
        }
        
        // ... signature verification checks ...
        if (publicKeyBase64 == null) {
            // Publish DLQ entry
            dlqPublisher.publishRejection(
                topic,
                null,  // keyBytes not applicable
                data,
                headersMap(headers),
                DlqReasonCode.SIGNATURE_KEY_UNKNOWN,
                "Unknown or revoked signing keyId='" + keyId + "'",
                Optional.of(decodedSummaryJson(trigger)),
                Optional.of(Map.of("keyId", keyId).toJson())
            );
            
            return new ProcessInstanceTriggerEnvelope(
                data, trigger, false, keyId,
                "Unknown or revoked signing keyId='" + keyId + "' — treating as security violation")
                .withReplayRoutingKeyHint(replayRoutingKeyHint);
        }
        
        if (!Ed25519Service.verify(data, signatureBytes, publicKeyBase64)) {
            // Publish DLQ entry
            dlqPublisher.publishRejection(
                topic,
                null,
                data,
                headersMap(headers),
                DlqReasonCode.SIGNATURE_VERIFICATION_FAILED,
                "Ed25519 signature verification failed for keyId=" + keyId,
                Optional.of(decodedSummaryJson(trigger)),
                Optional.of(Map.of("keyId", keyId).toJson())
            );
            
            return new ProcessInstanceTriggerEnvelope(data, trigger, false, keyId, ...)
                .withReplayRoutingKeyHint(replayRoutingKeyHint);
        }
        
        return new ProcessInstanceTriggerEnvelope(data, trigger, true, keyId);
        
    } catch (CborDecodeException e) {
        dlqPublisher.publishRejection(
            topic, null, data, headersMap(headers),
            DlqReasonCode.CBOR_DECODE_ERROR,
            "CBOR decoding failed: " + e.getMessage(),
            Optional.empty(),
            Optional.empty()
        );
        
        return new ProcessInstanceTriggerEnvelope(data, null, false, null,
            "CBOR decode error: " + e.getMessage());
    } catch (Exception e) {
        dlqPublisher.publishRejection(
            topic, null, data, headersMap(headers),
            DlqReasonCode.UNKNOWN_REJECTION_REASON,
            "Unexpected deserialization error: " + e.getMessage(),
            Optional.empty(),
            Optional.empty()
        );
        
        return new ProcessInstanceTriggerEnvelope(data, null, false, null,
            "Unexpected error: " + e.getMessage());
    }
}
```

**File**: `taktx-engine/src/main/java/io/taktx/engine/pi/ProcessInstanceProcessor.java`

Update `handleUnDecodedTrigger()` and other DLQ points:

```java
Example trimmed for brevity.
Use adjacent phase/file bullets as the source of truth.
```

Complete line 589 constructor:

```java
Example trimmed for brevity.
Use adjacent phase/file bullets as the source of truth.
```

**Dependency Injection**: Add `DlqPublisher` field to processor

**Effort**: 4 hours (including all error points)

---

#### Phase 2.2: Schedule-Commands Surface

**File**: `taktx-engine/src/main/java/io/taktx/engine/pd/ScheduleCommandDeserializer.java`

Convert from throwing exceptions to publishing DLQ entries:

```java
Example trimmed for brevity.
Use adjacent phase/file bullets as the source of truth.
```

**Note**: The deserializer should publish DLQ entries but still throw `DeserializationException` to signal Kafka Streams that deserialization failed. The global handler `ContinueOnDeserializationErrorHandler` will catch it and skip the record (logging is sufficient after DLQ publish).

**Effort**: 2 hours

---

#### Phase 2.3: Topic-Meta-Requested Surface

**File**: `taktx-engine/src/main/java/io/taktx/engine/topicmanagement/DynamicTopicManager.java`

Update rejection handling:

```java
Example trimmed for brevity.
Use adjacent phase/file bullets as the source of truth.
```

Remove or update `publishRejectedRequestedTopic()` to publish DLQ entry:

```java
// REMOVE: This method is no longer needed
// private void publishRejectedRequestedTopic(String topicName) { ... }

// DLQ publishing now handled in exception catch blocks above
```

**Effort**: 2 hours

---

#### Phase 2.4: Authorization Service

**File**: `taktx-engine/src/main/java/io/taktx/engine/security/EngineAuthorizationService.java`

The authorization service throws exceptions; we need to catch these at the processor level (where we have context) and publish DLQ entries. Update processors to wrap authorization calls:

```java
// In ProcessInstanceProcessor.process()
try {
    engineAuthorizationService.authorize(envelope);
} catch (AuthorizationTokenException e) {
    // Determine specific reason code
    DlqReasonCode reasonCode = mapAuthorizationErrorToReasonCode(e);
    
    dlqPublisher.publishRejection(
        context.recordMetadata().topic(),
        null,
        envelope.data(),
        headersMap(context.headers()),
        reasonCode,
        e.getMessage(),
        Optional.of(decodedSummaryJson(envelope.trigger())),
        Optional.empty()
    );
    
    // Record incident instead of processing further
    InstanceResult instanceResult = InstanceResult.empty();
    forwarder.forward(context, instanceResult, ..., ...);
    return;
}
```

Helper method:

```java
private DlqReasonCode mapAuthorizationErrorToReasonCode(AuthorizationTokenException e) {
    String message = e.getMessage();
    if (message.contains("JWT")) {
        if (message.contains("missing")) return DlqReasonCode.JWT_MISSING;
        if (message.contains("expired")) return DlqReasonCode.JWT_EXPIRED;
        if (message.contains("signature")) return DlqReasonCode.JWT_SIGNATURE_INVALID;
        return DlqReasonCode.JWT_MALFORMED;
    }
    if (message.contains("role")) return DlqReasonCode.INSUFFICIENT_ROLE;
    if (message.contains("scope")) return DlqReasonCode.INSUFFICIENT_SCOPE;
    if (message.contains("replay")) return DlqReasonCode.REPLAY_DETECTED;
    return DlqReasonCode.AUTHORIZATION_FAILED;
}
```

**Effort**: 2 hours

---

#### Phase 2.5: Global Deserialization Handler

**File**: `taktx-engine/src/main/java/io/taktx/engine/generic/ContinueOnDeserializationErrorHandler.java`

Enhance to publish DLQ entries for poison records:

```java
Example trimmed for brevity.
Use adjacent phase/file bullets as the source of truth.
```

**Effort**: 1 hour

---

#### Phase 2.6: Remove Old DLQ Infrastructure

- Remove unused `DlqEntryDTO`, `DlqEntryKey`, `ProcessInstanceDlqEntryDTO`, `ProcessDefinitionDlqEntryDTO` (or deprecate for gradual transition)
- Remove `InstanceResult.addDlqEntry()` and related forwarder logic (replace with direct `DlqPublisher` calls)
- Remove old branching logic in `TopologyProducer` that routes to compacted DLQ
- Remove old `DlqReplayProcessor` skeleton

**Effort**: 1 hour

---

#### Phase 2.7: Testing

**Test Files**:
- `ProcessInstanceTriggerEnvelopeDeserializerTest`: Unit tests for DLQ publishing on signature failures
- `ScheduleCommandDeserializerTest`: Unit tests for DLQ publishing on signature failures
- `DynamicTopicManagerTest`: Integration test for authorization failure → DLQ
- `ProcessInstanceProcessorTest`: Integration test for authorization and business logic failures → DLQ

**Effort**: 4 hours

**Total Phase 2 Effort**: ~18 hours

---

### Phase 3: Replay Mechanism

**Goal**: Implement operator-driven replay from DLQ back into processing.

#### Phase 3.1: Create Replay Command & Result Envelopes

**New File**: `taktx-shared/src/main/java/io/taktx/dto/DlqReplayCommand.java`

```java
Example trimmed for brevity.
Use adjacent phase/file bullets as the source of truth.
```

**New File**: `taktx-shared/src/main/java/io/taktx/dto/DlqReplayResult.java`

```java
Example trimmed for brevity.
Use adjacent phase/file bullets as the source of truth.
```

**Effort**: 1 hour

---

#### Phase 3.2: Implement Replay Processors

**New File**: `taktx-engine/src/main/java/io/taktx/engine/dlq/DlqProcessInstanceReplayProcessor.java`

```java
Example trimmed for brevity.
Use adjacent phase/file bullets as the source of truth.
```

**Similar processors** for schedule-commands and topic-meta-requested (Phase 3.3, 3.4).

**Effort**: 4 hours

---

#### Phase 3.3 & 3.4: Schedule-Commands & Topic-Meta Replay Processors

Similar to Phase 3.2, but tailored to each surface's validation logic:

- `DlqScheduleCommandReplayProcessor`: Validates Ed25519 signature, ENGINE role only
- `DlqTopicMetaReplayProcessor`: Validates JWT, CLIENT role minimum, and topic-specific checks

**Effort**: 3 hours each (6 hours total)

---

#### Phase 3.5: Topology Integration

**File**: `taktx-engine/src/main/java/io/taktx/engine/generic/TopologyProducer.java`

Update `setupDlqReplayProcessors()` to instantiate processors:

```java
Example trimmed for brevity.
Use adjacent phase/file bullets as the source of truth.
```

**Effort**: 2 hours

---

#### Phase 3.6: Testing

**Test Files**:
- `DlqProcessInstanceReplayProcessorTest`: Unit tests for replay with different validation policies, success/failure cases
- `DlqScheduleCommandReplayProcessorTest`: Tests for ENGINE role enforcement during replay
- `DlqTopicMetaReplayProcessorTest`: Tests for CLIENT role enforcement, topic validation during replay

**Integration Tests**:
- End-to-end: Publish to DLQ -> trigger replay via console -> verify derived message is emitted with lineage and audit trail updated

**Effort**: 4 hours

**Total Phase 3 Effort**: ~19 hours

---

### Phase 4: Observability & Operational Tooling

**Goal**: Add structured logging, metrics, and operational insights for DLQ.

#### Phase 4.1: Structured Logging

**DEPRECATED**: Simple structured log entries at rejection points:

```java
log.info("security_rejection",
    "surface", "process-instance",
    "reasonCode", reasonCode.name(),
    "engineInstanceId", engineInstanceService.getInstanceId(),
    "outcomeAction", "published_to_dlq"
);
```

Use SLF4J structured logging (if logging facade supports it) or JSON logging:

```json
{
  "event": "security_rejection",
  "timestamp": "2026-05-01T14:23:45Z",
  "surface": "process-instance",
  "reasonCode": "SIGNATURE_VERIFICATION_FAILED",
  "engineInstanceId": "node-2",
  "outcomeAction": "published_to_dlq"
}
```

**Effort**: 1 hour

---

#### Phase 4.2: Metrics

Add Micrometer/Prometheus metrics:

```java
Example trimmed for brevity.
Use adjacent phase/file bullets as the source of truth.
```

Update DLQ publisher and replay processors to emit metrics on each action.

**Effort**: 2 hours

---

#### Phase 4.3: Alerting Rules

Recommended Prometheus alerting rules (for operators):

```yaml
Example trimmed for brevity.
Use adjacent phase/file bullets as the source of truth.
```

Severity-driven operations policy:
- `CRITICAL` (for example `REPLAY_DETECTED`): immediate page/incident.
- `HIGH` (for example signature/trust failures): alert on threshold breach.
- `MEDIUM` and `LOW`: dashboard/trend monitoring unless overridden by tenant policy.

**Effort**: 1 hour

---

#### Phase 4.4: Operational Procedures

Document in new file: `docs/dlq-operations.md`

Topics:
- Querying DLQ topics by reason code
- Approving and submitting replay commands
- Monitoring replay results
- Archiving old DLQ entries
- Disaster recovery (if DLQ topic is corrupted)

**Effort**: 2 hours

---

#### Phase 4.5: Console Integration Contract (Engine Side)

Define APIs/data structures that console will rely on:

**Engine Provides**:
1. **DLQ Topics**: Three append-only topics that console can consume directly
   - `<tenant>.<namespace>.dlq.process-instance`
   - `<tenant>.<namespace>.dlq.schedule-commands`
   - `<tenant>.<namespace>.dlq.topic-meta-requested`
2. **DlqEnvelope Schema**: JSON serialization with reason codes, headers, and optional decoded summary
3. **Replay Topics**: Three input topics that console publishes DlqReplayCommand to
   - `<tenant>.<namespace>.dlq.replay.process-instance`
   - `<tenant>.<namespace>.dlq.replay.schedule-commands`
   - `<tenant>.<namespace>.dlq.replay.topic-meta-requested`
4. **Replay Results Topics**: Three output topics that console can monitor for audit trail
   - `<tenant>.<namespace>.dlq.replay-results.process-instance`
   - `<tenant>.<namespace>.dlq.replay-results.schedule-commands`
   - `<tenant>.<namespace>.dlq.replay-results.topic-meta-requested`
5. **Reason Code Enumeration**: `DlqReasonCode` with stable names for metrics/filtering

**Console Implements**:
1. **DLQ Viewer**: UI to display rejected messages (filterable by reason code, time range, keyword)
2. **Payload Inspector**: Attempt JSON/CBOR decode of raw payload; show decoded summary if available
3. **Correction UI**: Modify payload/headers, select validation policy, publish to replay topic
4. **Replay Monitor**: Track replay results, display success/failure to operator
5. **Lineage Visualization**: Show message origin and correction history

**Effort**: 1 hour (documentation, no code changes)

**Total Phase 4 Effort**: ~8 hours

---

## Part 3: Testing Strategy

### Unit Tests

Each module has targeted unit tests:

| Module | Tests | Coverage |
|--------|-------|----------|
| `DlqPublisher` | Header conversion, topic resolution, envelope serialization, error handling | 85% |
| `DlqReasonCode` | Enum values, category mapping | 100% |
| `ProcessInstanceTriggerEnvelopeDeserializer` | Signature failure scenarios, reason code assignment, DLQ publish | 80% |
| `ScheduleCommandDeserializer` | Signature failures, ENGINE role enforcement | 85% |
| `DlqProcessInstanceReplayProcessor` | Validation policies (STRICT, OPERATOR_OVERRIDE), success/failure paths | 80% |

**Total Unit Test Effort**: 8 hours

### Integration Tests

End-to-end scenarios:

| Scenario | Setup | Verification |
|----------|-------|--------------|
| Signature failure -> DLQ -> Replay | Publish bad signature to process-instance, approve replay via DLQ | Message replayed, replay-results shows SUCCESS |
| Authorization failure -> DLQ | Publish without JWT, expect authorization rejection | DLQ entry contains JWT_MISSING reason |
| Replay with OPERATOR_OVERRIDE | Publish corrected payload + approved override context, replay with OPERATOR_OVERRIDE | Enforces replay authn/authz and signature checks while applying approved exception path |
| Replay with schema mismatch | Replay schemaVersion v3 payload on engine expecting v5 | STRICT fails with compatibility reason; OPERATOR_OVERRIDE logs warning + approved exception path |
| Multiple surfaces | Inject failures on all three surfaces simultaneously | Each DLQ topic receives correct reason codes |
| Metrics & alerting | Generate high rejection rate | Prometheus counter increments, alert fires if threshold exceeded |
| Console integration | Use console to view DLQ, submit replay | Replay succeeds, audit trail in console matches |

**Total Integration Test Effort**: 6 hours

### Negative Tests

Edge cases and failure modes:

| Test | Input | Expected Behavior |
|------|-------|-------------------|
| Malformed DLQ entry | Corrupt envelope bytes/format | Deserializer gracefully skips, logs error |
| Replay validation fails | STRICT policy + bad signature | Replay marked FAILED, result published |
| Destination mismatch blocked | Replay destination doesn't match source tenant/namespace/surface | Engine rejects replay with validation error and audit record |
| DLQ publish fails | Network error/timeout | DlqPublisher raises exception, metric recorded |
| Missing replay destination | DlqReplayCommand without destinationTopic | Validation error, replay marked FAILED |

**Total Negative Test Effort**: 4 hours

**Total Testing Effort**: ~18 hours

---

## Part 4: Migration & Rollout

### Pre-Rollout Checklist

- [ ] All compilation errors fixed
- [ ] Phase 1 infrastructure tested (envelope, reason codes, publisher)
- [ ] Phase 2 rejection surfaces integrated and tested
- [ ] Phase 3 replay mechanism implemented and tested
- [ ] Phase 4 observability active (metrics, logging)
- [ ] DLQ topics pre-created on Kafka broker
- [ ] Retention policies configured by environment
- [ ] Operator documentation complete
- [ ] Console team notified of topic structure and replay flow
- [ ] Alerting rules deployed to monitoring

### Gradual Rollout

1. **Day 1: Deploy to dev/test environments**
   - Verify DLQ entries flowing
   - Validate reason codes across surfaces
   - Manual replay testing

2. **Day 2–3: Staging environment**
   - Load test (high volume rejections)
   - Monitor metrics for anomalies
   - Console team integration test

3. **Day 4: Production canary (1–2 instances)**
   - Monitor logs and metrics
   - Verify DLQ entries are durable
   - Operator spot-checks

4. **Day 5: Production full rollout**
   - Deploy to all instances
   - Monitor for 1 week
   - On-call monitoring of alerting rules

### Rollback Plan

If critical issues arise:
1. Disable DLQ publishing (feature flag in `DlqPublisher`)
2. Old DLQ infrastructure remains dormant but available
3. Revert to previous engine version if necessary
4. Investigate issues post-incident

---

## Part 5: Timeline & Resource Allocation

### Summary Effort by Phase

| Phase | Duration | Resource |
|-------|----------|----------|
| Phase 1: Infrastructure | 13 hours | 2 engineers (parallel) |
| Phase 2: Surface Integration | 18 hours | 2–3 engineers (parallel surfaces) |
| Phase 3: Replay Mechanism | 19 hours | 2 engineers (parallel processors) |
| Phase 4: Observability | 8 hours | 1 engineer |
| Testing | 18 hours | 1–2 engineers (ongoing) |
| Documentation | (included above) | – |
| **Total** | **~76 hours** | **2–3 engineers, 3–4 weeks** |

### Recommended Schedule

**Week 1**:
- Phase 1 (13 hours): Wed–Thu
- Phase 2 prep: Thu–Fri

**Week 2**:
- Phase 2 (18 hours): Mon–Wed
- Testing phase 1–2: Thu–Fri

**Week 3**:
- Phase 3 (19 hours): Mon–Tue
- Phase 4 (8 hours): Wed
- Testing phase 3: Thu–Fri

**Week 4**:
- Testing & refinement
- Console integration
- Staging validation

**Week 5**:
- Production rollout (gradual)

---

## Part 6D: Architecture Review Disposition (May 2026)

This section captures architecture-team review outcomes and **supersedes earlier examples** where they conflict.

### Accepted and Integrated

1. **Lineage + Immutability Model (Critical) — ACCEPTED**

Replay is reframed as **"derive a new message from a failed source record"**, not "edit and resend same message".

- Original source record is immutable and preserved in DLQ.
- Replay emits a **new** message, with new signature and explicit lineage fields.
- Audit chain links `original -> dlqEntry -> correction -> replayedMessage`.

Add lineage fields to `DlqReplayCommand` and `DlqReplayResult`:

```json
{
  "lineage": {
    "sourceTopic": "...",
    "sourcePartition": 1,
    "sourceOffset": 12345,
    "sourceTimestampMs": 1714550000000,
    "sourceSignature": "...",
    "sourceSignatureKeyId": "...",
    "sourceMessageHash": "sha256:..."
  },
  "correction": {
    "type": "manual | auto",
    "reason": "...",
    "changedFields": ["headers.Authorization", "payload.variables.priority"]
  }
}
```

Implementation rule:
- Replayed output MUST include headers:
  - `dlq-lin`
  - `dlq-cid`
  - `dlq-off`

Replay signing authority:
- Replayed messages MUST always be newly signed.
- Signing authority is ENGINE role keys (engine-owned signing), not operator keys.
- Replay metadata should include signer provenance:

```json
{
  "replaySigner": "engine-instance-id",
  "replaySignatureKeyId": "..."
}
```

2. **Replay Validation Policies (Hardening) — ACCEPTED WITH ADJUSTMENT**

Earlier `STRICT | LENIENT | SKIP_VALIDATION` is replaced by:

- `STRICT` (default): all normal verification + authorization checks
- `OPERATOR_OVERRIDE`: still verifies structure, destination policy, and replay authn/authz; allows controlled bypass for selected checks with explicit reason + approval

`SKIP_VALIDATION` is removed from standard operation.

Optional emergency mode:
- `SKIP_VALIDATION` can exist only behind explicit feature flag (default `false`) and must require elevated approval + audit marker `emergencyBypass=true`.

3. **DLQ Dedup / Idempotency Metadata — ACCEPTED**

To mitigate duplicate DLQ entries from retried deserialization/reprocessing, add source identity to `DlqEnvelope`:

```json
{
  "sourceTopic": "...",
  "sourcePartition": 1,
  "sourceOffset": 12345,
  "sourceTimestampMs": 1714550000000,
  "sourceMessageHash": "sha256:..."
}
```

Dedup strategy:
- Dedup key = `sourceTopic + sourcePartition + sourceOffset + sourceMessageHash`
- Console and replay tooling should group records on this key.
- Engine must remain append-only; dedup is logical, not physical deletion.

4. **Schema/Version Awareness — ACCEPTED**

Add explicit decode context to `DlqEnvelope`:

```json
{
  "messageType": "StartCommandDTO",
  "schemaVersion": 3,
  "decoderVersion": "engine-1.4.2",
  "schemaFingerprint": "sha256:..."
}
```

Rules:
- If decode fails before message type inference, fields may be null.
- Replay tooling should warn on schema mismatch between envelope and current console/engine schema.

5. **Severity Classification for Security Events — ACCEPTED**

Add `severity` to envelope and deterministic mapping from reason code.

```json
{
  "reasonCode": "REPLAY_DETECTED",
  "severity": "CRITICAL"
}
```

Baseline mapping:
- `LOW`: benign validation noise
- `MEDIUM`: business/data issues
- `HIGH`: signature or trust failures
- `CRITICAL`: replay attacks or systemic security anomalies

Examples:
- `SIGNATURE_VERIFICATION_FAILED` -> `HIGH`
- `SIGNATURE_KEY_REVOKED` -> `HIGH`
- `REPLAY_DETECTED` -> `CRITICAL`

6. **Compression Guidance — ACCEPTED**

DLQ remains JSON by default for operability. Add broker/topic compression guidance:
- Preferred: `zstd`
- Alternative: `lz4`

Operational note:
- Keep full headers and payload bytes in envelope for forensic value.
- Compression offsets JSON/base64 overhead sufficiently for expected DLQ volumes.

7. **Console Scope Expansion — ACCEPTED**

Add target capabilities to console contract:
- DLQ explorer (filters by reason, severity, process, time)
- Payload inspector (raw + decoded + schema mismatch hints)
- Correction UI (JSON/form modes + validation)
- Replay controls (approval, dry-run checks, batch replay)
- Lineage visualization (source -> rejection -> correction -> replay)

8. **Community vs Premium Positioning — ACCEPTED**

Community:
- DLQ topics + envelope + reason/severity metadata
- Kafka-native consumption and replay command ingestion

Premium/Ops Console:
- Rich explorer/filtering/search
- Decode/correction UI
- Replay approvals/workflows
- Lineage visualization
- RBAC + audit dashboards

### Accepted with Clarification

9. **Publishing from Deserializer — ACCEPTED WITH SAFEGUARDS**

Risk acknowledged: retries can create duplicates.

Clarification:
- Keep publishing near rejection point (including deserializer path) to avoid silent loss.
- Rely on source identity fields and logical dedup in tools/analytics.
- Where Kafka Streams metadata is unavailable, populate best-effort fields and include `captureStage` (`DESERIALIZER`, `PROCESSOR`, `ERROR_HANDLER`).

Explicit duplicate behavior:
- DLQ is append-only and may contain duplicates under retries/rebalances.
- Deduplication is logical (console/tooling), not enforced in engine write-path dedup.

10. **Replay Destination Safety — ACCEPTED**

Engine MUST enforce destination safety regardless of console behavior:
- `destinationTopic` must match original replay surface.
- `destinationTopic` must match source tenant + namespace.
- `destinationTopic` must be in per-processor whitelist.

11. **Schema Evolution Compatibility Strategy — ACCEPTED**

Compatibility behavior:
- `STRICT`: replay fails on incompatible schema.
- `OPERATOR_OVERRIDE`: replay allowed only with explicit approval and warning/audit markers.

Operational behavior:
- Console warns on schema mismatch (`schemaVersion`, `decoderVersion`, fingerprint).
- Engine logs compatibility decisions and includes reason/context in replay result.

12. **DLQ Envelope Growth / Retention Policy — ACCEPTED**

Clarification:
- DLQ is optimized for forensic debugging, not long-term cheap storage.
- Retention must be enforced per environment.
- Future options: tiered storage and cold archive (for example object storage) for long retention.

13. **Console Dry-Run Replay — ACCEPTED**

Add dry-run mode prior to publish:
- Validate payload format and schema.
- Execute deserialization + authorization checks without side effects.
- Return preview of pass/fail reasons to avoid replay loops.

---

## Part 7: Conclusion & Next Steps

This design document provides a comprehensive architecture for the TaktX Engine Dead Letter Queue feature. The implemented approach emphasises:

✅ **Audit Trail**: Append-only unified `dlq` topic preserves all rejections immutably; per-surface routing via `DlqEnvelope.sourceTopic`  
✅ **Operator Access**: Direct Kafka topic consumption for forensic analysis; `taktx-client` DLQ API for programmatic access  
✅ **Structured Metadata**: Unified envelope with reason codes, headers, and optional decoded summary  
✅ **Replay Mechanism**: Explicit operator-driven replay with `STRICT`/`OPERATOR_OVERRIDE` validation policies and full audit results  
✅ **Observability**: Micrometer metrics, structured logging, and Prometheus alert rules for operational insights  
✅ **Scalability**: Single shared topic with per-envelope metadata; independent per-surface topics can be introduced later if throughput or ACL isolation demands it  

### Open Questions — Resolved

All three open questions from the original design review have been resolved during implementation:

- **Open question 1 (RBAC for replay approval)**: The engine enforces destination-topic safety and ENGINE signing. Operator identity (`operatorId`) is a plain string in the Community tier. JWT-backed operator auth for replay approval is a Premium ops-console concern (see `docs/dlq-feature-matrix.md`).
- **Open question 2 (Console ownership)**: Console UI is a Premium ops-console feature built by the console team on top of the `taktx-client` DLQ API. All console interactions flow through the three DLQ topics — no additional engine API surface is required (see `docs/dlq-console-contract.md`).
- **Open question 3 (Retention sufficiency)**: 30/90-day retention is the baseline recommendation. Long-term cold-archive via Kafka Connect + object storage is documented in `docs/dlq-retention-policy.md` for regulatory retention requirements.

### Companion Documents

| Document | Purpose |
|---|---|
| `docs/dlq-implementation-backlog.md` | Full implementation history (checkpoints 1–18), all task status |
| `docs/dlq-console-contract.md` | Engine-console topic/DTO contract (DLQ-019) |
| `docs/dlq-feature-matrix.md` | Community vs Premium feature split (DLQ-020) |
| `docs/dlq-retention-policy.md` | Per-environment retention and storage guidance (DLQ-018) |

### Kickoff

Once this design is approved, implementation can proceed in phases without blockers. Phase 1 fixes compilation errors and establishes infrastructure; Phases 2–4 build out features incrementally with independent testing.

