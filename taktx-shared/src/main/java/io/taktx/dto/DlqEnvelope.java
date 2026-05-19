/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.dto;

import jakarta.annotation.Nullable;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DlqEnvelope {
  private String sourceTopic;
  @Nullable private byte[] keyBytes;
  @Nullable private byte[] valueBytes;
  private Map<String, String> headers;
  private DlqReasonCode reasonCode;
  private String reasonText;
  private DlqSeverity severity;
  private DlqCaptureStage captureStage;
  private long rejectionTimestampMs;
  private String engineInstanceId;

  @Nullable private Integer sourcePartition;
  @Nullable private Long sourceOffset;
  @Nullable private Long sourceTimestampMs;
  @Nullable private String sourceMessageHash;

  @Nullable private String messageType;
  @Nullable private Integer schemaVersion;
  @Nullable private String decoderVersion;
  @Nullable private String schemaFingerprint;

  @Nullable private String decodedSummaryJson;
  @Nullable private String additionalContextJson;
  @Nullable private DlqLineageDTO lineage;

  @Nullable private String replaySigner;
  @Nullable private String replaySignatureKeyId;
}
