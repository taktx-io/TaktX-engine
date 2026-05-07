/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.client.dlq;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.taktx.dto.DlqCaptureStage;
import io.taktx.dto.DlqEnvelope;
import io.taktx.dto.DlqReasonCode;
import io.taktx.dto.DlqReplayCommand;
import io.taktx.dto.DlqSeverity;
import io.taktx.dto.ReplayValidationPolicy;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link DlqReplayCommandBuilder} — covers DLQ-019 console contract. */
class DlqReplayCommandBuilderTest {

  // ── from(DlqEnvelope) ─────────────────────────────────────────────────────────

  @Test
  void from_populatesLineageFromEnvelope() {
    DlqEnvelope envelope = buildEnvelope();

    DlqReplayCommand cmd =
        DlqReplayCommandBuilder.from(envelope).operatorId("ops@example.com").build();

    assertThat(cmd.getLineage()).isNotNull();
    assertThat(cmd.getLineage().getSourceTopic()).isEqualTo("process-instance");
    assertThat(cmd.getLineage().getSourcePartition()).isEqualTo(2);
    assertThat(cmd.getLineage().getSourceOffset()).isEqualTo(12345L);
    assertThat(cmd.getLineage().getSourceMessageHash()).isEqualTo("sha256:abc");
    assertThat(cmd.getLineage().getSourceTimestampMs()).isEqualTo(1_714_550_000_000L);
  }

  @Test
  void from_setsDlqEntryRefFromCoordinates() {
    DlqEnvelope envelope = buildEnvelope();

    DlqReplayCommand cmd =
        DlqReplayCommandBuilder.from(envelope).operatorId("ops@example.com").build();

    assertThat(cmd.getDlqEntryRef()).isEqualTo("process-instance:2:12345:sha256:abc");
  }

  @Test
  void from_setsDestinationTopicFromSourceTopic() {
    DlqEnvelope envelope = buildEnvelope();

    DlqReplayCommand cmd =
        DlqReplayCommandBuilder.from(envelope).operatorId("ops@example.com").build();

    assertThat(cmd.getDestinationTopic()).isEqualTo("process-instance");
  }

  @Test
  void from_copiesValueBytesFromEnvelope() {
    byte[] payload = "original-payload".getBytes(StandardCharsets.UTF_8);
    DlqEnvelope envelope = buildEnvelope();
    envelope.setValueBytes(payload);

    DlqReplayCommand cmd =
        DlqReplayCommandBuilder.from(envelope).operatorId("ops@example.com").build();

    assertThat(cmd.getCorrectedValueBytes()).isEqualTo(payload);
  }

  @Test
  void from_copiesSchemaVersionFromEnvelope() {
    DlqEnvelope envelope = buildEnvelope();
    envelope.setSchemaVersion(1);

    DlqReplayCommand cmd =
        DlqReplayCommandBuilder.from(envelope).operatorId("ops@example.com").build();

    assertThat(cmd.getExpectedSchemaVersion()).isEqualTo(1);
  }

  @Test
  void from_copiesHeadersFromEnvelope() {
    DlqEnvelope envelope = buildEnvelope();
    Map<String, String> headers = Map.of("Authorization", "Bearer xxx");
    envelope.setHeaders(headers);

    DlqReplayCommand cmd =
        DlqReplayCommandBuilder.from(envelope).operatorId("ops@example.com").build();

    assertThat(cmd.getCorrectedHeaders()).containsEntry("Authorization", "Bearer xxx");
  }

  // ── Missing-partition / missing-offset scenario ───────────────────────────────

  @Test
  void from_handlesNullPartitionAndOffset() {
    DlqEnvelope envelope = buildEnvelope();
    envelope.setSourcePartition(null);
    envelope.setSourceOffset(null);

    DlqReplayCommand cmd =
        DlqReplayCommandBuilder.from(envelope).operatorId("ops@example.com").build();

    assertThat(cmd.getDlqEntryRef()).isEqualTo("process-instance:?:?:sha256:abc");
  }

  // ── Dry-run flag ──────────────────────────────────────────────────────────────

  @Test
  void dryRun_setsFlag() {
    DlqReplayCommand cmd =
        DlqReplayCommandBuilder.from(buildEnvelope())
            .operatorId("ops@example.com")
            .dryRun()
            .build();

    assertThat(cmd.isDryRun()).isTrue();
  }

