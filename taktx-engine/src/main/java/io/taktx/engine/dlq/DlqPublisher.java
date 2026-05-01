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
import io.taktx.dto.ProcessDefinitionDlqEntryDTO;
import io.taktx.dto.ProcessInstanceDlqEntryDTO;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class DlqPublisher {

  public DlqEnvelope toEnvelope(DlqEntryDTO entry, long rejectionTimestampMs, String engineInstanceId) {
    String sourceTopic = sourceTopic(entry);
    byte[] valueBytes = valueBytes(entry);
    DlqReasonCode reasonCode = reasonCode(entry, valueBytes);
    Map<String, String> headers = headerSnapshot(entry);

    return new DlqEnvelope(
        sourceTopic,
        null,
        valueBytes,
        headers,
        reasonCode,
        reasonText(entry, reasonCode),
        reasonCode.getSeverity(),
        DlqCaptureStage.PROCESSOR,
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
    return "unknown";
  }

  private static byte[] valueBytes(DlqEntryDTO entry) {
    if (entry instanceof ProcessInstanceDlqEntryDTO processInstanceDlqEntry) {
      return processInstanceDlqEntry.getData();
    }
    return null;
  }

  private static Map<String, String> headerSnapshot(DlqEntryDTO entry) {
    Map<String, String> headers = new HashMap<>();
    if (entry instanceof ProcessInstanceDlqEntryDTO processInstanceDlqEntry
        && processInstanceDlqEntry.getHeaders() != null) {
      processInstanceDlqEntry
          .getHeaders()
          .forEach(
              (key, value) -> {
                if (value != null) {
                  headers.put(key, Base64.getEncoder().encodeToString(value));
                }
              });
    }
    return headers;
  }

  private static DlqReasonCode reasonCode(DlqEntryDTO entry, byte[] valueBytes) {
    if (entry instanceof ProcessInstanceDlqEntryDTO processInstanceDlqEntry
        && processInstanceDlqEntry.getTrigger() == null
        && valueBytes != null
        && valueBytes.length > 0) {
      return DlqReasonCode.CBOR_DECODE_ERROR;
    }
    return DlqReasonCode.PROCESSOR_EXCEPTION;
  }

  private static String reasonText(DlqEntryDTO entry, DlqReasonCode reasonCode) {
    if (reasonCode == DlqReasonCode.CBOR_DECODE_ERROR) {
      return "Unable to decode process-instance trigger payload";
    }
    return "Rejected by legacy DLQ path for " + entry.getClass().getSimpleName();
  }

  private static String messageHash(byte[] valueBytes) {
    if (valueBytes == null || valueBytes.length == 0) {
      return null;
    }
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(valueBytes);
      return "sha256:" + bytesToHex(hash);
    } catch (NoSuchAlgorithmException e) {
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

