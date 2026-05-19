/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.engine.dlq;

import io.taktx.Topics;
import io.taktx.dto.DlqCaptureStage;
import io.taktx.dto.DlqEntryDTO;
import io.taktx.dto.DlqEnvelope;
import io.taktx.dto.DlqReasonCode;
import io.taktx.dto.DmnDefinitionsDlqEntryDTO;
import io.taktx.dto.MessageEventDlqEntryDTO;
import io.taktx.dto.ProcessDefinitionDlqEntryDTO;
import io.taktx.dto.ProcessInstanceDlqEntryDTO;
import io.taktx.dto.SignalDlqEntryDTO;
import io.taktx.dto.TopicMetaDlqEntryDTO;
import io.taktx.dto.UserTaskResponseDlqEntryDTO;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

@ApplicationScoped
public class DlqPublisher {

  @Inject DlqObservabilityService observabilityService;

  /** No-arg constructor for CDI (required for {@code @ApplicationScoped}). */
  DlqPublisher() {}

  /** Testing constructor. */
  DlqPublisher(DlqObservabilityService observabilityService) {
    this.observabilityService = observabilityService;
  }

  // Header keys are centralised in DlqHeaders; kept as aliases here for readability.
  private static final String DLQ_REASON_HINT_HEADER = DlqHeaders.REASON_HINT;
  private static final String DLQ_REASON_TEXT_HEADER = DlqHeaders.REASON_TEXT;
  private static final String DLQ_CAPTURE_STAGE_HEADER = DlqHeaders.CAPTURE_STAGE;

  public DlqEnvelope toEnvelope(
      DlqEntryDTO entry, long rejectionTimestampMs, String engineInstanceId) {
    String sourceTopic = sourceTopic(entry);
    byte[] valueBytes = valueBytes(entry);
    DlqReasonCode reasonCode = reasonCode(entry, valueBytes);
    Map<String, String> headers = headerSnapshot(entry);
    DlqCaptureStage captureStage = captureStage(entry);

    DlqEnvelope envelope =
        new DlqEnvelope(
            sourceTopic,
            null,
            valueBytes,
            headers,
            reasonCode,
            reasonText(entry, reasonCode),
            reasonCode.getSeverity(),
            captureStage,
            rejectionTimestampMs,
            engineInstanceId,
            null,
            null,
            null,
            messageHash(valueBytes),
            entry.getClass().getSimpleName(),
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null);
    observabilityService.recordDlqEntry(envelope);
    return envelope;
  }

  public String recordKey(DlqEnvelope envelope) {
    return envelope.getSourceTopic();
  }

  private static String sourceTopic(DlqEntryDTO entry) {
    if (entry instanceof ProcessInstanceDlqEntryDTO) {
      return Topics.PROCESS_INSTANCE_TRIGGER_TOPIC.getTopicName();
    }
    if (entry instanceof ProcessDefinitionDlqEntryDTO) {
      return Topics.PROCESS_DEFINITIONS_TRIGGER_TOPIC.getTopicName();
    }
    if (entry instanceof MessageEventDlqEntryDTO) {
      return Topics.MESSAGE_EVENT_TOPIC.getTopicName();
    }
    if (entry instanceof SignalDlqEntryDTO) {
      return Topics.SIGNAL_TOPIC.getTopicName();
    }
    if (entry instanceof UserTaskResponseDlqEntryDTO) {
      return Topics.USER_TASK_RESPONSE_TOPIC.getTopicName();
    }
    if (entry instanceof DmnDefinitionsDlqEntryDTO) {
      return Topics.DMN_DEFINITIONS_TRIGGER_TOPIC.getTopicName();
    }
    if (entry instanceof TopicMetaDlqEntryDTO) {
      return Topics.TOPIC_META_REQUESTED_TOPIC.getTopicName();
    }
    return "unknown";
  }

  private static byte[] valueBytes(DlqEntryDTO entry) {
    if (entry instanceof ProcessInstanceDlqEntryDTO processInstanceDlqEntry) {
      return processInstanceDlqEntry.getData();
    }
    if (entry instanceof TopicMetaDlqEntryDTO topicMetaDlqEntry) {
      return topicMetaDlqEntry.getData();
    }
    return new byte[0];
  }

