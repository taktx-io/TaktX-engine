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

import io.taktx.dto.DlqEnvelope;
import io.taktx.dto.DlqLineageDTO;
import io.taktx.dto.DlqReasonCode;
import io.taktx.dto.DlqReplayCommand;
import io.taktx.dto.DlqReplayResult;
import io.taktx.dto.DlqSeverity;
import io.taktx.dto.ReplayValidationPolicy;
import io.taktx.serdes.DlqProtoMapper;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the DLQ client protobuf serdes — verifies round-trip serialisation of {@link
 * DlqEnvelope}, {@link DlqReplayCommand}, and {@link DlqReplayResult}.
 */
class DlqClientSerdesTest {

  // ── DlqEnvelopeDeserializer — client-side round-trip ─────────────────────────

  @Test
  void deserializeEnvelope_roundTrip() throws Exception {
    DlqEnvelope original = new DlqEnvelope();
    original.setSourceTopic("process-instance");
    original.setSourcePartition(0);
    original.setSourceOffset(42L);
    original.setReasonCode(DlqReasonCode.PAYLOAD_DESERIALIZATION_ERROR);
    original.setSeverity(DlqSeverity.MEDIUM);
    original.setRejectionTimestampMs(1_714_550_000_000L);
    original.setEngineInstanceId("engine-1");
    original.setLineage(
        DlqLineageDTO.builder().sourceTopic("process-instance").sourcePartition(0).build());

    byte[] proto = DlqProtoMapper.toProto(original).toByteArray();

    DlqEnvelopeDeserializer deser = new DlqEnvelopeDeserializer();
    DlqEnvelope result = deser.deserialize("dlq", proto);

    assertThat(result.getSourceTopic()).isEqualTo("process-instance");
    assertThat(result.getSourcePartition()).isZero();
    assertThat(result.getSourceOffset()).isEqualTo(42L);
    assertThat(result.getReasonCode()).isEqualTo(DlqReasonCode.PAYLOAD_DESERIALIZATION_ERROR);
    assertThat(result.getSeverity()).isEqualTo(DlqSeverity.MEDIUM);
  }

  @Test
  void deserializeEnvelope_nullReturnsNull() {
    DlqEnvelopeDeserializer deser = new DlqEnvelopeDeserializer();
    assertThat(deser.deserialize("dlq", null)).isNull();
  }

  @Test
  void deserializeEnvelope_invalidBytesThrows() {
    DlqEnvelopeDeserializer deser = new DlqEnvelopeDeserializer();
    byte[] bytes = "NOT_PROTO".getBytes(StandardCharsets.UTF_8);
    assertThatThrownBy(() -> deser.deserialize("dlq", bytes))
        .isInstanceOf(IllegalStateException.class);
  }

  // ── DlqEnvelopeDeserializer — cross-boundary (engine → client) ───────────────

  @Test
  void deserializeEnvelope_engineProtoBytes_decodedByClient() throws Exception {
    DlqEnvelope original = new DlqEnvelope();
    original.setSourceTopic("process-instance");
    original.setSourcePartition(2);
    original.setSourceOffset(99L);
    original.setReasonCode(DlqReasonCode.PROCESSOR_EXCEPTION);
    original.setSeverity(DlqSeverity.HIGH);
    original.setRejectionTimestampMs(1_714_600_000_000L);
    original.setEngineInstanceId("tenant.ns@localhost:8080");

    byte[] engineBytes = DlqProtoMapper.toProto(original).toByteArray();

    DlqEnvelopeDeserializer deser = new DlqEnvelopeDeserializer();
    DlqEnvelope result = deser.deserialize("dlq", engineBytes);

    assertThat(result.getSourceTopic()).isEqualTo("process-instance");
    assertThat(result.getSourcePartition()).isEqualTo(2);
    assertThat(result.getSourceOffset()).isEqualTo(99L);
    assertThat(result.getReasonCode()).isEqualTo(DlqReasonCode.PROCESSOR_EXCEPTION);
    assertThat(result.getSeverity()).isEqualTo(DlqSeverity.HIGH);
    assertThat(result.getEngineInstanceId()).isEqualTo("tenant.ns@localhost:8080");
  }

  // ── DlqReplayResultDeserializer — client-side round-trip ─────────────────────

