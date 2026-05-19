/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

class DlqEnvelopeTest {

  @Test
  void allArgsConstructor_retainsCoreMetadata() {
    DlqLineageDTO lineage =
        new DlqLineageDTO(
            "process-instance", 1, 42L, 1_700_000_000_000L, "sha256:abc", "kid-1", "sig");

    DlqEnvelope envelope =
        new DlqEnvelope(
            "process-instance",
            null,
            new byte[] {1, 2, 3},
            Map.of("header-a", "value-a"),
            DlqReasonCode.PAYLOAD_DESERIALIZATION_ERROR,
            "decode failed",
            DlqSeverity.MEDIUM,
            DlqCaptureStage.DESERIALIZER,
            1_700_000_100_000L,
            "engine-1",
            1,
            42L,
            1_700_000_000_000L,
            "sha256:abc",
            "StartCommandDTO",
            3,
            "engine-1.4.2",
            "sha256:schema",
            "{\"summary\":true}",
            "{\"context\":true}",
            lineage,
            "engine-1",
            "kid-1");

    assertThat(envelope.getSourceTopic()).isEqualTo("process-instance");
    assertThat(envelope.getReasonCode()).isEqualTo(DlqReasonCode.PAYLOAD_DESERIALIZATION_ERROR);
    assertThat(envelope.getCaptureStage()).isEqualTo(DlqCaptureStage.DESERIALIZER);
    assertThat(envelope.getLineage()).isEqualTo(lineage);
    assertThat(envelope.getReplaySigner()).isEqualTo("engine-1");
    assertThat(envelope.getReplaySignatureKeyId()).isEqualTo("kid-1");
  }
}