  private static Map<String, String> headerSnapshot(DlqEntryDTO entry) {
    Map<String, String> headers = new HashMap<>();
    Map<String, byte[]> rawHeaders = getHeadersMap(entry);
    if (rawHeaders != null) {
      rawHeaders.forEach(
          (key, value) -> {
            if (value != null) {
              headers.put(key, Base64.getEncoder().encodeToString(value));
            }
          });
    }
    return headers;
  }

  private static Map<String, byte[]> getHeadersMap(DlqEntryDTO entry) {
    return switch (entry) {
      case ProcessInstanceDlqEntryDTO pi -> pi.getHeaders();
      case ProcessDefinitionDlqEntryDTO pd -> pd.getHeaders();
      case MessageEventDlqEntryDTO me -> me.getHeaders();
      case SignalDlqEntryDTO s -> s.getHeaders();
      case UserTaskResponseDlqEntryDTO u -> u.getHeaders();
      case DmnDefinitionsDlqEntryDTO dmn -> dmn.getHeaders();
      case TopicMetaDlqEntryDTO topicMeta -> topicMeta.getHeaders();
      default -> null;
    };
  }

  private static DlqReasonCode reasonCode(DlqEntryDTO entry, byte[] valueBytes) {
    String reasonHint = headerValue(entry, DLQ_REASON_HINT_HEADER);
    if (reasonHint != null) {
      try {
        return DlqReasonCode.valueOf(reasonHint);
      } catch (IllegalArgumentException _) {
        // ignore invalid hint and use fallback inference
      }
    }

    if (entry instanceof ProcessInstanceDlqEntryDTO processInstanceDlqEntry
        && processInstanceDlqEntry.getTrigger() == null
        && valueBytes != null
        && valueBytes.length > 0) {
      return DlqReasonCode.PAYLOAD_DESERIALIZATION_ERROR;
    }
    return DlqReasonCode.PROCESSOR_EXCEPTION;
  }

  private static String reasonText(DlqEntryDTO entry, DlqReasonCode reasonCode) {
    String reasonTextHint = headerValue(entry, DLQ_REASON_TEXT_HEADER);
    if (reasonTextHint != null && !reasonTextHint.isBlank()) {
      return reasonTextHint;
    }

    if (reasonCode == DlqReasonCode.PAYLOAD_DESERIALIZATION_ERROR) {
      return "Unable to decode process-instance trigger payload";
    }
    return "Processing exception for " + entry.getClass().getSimpleName();
  }

  private static DlqCaptureStage captureStage(DlqEntryDTO entry) {
    String captureStageHint = headerValue(entry, DLQ_CAPTURE_STAGE_HEADER);
    if (captureStageHint != null) {
      try {
        return DlqCaptureStage.valueOf(captureStageHint);
      } catch (IllegalArgumentException _) {
        // ignore invalid hint and use default
      }
    }
    return DlqCaptureStage.PROCESSOR;
  }

  private static String headerValue(DlqEntryDTO entry, String key) {
    Map<String, byte[]> headers = getHeadersMap(entry);
    if (headers == null) {
      return null;
    }
    byte[] value = headers.get(key);
    if (value == null) {
      return null;
    }
    return new String(value, StandardCharsets.UTF_8);
  }

  private static String messageHash(byte[] valueBytes) {
    if (valueBytes == null || valueBytes.length == 0) {
      return null;
    }
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(valueBytes);
      return "sha256:" + bytesToHex(hash);
    } catch (NoSuchAlgorithmException _) {
      return "sha256-unavailable:" + new String(valueBytes, StandardCharsets.ISO_8859_1).hashCode();
    }
  }

  private static String bytesToHex(byte[] bytes) {
    StringBuilder sb = new StringBuilder(bytes.length * 2);
    for (byte b : bytes) {
      sb.append(String.format("%02x", b));
    }
    return sb.toString();
  }
}
