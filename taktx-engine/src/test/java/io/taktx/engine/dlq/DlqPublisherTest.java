/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.engine.dlq;

import static org.assertj.core.api.Assertions.assertThat;

import io.taktx.dto.DlqEnvelope;
import io.taktx.dto.DlqReasonCode;
import io.taktx.dto.ProcessDefinitionDlqEntryDTO;
import io.taktx.dto.ProcessDefinitionKey;
import io.taktx.dto.ProcessInstanceDlqEntryDTO;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DlqPublisherTest {

  private final DlqPublisher dlqPublisher = new DlqPublisher();

  @Test
  void toEnvelope_mapsDecodeFailureToCborErrorAndPreservesPayloadDetails() {
    byte[] payload = new byte[] {1, 2, 3};
    byte[] authHeader = new byte[] {9, 8, 7};
    ProcessInstanceDlqEntryDTO entry =
        new ProcessInstanceDlqEntryDTO(
            UUID.fromString("3f31f75d-d818-4767-a4ac-f9c6f78a7f88"),
            null,
            Map.of("X-Test", authHeader),
            payload);

    DlqEnvelope envelope = dlqPublisher.toEnvelope(entry, 1_700_000_000_000L, "engine-a");

    assertThat(envelope.getSourceTopic()).isEqualTo("process-instance");
    assertThat(envelope.getReasonCode()).isEqualTo(DlqReasonCode.CBOR_DECODE_ERROR);
    assertThat(envelope.getSeverity()).isEqualTo(DlqReasonCode.CBOR_DECODE_ERROR.getSeverity());
    assertThat(envelope.getCaptureStage().name()).isEqualTo("PROCESSOR");
    assertThat(envelope.getHeaders()).containsEntry("X-Test", Base64.getEncoder().encodeToString(authHeader));
    assertThat(envelope.getValueBytes()).containsExactly(payload);
    assertThat(envelope.getSourceMessageHash()).startsWith("sha256:");
    assertThat(dlqPublisher.recordKey(envelope)).isEqualTo("process-instance");
  }

  @Test
  void toEnvelope_mapsProcessDefinitionEntryToDefinitionsTopic() {
    ProcessDefinitionDlqEntryDTO entry =
        new ProcessDefinitionDlqEntryDTO(
            new ProcessDefinitionKey("demo-process", 3),
            null);

    DlqEnvelope envelope = dlqPublisher.toEnvelope(entry, 1_700_000_100_000L, "engine-b");

    assertThat(envelope.getSourceTopic()).isEqualTo("definitions");
    assertThat(envelope.getReasonCode()).isEqualTo(DlqReasonCode.PROCESSOR_EXCEPTION);
    assertThat(envelope.getMessageType()).isEqualTo("ProcessDefinitionDlqEntryDTO");
    assertThat(envelope.getEngineInstanceId()).isEqualTo("engine-b");
    assertThat(envelope.getHeaders()).isEmpty();
  }
}
