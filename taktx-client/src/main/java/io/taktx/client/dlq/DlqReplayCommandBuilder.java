/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.client.dlq;

import io.taktx.dto.DlqEnvelope;
import io.taktx.dto.DlqLineageDTO;
import io.taktx.dto.DlqReplayCommand;
import io.taktx.dto.ReplayValidationPolicy;
import jakarta.annotation.Nullable;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Fluent builder for {@link DlqReplayCommand}.
 *
 * <p>Construct a builder from a {@link DlqEnvelope} to have lineage, dedup reference, and
 * destination topic automatically populated. Then supply the corrected payload and any metadata
 * before calling {@link #build()}.
 *
 * <pre>{@code
 * DlqReplayCommand cmd = DlqReplayCommandBuilder.from(envelope)
 *     .operatorId("ops-user@example.com")
 *     .correctedPayload(correctedBytes)
 *     .correctedHeaders(Map.of("Authorization", Base64.getEncoder().encodeToString(newJwt.getBytes())))
 *     .validationPolicy(ReplayValidationPolicy.STRICT)
 *     .dryRun(true)
 *     .build();
 * }</pre>
 *
 * <h2>DLQ entry reference</h2>
 *
 * <p>The {@code dlqEntryRef} uniquely identifies a DLQ entry within the {@code dlq} topic and is
 * derived from the envelope's source coordinates:
 *
 * <pre>{@code <sourceTopic>:<partition>:<offset>:<messageHash>}</pre>
 *
 * <p>When partition or offset are {@code null} (e.g. captured at the deserialiser stage before
 * Kafka metadata is available), those fields are substituted with {@code "?"}.
 *
 * <h2>Destination topic</h2>
 *
 * <p>The engine's replay processor expects the <em>bare</em> topic name (without tenant/namespace
 * prefix). {@link #from(DlqEnvelope)} sets this automatically from {@link
 * DlqEnvelope#getSourceTopic()}, which always carries the bare name.
 *
 * <h2>Community vs Premium</h2>
 *
 * <p>This builder is available in the Community tier — it interacts only with the standard {@code
 * dlq.replay} Kafka topic and requires no Premium console subscription.
 */
public class DlqReplayCommandBuilder {

  private String dlqEntryRef;
  private String operatorId;
  private long approvedAtMs = Instant.now().toEpochMilli();
  private String operatorNotes;
  private byte[] correctedValueBytes;
  private byte[] correctedKeyBytes;
  private Map<String, String> correctedHeaders;
  private String destinationTopic;
  private ReplayValidationPolicy validationPolicy = ReplayValidationPolicy.STRICT;
  private DlqLineageDTO lineage;
  private String overrideReason;
  private List<String> changedFields;
  private boolean dryRun = false;
  private Integer expectedSchemaVersion;

  /** Private — use {@link #from(DlqEnvelope)} or {@link #newBuilder()}. */
  private DlqReplayCommandBuilder() {}

  /**
   * Creates a builder pre-populated from a {@link DlqEnvelope}.
   *
   * <p>Automatically sets:
   *
   * <ul>
   *   <li>{@code dlqEntryRef} — stable identifier derived from source coordinates
   *   <li>{@code destinationTopic} — bare topic name from {@code envelope.sourceTopic}
   *   <li>{@code lineage} — full provenance from the envelope
   *   <li>{@code correctedValueBytes} — copied from the envelope's raw value bytes (operator can
   *       override with {@link #correctedPayload(byte[])})
   * </ul>
   *
   * @param envelope the DLQ entry to replay; must not be {@code null}
   * @return a builder initialised from the supplied envelope
   */
  public static DlqReplayCommandBuilder from(DlqEnvelope envelope) {
    if (envelope == null) {
      throw new IllegalArgumentException("DlqEnvelope must not be null");
    }
    DlqReplayCommandBuilder b = new DlqReplayCommandBuilder();
    b.dlqEntryRef = buildDlqEntryRef(envelope);
    b.destinationTopic = envelope.getSourceTopic();
    b.correctedValueBytes = envelope.getValueBytes();
    b.correctedHeaders = envelope.getHeaders();
    b.lineage =
        DlqLineageDTO.builder()
            .sourceTopic(envelope.getSourceTopic())
            .sourcePartition(envelope.getSourcePartition())
            .sourceOffset(envelope.getSourceOffset())
            .sourceTimestampMs(envelope.getSourceTimestampMs())
            .sourceMessageHash(envelope.getSourceMessageHash())
            .build();
    if (envelope.getSchemaVersion() != null) {
      b.expectedSchemaVersion = envelope.getSchemaVersion();
    }
    return b;
  }

  /**
   * Creates an empty builder for fully-manual command construction. Prefer {@link
   * #from(DlqEnvelope)} whenever an envelope is available.
   *
   * @return a new empty replay-command builder
   */
  public static DlqReplayCommandBuilder newBuilder() {
    return new DlqReplayCommandBuilder();
  }

  // ── Mandatory fields ─────────────────────────────────────────────────────────

  /**
   * Identity of the operator approving the replay (e.g. an email address or service account name).
   *
   * @param operatorId operator identity string; must not be {@code null} or blank
   * @return this builder
   */
  public DlqReplayCommandBuilder operatorId(String operatorId) {
    this.operatorId = operatorId;
    return this;
  }

  // ── Optional overrides ────────────────────────────────────────────────────────

  /**
   * Overrides the corrected payload bytes. When omitted, the raw {@code valueBytes} from the
   * original envelope are reused as-is.
   *
   * @param correctedValueBytes corrected CBOR-encoded payload bytes
   * @return this builder
   */
  public DlqReplayCommandBuilder correctedPayload(byte[] correctedValueBytes) {
    this.correctedValueBytes = correctedValueBytes;
    return this;
  }

  /**
   * Overrides the corrected key bytes. Required only for keyed surfaces; most DLQ ingress surfaces
   * use a {@code null} key.
   *
   * @param correctedKeyBytes corrected key bytes, or {@code null} for keyless records
   * @return this builder
   */
  public DlqReplayCommandBuilder correctedKey(@Nullable byte[] correctedKeyBytes) {
    this.correctedKeyBytes = correctedKeyBytes;
    return this;
  }

  /**
   * Sets the corrected Kafka headers snapshot. Values must be base64-encoded strings — the engine
   * replay processor decodes them before attaching to the forwarded record.
   *
   * <p>The engine always replaces {@code X-TaktX-Signature} with a fresh ENGINE-signed value; there
   * is no need to include it here.
   *
   * @param correctedHeaders map of header name → base64-encoded header value
   * @return this builder
   */
  public DlqReplayCommandBuilder correctedHeaders(Map<String, String> correctedHeaders) {
    this.correctedHeaders = correctedHeaders;
    return this;
  }

  /**
   * Sets the replay validation policy.
   *
   * <ul>
   *   <li>{@link ReplayValidationPolicy#STRICT} (default): all verification + schema checks must
   *       pass.
   *   <li>{@link ReplayValidationPolicy#OPERATOR_OVERRIDE}: schema version mismatch is allowed with
   *       an explicit {@link #overrideReason(String)}.
   * </ul>
   *
   * @param validationPolicy validation policy to apply during replay
   * @return this builder
   */
  public DlqReplayCommandBuilder validationPolicy(ReplayValidationPolicy validationPolicy) {
    this.validationPolicy = validationPolicy;
    return this;
  }

  /**
   * Enables dry-run mode. The engine runs all validation steps but does NOT forward the record to
   * the target ingress topic. The result status will be {@code DRY_RUN_PASSED} or {@code FAILED}.
   *
   * @param dryRun {@code true} to validate without replaying, {@code false} to perform a live
   *     replay
   * @return this builder
   */
  public DlqReplayCommandBuilder dryRun(boolean dryRun) {
    this.dryRun = dryRun;
    return this;
  }

  /**
   * Convenience method — enables dry-run mode. Equivalent to {@code dryRun(true)}.
   *
   * @return this builder
   */
  public DlqReplayCommandBuilder dryRun() {
    return dryRun(true);
  }

  /**
   * Human-readable justification required when using {@link
   * ReplayValidationPolicy#OPERATOR_OVERRIDE}. Also recorded in the replay-result audit trail.
   *
   * @param overrideReason operator-supplied reason for bypassing strict validation
   * @return this builder
   */
  public DlqReplayCommandBuilder overrideReason(String overrideReason) {
    this.overrideReason = overrideReason;
    return this;
  }

  /**
   * Documents which fields were changed in this correction (for the audit trail). Example: {@code
   * List.of("headers.Authorization", "payload.variables.priority")}.
   *
   * @param changedFields logical field paths changed by the operator
   * @return this builder
   */
  public DlqReplayCommandBuilder changedFields(List<String> changedFields) {
    this.changedFields = changedFields;
    return this;
  }

  /**
   * Optional free-text notes for the operator. Stored in the command but not evaluated by the
   * engine.
   *
   * @param operatorNotes free-text audit notes from the operator
   * @return this builder
   */
  public DlqReplayCommandBuilder operatorNotes(String operatorNotes) {
    this.operatorNotes = operatorNotes;
    return this;
  }

  /**
   * Sets the approval timestamp in epoch milliseconds. Defaults to the current system time at
   * builder creation.
   *
   * @param approvedAtMs approval timestamp in epoch milliseconds
   * @return this builder
   */
  public DlqReplayCommandBuilder approvedAtMs(long approvedAtMs) {
    this.approvedAtMs = approvedAtMs;
    return this;
  }

  /**
   * Overrides the expected schema version of the corrected payload. When unset the engine uses the
   * schema version recorded in the original envelope (populated automatically by {@link
   * #from(DlqEnvelope)}).
   *
   * @param expectedSchemaVersion schema version expected by the replay processor
   * @return this builder
   */
  public DlqReplayCommandBuilder expectedSchemaVersion(int expectedSchemaVersion) {
    this.expectedSchemaVersion = expectedSchemaVersion;
    return this;
  }

  /**
   * Overrides the destination topic (bare name, without tenant/namespace prefix). Normally derived
   * automatically from the envelope by {@link #from(DlqEnvelope)}.
   *
   * <p>Must be one of the 8 allowed ingress surfaces — the engine enforces this whitelist
   * regardless.
   *
   * @param destinationTopic bare destination topic name
   * @return this builder
   */
  public DlqReplayCommandBuilder destinationTopic(String destinationTopic) {
    this.destinationTopic = destinationTopic;
    return this;
  }

  /**
   * Explicitly sets the DLQ entry reference. Normally derived automatically from the envelope by
   * {@link #from(DlqEnvelope)}.
   *
   * @param dlqEntryRef stable DLQ entry identifier in {@code topic:partition:offset:hash} form
   * @return this builder
   */
  public DlqReplayCommandBuilder dlqEntryRef(String dlqEntryRef) {
    this.dlqEntryRef = dlqEntryRef;
    return this;
  }

  /**
   * Builds the {@link DlqReplayCommand}.
   *
   * @return the validated replay command
   * @throws IllegalStateException if required fields are missing
   */
  public DlqReplayCommand build() {
    if (operatorId == null || operatorId.isBlank()) {
      throw new IllegalStateException(
          "DlqReplayCommandBuilder: operatorId is required — call operatorId(\"...\")");
    }
    if (destinationTopic == null || destinationTopic.isBlank()) {
      throw new IllegalStateException(
          "DlqReplayCommandBuilder: destinationTopic is required — call destinationTopic(\"...\")");
    }
    if (correctedValueBytes == null) {
      throw new IllegalStateException(
          "DlqReplayCommandBuilder: correctedValueBytes is required — call correctedPayload(...)");
    }
    return DlqReplayCommand.builder()
        .dlqEntryRef(dlqEntryRef)
        .operatorId(operatorId)
        .approvedAtMs(approvedAtMs)
        .operatorNotes(operatorNotes)
        .correctedValueBytes(correctedValueBytes)
        .correctedKeyBytes(correctedKeyBytes)
        .correctedHeaders(correctedHeaders)
        .destinationTopic(destinationTopic)
        .validationPolicy(validationPolicy)
        .lineage(lineage)
        .overrideReason(overrideReason)
        .changedFields(changedFields)
        .dryRun(dryRun)
        .expectedSchemaVersion(expectedSchemaVersion)
        .build();
  }

  // ── Helpers ──────────────────────────────────────────────────────────────────

  /**
   * Builds a stable, human-readable dedup reference from an envelope's source coordinates. Format:
   * {@code <sourceTopic>:<partition>:<offset>:<messageHash>}.
   */
  static String buildDlqEntryRef(DlqEnvelope envelope) {
    String partition =
        envelope.getSourcePartition() != null ? envelope.getSourcePartition().toString() : "?";
    String offset =
        envelope.getSourceOffset() != null ? envelope.getSourceOffset().toString() : "?";
    String hash = envelope.getSourceMessageHash() != null ? envelope.getSourceMessageHash() : "?";
    return envelope.getSourceTopic() + ":" + partition + ":" + offset + ":" + hash;
  }
}
