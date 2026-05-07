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

import com.fasterxml.jackson.databind.ObjectMapper;
import io.taktx.dto.DlqEnvelope;
import io.taktx.dto.DlqReasonCode;
import io.taktx.dto.DlqReplayCommand;
import io.taktx.dto.DlqReplayResult;
import io.taktx.dto.DlqSeverity;
import io.taktx.dto.ReplayValidationPolicy;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the DLQ client JSON serdes — verifies round-trip serialisation of {@link
 * DlqEnvelope}, {@link DlqReplayCommand}, and {@link DlqReplayResult}.
 */
class DlqClientSerdesTest {

  private static final ObjectMapper MAPPER = DlqClientMapper.INSTANCE;

  // ── DlqEnvelopeJsonDeserializer ───────────────────────────────────────────────

  @Test
  void deserializeEnvelope_roundTrip() throws Exception {
    DlqEnvelope original = new DlqEnvelope();
    original.setSourceTopic("process-instance");
    original.setSourcePartition(0);
    original.setSourceOffset(42L);
    original.setReasonCode(DlqReasonCode.CBOR_DECODE_ERROR);
    original.setSeverity(DlqSeverity.MEDIUM);
    original.setRejectionTimestampMs(1_714_550_000_000L);
    original.setEngineInstanceId("engine-1");

    byte[] json = MAPPER.writeValueAsBytes(original);

    DlqEnvelopeJsonDeserializer deser = new DlqEnvelopeJsonDeserializer();
    DlqEnvelope result = deser.deserialize("dlq", json);

    assertThat(result.getSourceTopic()).isEqualTo("process-instance");
    assertThat(result.getSourcePartition()).isZero();
    assertThat(result.getSourceOffset()).isEqualTo(42L);
    assertThat(result.getReasonCode()).isEqualTo(DlqReasonCode.CBOR_DECODE_ERROR);
    assertThat(result.getSeverity()).isEqualTo(DlqSeverity.MEDIUM);
  }

  @Test
  void deserializeEnvelope_nullReturnsNull() {
    DlqEnvelopeJsonDeserializer deser = new DlqEnvelopeJsonDeserializer();
    assertThat(deser.deserialize("dlq", null)).isNull();
  }

  @Test
  void deserializeEnvelope_invalidJsonThrows() {
    DlqEnvelopeJsonDeserializer deser = new DlqEnvelopeJsonDeserializer();
    byte[] bytes = "NOT_JSON".getBytes(StandardCharsets.UTF_8);
    assertThatThrownBy(() -> deser.deserialize("dlq", bytes))
        .isInstanceOf(IllegalStateException.class);
  }

  // ── DlqReplayResultJsonDeserializer ───────────────────────────────────────────

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

    byte[] json = MAPPER.writeValueAsBytes(original);

    DlqReplayResultJsonDeserializer deser = new DlqReplayResultJsonDeserializer();
    DlqReplayResult result = deser.deserialize("dlq.replay-results", json);

    assertThat(result.getStatus()).isEqualTo("SUCCESS");
    assertThat(result.getCorrectionId()).isEqualTo("corr-uuid-1234");
    assertThat(result.getReplaySigner()).isEqualTo("engine-node-1");
    assertThat(result.isDryRun()).isFalse();
  }

  @Test
  void deserializeReplayResult_nullReturnsNull() {
    DlqReplayResultJsonDeserializer deser = new DlqReplayResultJsonDeserializer();
    assertThat(deser.deserialize("dlq.replay-results", null)).isNull();
  }

  // ── DlqReplayCommand serialisation (via MAPPER) ───────────────────────────────

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

    byte[] json = MAPPER.writeValueAsBytes(original);
    DlqReplayCommand result = MAPPER.readValue(json, DlqReplayCommand.class);

    assertThat(result.getDlqEntryRef()).isEqualTo("process-instance:0:42:sha256:abc");
    assertThat(result.getValidationPolicy()).isEqualTo(ReplayValidationPolicy.STRICT);
    assertThat(result.isDryRun()).isTrue();
  }
}
