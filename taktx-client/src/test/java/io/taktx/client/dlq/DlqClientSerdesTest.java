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
import com.fasterxml.jackson.dataformat.cbor.CBORFactory;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.taktx.dto.DlqEnvelope;
import io.taktx.dto.DlqReasonCode;
import io.taktx.dto.DlqReplayCommand;
import io.taktx.dto.DlqReplayResult;
import io.taktx.dto.DlqSeverity;
import io.taktx.dto.ReplayValidationPolicy;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the DLQ client CBOR serdes — verifies round-trip serialisation of {@link
 * DlqEnvelope}, {@link DlqReplayCommand}, and {@link DlqReplayResult}.
 *
 * <p>Cross-boundary tests use an engine-equivalent CBOR {@link ObjectMapper} (plain {@code
 * CBORFactory} + {@code JavaTimeModule}, no {@code @JsonFormat(shape = ARRAY)}) to produce bytes
 * the same way the engine's {@code ObjectMapperSerde} would, then assert the client deserializers
 * can decode them correctly.
 */
class DlqClientSerdesTest {

  private static final ObjectMapper CLIENT_MAPPER = DlqClientMapper.INSTANCE;

  /**
   * Simulates the engine-side CBOR serialisation: {@code new ObjectMapper(new CBORFactory())} with
   * {@code JavaTimeModule}, matching Quarkus's CDI-produced mapper from {@code
   * CustomObjectMapperProvider}.
   */
  private static final ObjectMapper ENGINE_CBOR =
      new ObjectMapper(new CBORFactory()).registerModule(new JavaTimeModule());

  // ── DlqEnvelopeCborDeserializer — client-side round-trip ─────────────────────

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

    byte[] cbor = CLIENT_MAPPER.writeValueAsBytes(original);

    DlqEnvelopeCborDeserializer deser = new DlqEnvelopeCborDeserializer();
    DlqEnvelope result = deser.deserialize("dlq", cbor);