  @Test
  void deserializeReplayResult_roundTrip() throws Exception {
    DlqReplayResult original =
        DlqReplayResult.builder()
            .dlqEntryRef("process-instance:0:42:sha256:abc")
            .operatorId("ops@example.com")
            .replayAtMs(1_714_550_000_000L)
            .status("SUCCESS")
            .outcomeText("Forwarded to target ingress topic")
            .replaySigner("engine-node-1")
            .replaySignatureKeyId("engine-key-2026")
            .compatibilityDecision("COMPATIBLE")
            .dryRun(false)
            .lineageRef("process-instance:0:42:sha256:abc")
            .correctionId("corr-uuid-1234")
            .build();

    byte[] proto = DlqProtoMapper.toProto(original).toByteArray();

    DlqReplayResultDeserializer deser = new DlqReplayResultDeserializer();
    DlqReplayResult result = deser.deserialize("dlq.replay-results", proto);

    assertThat(result.getStatus()).isEqualTo("SUCCESS");
    assertThat(result.getCorrectionId()).isEqualTo("corr-uuid-1234");
    assertThat(result.getReplaySigner()).isEqualTo("engine-node-1");
    assertThat(result.isDryRun()).isFalse();
  }

  @Test
  void deserializeReplayResult_nullReturnsNull() {
    DlqReplayResultDeserializer deser = new DlqReplayResultDeserializer();
    assertThat(deser.deserialize("dlq.replay-results", null)).isNull();
  }

  // ── DlqReplayResultDeserializer — cross-boundary (engine → client) ───────────

  @Test
  void deserializeReplayResult_engineProtoBytes_decodedByClient() throws Exception {
    DlqReplayResult original =
        DlqReplayResult.builder()
            .dlqEntryRef("process-instance:1:77:sha256:def")
            .operatorId("admin@acme.com")
            .replayAtMs(1_714_700_000_000L)
            .status("FAILED")
            .outcomeText("Schema incompatibility")
            .dryRun(true)
            .correctionId("corr-5678")
            .build();

    byte[] engineBytes = DlqProtoMapper.toProto(original).toByteArray();

    DlqReplayResultDeserializer deser = new DlqReplayResultDeserializer();
    DlqReplayResult result = deser.deserialize("dlq.replay-results", engineBytes);

    assertThat(result.getDlqEntryRef()).isEqualTo("process-instance:1:77:sha256:def");
    assertThat(result.getStatus()).isEqualTo("FAILED");
    assertThat(result.isDryRun()).isTrue();
    assertThat(result.getCorrectionId()).isEqualTo("corr-5678");
  }

  // ── DlqReplayCommand serialisation — round-trip + cross-boundary ─────────────

  @Test
  void serializeReplayCommand_roundTrip() throws Exception {
    DlqReplayCommand original =
        DlqReplayCommand.builder()
            .dlqEntryRef("process-instance:0:42:sha256:abc")
            .operatorId("ops@example.com")
            .approvedAtMs(1_714_550_000_000L)
            .correctedValueBytes("corrected".getBytes(StandardCharsets.UTF_8))
            .destinationTopic("process-instance")
            .validationPolicy(ReplayValidationPolicy.STRICT)
            .dryRun(true)
            .build();

    byte[] proto = DlqProtoMapper.toProto(original).toByteArray();
    DlqReplayCommand result =
        DlqProtoMapper.toDto(io.taktx.proto.DlqReplayCommand.parseFrom(proto));

    assertThat(result.getDlqEntryRef()).isEqualTo("process-instance:0:42:sha256:abc");
    assertThat(result.getValidationPolicy()).isEqualTo(ReplayValidationPolicy.STRICT);
    assertThat(result.isDryRun()).isTrue();
  }

  /**
   * Cross-boundary: client serialises a {@link DlqReplayCommand} to protobuf; the engine-equivalent
   * protobuf parser must be able to deserialise it (mirrors what {@code DLQ_REPLAY_COMMAND_SERDE}
   * does on the engine side).
   */
  @Test
  void serializeReplayCommand_clientProtoBytes_decodedByEngine() throws Exception {
    DlqReplayCommand original =
        DlqReplayCommand.builder()
            .dlqEntryRef("process-instance:3:10:sha256:xyz")
            .operatorId("ops@example.com")
            .approvedAtMs(1_714_550_000_000L)
            .destinationTopic("process-instance")
            .validationPolicy(ReplayValidationPolicy.OPERATOR_OVERRIDE)
            .dryRun(false)
            .build();

    byte[] clientBytes = DlqProtoMapper.toProto(original).toByteArray();

    DlqReplayCommand result =
        DlqProtoMapper.toDto(io.taktx.proto.DlqReplayCommand.parseFrom(clientBytes));

    assertThat(result.getDlqEntryRef()).isEqualTo("process-instance:3:10:sha256:xyz");
    assertThat(result.getValidationPolicy()).isEqualTo(ReplayValidationPolicy.OPERATOR_OVERRIDE);
    assertThat(result.isDryRun()).isFalse();
  }
}