  @Test
  void dryRunFalse_byDefault() {
    DlqReplayCommand cmd =
        DlqReplayCommandBuilder.from(buildEnvelope()).operatorId("ops@example.com").build();

    assertThat(cmd.isDryRun()).isFalse();
  }

  // ── Validation policy ─────────────────────────────────────────────────────────

  @Test
  void strictPolicy_isDefault() {
    DlqReplayCommand cmd =
        DlqReplayCommandBuilder.from(buildEnvelope()).operatorId("ops@example.com").build();

    assertThat(cmd.getValidationPolicy()).isEqualTo(ReplayValidationPolicy.STRICT);
  }

  @Test
  void operatorOverride_withReason() {
    DlqReplayCommand cmd =
        DlqReplayCommandBuilder.from(buildEnvelope())
            .operatorId("ops@example.com")
            .validationPolicy(ReplayValidationPolicy.OPERATOR_OVERRIDE)
            .overrideReason("Schema bumped during incident window")
            .changedFields(List.of("headers.Authorization"))
            .build();

    assertThat(cmd.getValidationPolicy()).isEqualTo(ReplayValidationPolicy.OPERATOR_OVERRIDE);
    assertThat(cmd.getOverrideReason()).isEqualTo("Schema bumped during incident window");
    assertThat(cmd.getChangedFields()).containsExactly("headers.Authorization");
  }

  // ── Corrected payload override ────────────────────────────────────────────────

  @Test
  void correctedPayload_overridesEnvelopeBytes() {
    byte[] corrected = "fixed-payload".getBytes(StandardCharsets.UTF_8);

    DlqReplayCommand cmd =
        DlqReplayCommandBuilder.from(buildEnvelope())
            .operatorId("ops@example.com")
            .correctedPayload(corrected)
            .build();

    assertThat(cmd.getCorrectedValueBytes()).isEqualTo(corrected);
  }

  // ── Validation errors ─────────────────────────────────────────────────────────

  @Test
  void build_throwsWhenOperatorIdMissing() {
    assertThatThrownBy(() -> DlqReplayCommandBuilder.from(buildEnvelope()).build())
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("operatorId");
  }

  @Test
  void build_throwsWhenCorrectedValueBytesNull() {
    DlqEnvelope envelope = buildEnvelope();
    envelope.setValueBytes(null);

    assertThatThrownBy(
            () -> DlqReplayCommandBuilder.from(envelope).operatorId("ops@example.com").build())
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("correctedValueBytes");
  }

  @Test
  void from_throwsWhenEnvelopeIsNull() {
    assertThatThrownBy(() -> DlqReplayCommandBuilder.from(null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  // ── buildDlqEntryRef static helper ───────────────────────────────────────────

  @Test
  void buildDlqEntryRef_allFieldsPresent() {
    DlqEnvelope env = buildEnvelope();
    assertThat(DlqReplayCommandBuilder.buildDlqEntryRef(env))
        .isEqualTo("process-instance:2:12345:sha256:abc");
  }

  @Test
  void buildDlqEntryRef_missingHash() {
    DlqEnvelope env = buildEnvelope();
    env.setSourceMessageHash(null);
    assertThat(DlqReplayCommandBuilder.buildDlqEntryRef(env))
        .isEqualTo("process-instance:2:12345:?");
  }

  // ── Helpers ─────────────────────────────────────────────────────────────────

  private static DlqEnvelope buildEnvelope() {
    DlqEnvelope env = new DlqEnvelope();
    env.setSourceTopic("process-instance");
    env.setSourcePartition(2);
    env.setSourceOffset(12345L);
    env.setSourceTimestampMs(1_714_550_000_000L);
    env.setSourceMessageHash("sha256:abc");
    env.setValueBytes("payload".getBytes(StandardCharsets.UTF_8));
    env.setHeaders(Map.of());
    env.setReasonCode(DlqReasonCode.AUTHORIZATION_FAILED);
    env.setSeverity(DlqSeverity.MEDIUM);
    env.setCaptureStage(DlqCaptureStage.PROCESSOR);
    env.setRejectionTimestampMs(System.currentTimeMillis());
    env.setEngineInstanceId("test-engine");
    return env;
  }
}