    assertThat(result.getSourceTopic()).isEqualTo("process-instance");
    assertThat(result.getSourcePartition()).isZero();
    assertThat(result.getSourceOffset()).isEqualTo(42L);
    assertThat(result.getReasonCode()).isEqualTo(DlqReasonCode.CBOR_DECODE_ERROR);
    assertThat(result.getSeverity()).isEqualTo(DlqSeverity.MEDIUM);
  }

  @Test
  void deserializeEnvelope_nullReturnsNull() {
    DlqEnvelopeCborDeserializer deser = new DlqEnvelopeCborDeserializer();
    assertThat(deser.deserialize("dlq", null)).isNull();
  }

  @Test
  void deserializeEnvelope_invalidBytesThrows() {
    DlqEnvelopeCborDeserializer deser = new DlqEnvelopeCborDeserializer();
    byte[] bytes = "NOT_CBOR".getBytes(StandardCharsets.UTF_8);
    assertThatThrownBy(() -> deser.deserialize("dlq", bytes))
        .isInstanceOf(IllegalStateException.class);
  }

  // ── DlqEnvelopeCborDeserializer — cross-boundary (engine → client) ────────────

  @Test
  void deserializeEnvelope_engineCborBytes_decodedByClient() throws Exception {
    DlqEnvelope original = new DlqEnvelope();
    original.setSourceTopic("process-instance");
    original.setSourcePartition(2);
    original.setSourceOffset(99L);
    original.setReasonCode(DlqReasonCode.PROCESSOR_EXCEPTION);
    original.setSeverity(DlqSeverity.HIGH);
    original.setRejectionTimestampMs(1_714_600_000_000L);
    original.setEngineInstanceId("tenant.ns@localhost:8080");

    // Produce bytes the same way the engine's ObjectMapperSerde would.
    byte[] engineBytes = ENGINE_CBOR.writeValueAsBytes(original);

    DlqEnvelopeCborDeserializer deser = new DlqEnvelopeCborDeserializer();
    DlqEnvelope result = deser.deserialize("dlq", engineBytes);

    assertThat(result.getSourceTopic()).isEqualTo("process-instance");
    assertThat(result.getSourcePartition()).isEqualTo(2);
    assertThat(result.getSourceOffset()).isEqualTo(99L);
    assertThat(result.getReasonCode()).isEqualTo(DlqReasonCode.PROCESSOR_EXCEPTION);
    assertThat(result.getSeverity()).isEqualTo(DlqSeverity.HIGH);
    assertThat(result.getEngineInstanceId()).isEqualTo("tenant.ns@localhost:8080");
  }

  // ── DlqReplayResultCborDeserializer — client-side round-trip ─────────────────

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

    byte[] cbor = CLIENT_MAPPER.writeValueAsBytes(original);

    DlqReplayResultCborDeserializer deser = new DlqReplayResultCborDeserializer();
    DlqReplayResult result = deser.deserialize("dlq.replay-results", cbor);

    assertThat(result.getStatus()).isEqualTo("SUCCESS");
    assertThat(result.getCorrectionId()).isEqualTo("corr-uuid-1234");
    assertThat(result.getReplaySigner()).isEqualTo("engine-node-1");
    assertThat(result.isDryRun()).isFalse();
  }

  @Test
  void deserializeReplayResult_nullReturnsNull() {
    DlqReplayResultCborDeserializer deser = new DlqReplayResultCborDeserializer();
    assertThat(deser.deserialize("dlq.replay-results", null)).isNull();
  }

  // ── DlqReplayResultCborDeserializer — cross-boundary (engine → client) ───────

  @Test
  void deserializeReplayResult_engineCborBytes_decodedByClient() throws Exception {
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

    byte[] engineBytes = ENGINE_CBOR.writeValueAsBytes(original);

    DlqReplayResultCborDeserializer deser = new DlqReplayResultCborDeserializer();
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

    byte[] cbor = CLIENT_MAPPER.writeValueAsBytes(original);
    DlqReplayCommand result = CLIENT_MAPPER.readValue(cbor, DlqReplayCommand.class);

    assertThat(result.getDlqEntryRef()).isEqualTo("process-instance:0:42:sha256:abc");
    assertThat(result.getValidationPolicy()).isEqualTo(ReplayValidationPolicy.STRICT);
    assertThat(result.isDryRun()).isTrue();
  }

  /**
   * Cross-boundary: client serialises a {@link DlqReplayCommand} to CBOR; the engine-equivalent
   * CBOR mapper must be able to deserialise it (mirrors what {@code DLQ_REPLAY_COMMAND_SERDE} does
   * on the engine side).
   */
  @Test
  void serializeReplayCommand_clientCborBytes_decodedByEngine() throws Exception {
    DlqReplayCommand original =
        DlqReplayCommand.builder()
            .dlqEntryRef("process-instance:3:10:sha256:xyz")
            .operatorId("ops@example.com")
            .approvedAtMs(1_714_550_000_000L)
            .destinationTopic("process-instance")
            .validationPolicy(ReplayValidationPolicy.OPERATOR_OVERRIDE)
            .dryRun(false)
            .build();

    // Client writes CBOR — same bytes that DlqReplayCommandProducer will publish.
    byte[] clientBytes = CLIENT_MAPPER.writeValueAsBytes(original);

    // Engine reads with its own CBOR mapper — must decode without error.
    DlqReplayCommand result = ENGINE_CBOR.readValue(clientBytes, DlqReplayCommand.class);

    assertThat(result.getDlqEntryRef()).isEqualTo("process-instance:3:10:sha256:xyz");
    assertThat(result.getValidationPolicy()).isEqualTo(ReplayValidationPolicy.OPERATOR_OVERRIDE);
    assertThat(result.isDryRun()).isFalse();
  }
}
