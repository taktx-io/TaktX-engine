/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.serdes;

import io.taktx.dto.DlqCaptureStage;
import io.taktx.dto.DlqEnvelope;
import io.taktx.dto.DlqLineageDTO;
import io.taktx.dto.DlqReasonCode;
import io.taktx.dto.DlqReplayCommand;
import io.taktx.dto.DlqReplayResult;
import io.taktx.dto.DlqSeverity;
import io.taktx.dto.ReplayValidationPolicy;
import java.util.LinkedHashMap;
import java.util.List;

/** Shared DTO ↔ protobuf mapper for DLQ records. */
public final class DlqProtoMapper {

  private DlqProtoMapper() {}

  public static io.taktx.proto.DlqEnvelope toProto(DlqEnvelope dto) {
    io.taktx.proto.DlqEnvelope.Builder builder = io.taktx.proto.DlqEnvelope.newBuilder();
    if (dto == null) {
      return builder.build();
    }
    if (dto.getSourceTopic() != null) {
      builder.setSourceTopic(dto.getSourceTopic());
    }
    if (dto.getKeyBytes() != null) {
      builder.setKeyBytes(com.google.protobuf.ByteString.copyFrom(dto.getKeyBytes()));
    }
    if (dto.getValueBytes() != null) {
      builder.setValueBytes(com.google.protobuf.ByteString.copyFrom(dto.getValueBytes()));
    }
    if (dto.getHeaders() != null) {
      builder.putAllHeaders(dto.getHeaders());
    }
    if (dto.getReasonCode() != null) {
      builder.setReasonCode(toProto(dto.getReasonCode()));
    }
    if (dto.getReasonText() != null) {
      builder.setReasonText(dto.getReasonText());
    }
    if (dto.getSeverity() != null) {
      builder.setSeverity(toProto(dto.getSeverity()));
    }
    if (dto.getCaptureStage() != null) {
      builder.setCaptureStage(toProto(dto.getCaptureStage()));
    }
    builder.setRejectionTimestampMs(dto.getRejectionTimestampMs());
    if (dto.getEngineInstanceId() != null) {
      builder.setEngineInstanceId(dto.getEngineInstanceId());
    }
    if (dto.getSourcePartition() != null) {
      builder.setSourcePartition(dto.getSourcePartition());
    }
    if (dto.getSourceOffset() != null) {
      builder.setSourceOffset(dto.getSourceOffset());
    }
    if (dto.getSourceTimestampMs() != null) {
      builder.setSourceTimestampMs(dto.getSourceTimestampMs());
    }
    if (dto.getSourceMessageHash() != null) {
      builder.setSourceMessageHash(dto.getSourceMessageHash());
    }
    if (dto.getMessageType() != null) {
      builder.setMessageType(dto.getMessageType());
    }
    if (dto.getSchemaVersion() != null) {
      builder.setSchemaVersion(dto.getSchemaVersion());
    }
    if (dto.getDecoderVersion() != null) {
      builder.setDecoderVersion(dto.getDecoderVersion());
    }
    if (dto.getSchemaFingerprint() != null) {
      builder.setSchemaFingerprint(dto.getSchemaFingerprint());
    }
    if (dto.getDecodedSummaryJson() != null) {
      builder.setDecodedSummaryJson(dto.getDecodedSummaryJson());
    }
    if (dto.getAdditionalContextJson() != null) {
      builder.setAdditionalContextJson(dto.getAdditionalContextJson());
    }
    if (dto.getLineage() != null) {
      builder.setLineage(toProto(dto.getLineage()));
    }
    if (dto.getReplaySigner() != null) {
      builder.setReplaySigner(dto.getReplaySigner());
    }
    if (dto.getReplaySignatureKeyId() != null) {
      builder.setReplaySignatureKeyId(dto.getReplaySignatureKeyId());
    }
    return builder.build();
  }

  public static DlqEnvelope toDto(io.taktx.proto.DlqEnvelope message) {
    if (message == null) {
      return null;
    }
    return new DlqEnvelope(
        emptyToNull(message.getSourceTopic()),
        emptyBytesToNull(message.getKeyBytes().toByteArray()),
        emptyBytesToNull(message.getValueBytes().toByteArray()),
        new LinkedHashMap<>(message.getHeadersMap()),
        toDto(message.getReasonCode()),
        emptyToNull(message.getReasonText()),
        toDto(message.getSeverity()),
        toDto(message.getCaptureStage()),
        message.getRejectionTimestampMs(),
        emptyToNull(message.getEngineInstanceId()),
        message.hasSourcePartition() ? message.getSourcePartition() : null,
        message.hasSourceOffset() ? message.getSourceOffset() : null,
        message.hasSourceTimestampMs() ? message.getSourceTimestampMs() : null,
        emptyToNull(message.getSourceMessageHash()),
        emptyToNull(message.getMessageType()),
        message.hasSchemaVersion() ? message.getSchemaVersion() : null,
        emptyToNull(message.getDecoderVersion()),
        emptyToNull(message.getSchemaFingerprint()),
        emptyToNull(message.getDecodedSummaryJson()),
        emptyToNull(message.getAdditionalContextJson()),
        message.hasLineage() ? toDto(message.getLineage()) : null,
        emptyToNull(message.getReplaySigner()),
        emptyToNull(message.getReplaySignatureKeyId()));
  }

  public static io.taktx.proto.DlqReplayCommand toProto(DlqReplayCommand dto) {
    io.taktx.proto.DlqReplayCommand.Builder builder = io.taktx.proto.DlqReplayCommand.newBuilder();
    if (dto == null) {
      return builder.build();
    }
    if (dto.getDlqEntryRef() != null) {
      builder.setDlqEntryRef(dto.getDlqEntryRef());
    }
    if (dto.getOperatorId() != null) {
      builder.setOperatorId(dto.getOperatorId());
    }
    builder.setApprovedAtMs(dto.getApprovedAtMs());
    if (dto.getOperatorNotes() != null) {
      builder.setOperatorNotes(dto.getOperatorNotes());
    }
    if (dto.getCorrectedValueBytes() != null) {
      builder.setCorrectedValueBytes(
          com.google.protobuf.ByteString.copyFrom(dto.getCorrectedValueBytes()));
    }
    if (dto.getCorrectedKeyBytes() != null) {
      builder.setCorrectedKeyBytes(
          com.google.protobuf.ByteString.copyFrom(dto.getCorrectedKeyBytes()));
    }
    if (dto.getCorrectedHeaders() != null) {
      builder.putAllCorrectedHeaders(dto.getCorrectedHeaders());
    }
    if (dto.getDestinationTopic() != null) {
      builder.setDestinationTopic(dto.getDestinationTopic());
    }
    if (dto.getValidationPolicy() != null) {
      builder.setValidationPolicy(toProto(dto.getValidationPolicy()));
    }
    if (dto.getLineage() != null) {
      builder.setLineage(toProto(dto.getLineage()));
    }
    if (dto.getOverrideReason() != null) {
      builder.setOverrideReason(dto.getOverrideReason());
    }
    List<String> changedFields = dto.getChangedFields();
    if (changedFields != null) {
      builder.addAllChangedFields(changedFields);
    }
    builder.setDryRun(dto.isDryRun());
    if (dto.getExpectedSchemaVersion() != null) {
      builder.setExpectedSchemaVersion(dto.getExpectedSchemaVersion());
    }
    return builder.build();
  }

  public static DlqReplayCommand toDto(io.taktx.proto.DlqReplayCommand message) {
    if (message == null) {
      return null;
    }
    return DlqReplayCommand.builder()
        .dlqEntryRef(emptyToNull(message.getDlqEntryRef()))
        .operatorId(emptyToNull(message.getOperatorId()))
        .approvedAtMs(message.getApprovedAtMs())
        .operatorNotes(emptyToNull(message.getOperatorNotes()))
        .correctedValueBytes(emptyBytesToNull(message.getCorrectedValueBytes().toByteArray()))
        .correctedKeyBytes(emptyBytesToNull(message.getCorrectedKeyBytes().toByteArray()))
        .correctedHeaders(new LinkedHashMap<>(message.getCorrectedHeadersMap()))
        .destinationTopic(emptyToNull(message.getDestinationTopic()))
        .validationPolicy(toDto(message.getValidationPolicy()))
        .lineage(message.hasLineage() ? toDto(message.getLineage()) : null)
        .overrideReason(emptyToNull(message.getOverrideReason()))
        .changedFields(
            message.getChangedFieldsList().isEmpty()
                ? null
                : List.copyOf(message.getChangedFieldsList()))
        .dryRun(message.getDryRun())
        .expectedSchemaVersion(
            message.hasExpectedSchemaVersion() ? message.getExpectedSchemaVersion() : null)
        .build();
  }

  public static io.taktx.proto.DlqReplayResult toProto(DlqReplayResult dto) {
    io.taktx.proto.DlqReplayResult.Builder builder = io.taktx.proto.DlqReplayResult.newBuilder();
    if (dto == null) {
      return builder.build();
    }
    if (dto.getDlqEntryRef() != null) {
      builder.setDlqEntryRef(dto.getDlqEntryRef());
    }
    if (dto.getOperatorId() != null) {
      builder.setOperatorId(dto.getOperatorId());
    }
    builder.setReplayAtMs(dto.getReplayAtMs());
    if (dto.getStatus() != null) {
      builder.setStatus(dto.getStatus());
    }
    if (dto.getOutcomeText() != null) {
      builder.setOutcomeText(dto.getOutcomeText());
    }
    if (dto.getFailureReasonCode() != null) {
      builder.setFailureReasonCode(toProto(dto.getFailureReasonCode()));
    }
    if (dto.getReplaySigner() != null) {
      builder.setReplaySigner(dto.getReplaySigner());
    }
    if (dto.getReplaySignatureKeyId() != null) {
      builder.setReplaySignatureKeyId(dto.getReplaySignatureKeyId());
    }
    if (dto.getCompatibilityDecision() != null) {
      builder.setCompatibilityDecision(dto.getCompatibilityDecision());
    }
    if (dto.getOverrideReason() != null) {
      builder.setOverrideReason(dto.getOverrideReason());
    }
    builder.setDryRun(dto.isDryRun());
    if (dto.getLineageRef() != null) {
      builder.setLineageRef(dto.getLineageRef());
    }
    if (dto.getCorrectionId() != null) {
      builder.setCorrectionId(dto.getCorrectionId());
    }
    return builder.build();
  }

  public static DlqReplayResult toDto(io.taktx.proto.DlqReplayResult message) {
    if (message == null) {
      return null;
    }
    return DlqReplayResult.builder()
        .dlqEntryRef(emptyToNull(message.getDlqEntryRef()))
        .operatorId(emptyToNull(message.getOperatorId()))
        .replayAtMs(message.getReplayAtMs())
        .status(emptyToNull(message.getStatus()))
        .outcomeText(emptyToNull(message.getOutcomeText()))
        .failureReasonCode(toDto(message.getFailureReasonCode()))
        .replaySigner(emptyToNull(message.getReplaySigner()))
        .replaySignatureKeyId(emptyToNull(message.getReplaySignatureKeyId()))
        .compatibilityDecision(emptyToNull(message.getCompatibilityDecision()))
        .overrideReason(emptyToNull(message.getOverrideReason()))
        .dryRun(message.getDryRun())
        .lineageRef(emptyToNull(message.getLineageRef()))
        .correctionId(emptyToNull(message.getCorrectionId()))
        .build();
  }

  public static io.taktx.proto.DlqLineageMessage toProto(DlqLineageDTO dto) {
    io.taktx.proto.DlqLineageMessage.Builder builder =
        io.taktx.proto.DlqLineageMessage.newBuilder();
    if (dto == null) {
      return builder.build();
    }
    if (dto.getSourceTopic() != null) {
      builder.setSourceTopic(dto.getSourceTopic());
    }
    if (dto.getSourcePartition() != null) {
      builder.setSourcePartition(dto.getSourcePartition());
    }
    if (dto.getSourceOffset() != null) {
      builder.setSourceOffset(dto.getSourceOffset());
    }
    if (dto.getSourceTimestampMs() != null) {
      builder.setSourceTimestampMs(dto.getSourceTimestampMs());
    }
    if (dto.getSourceMessageHash() != null) {
      builder.setSourceMessageHash(dto.getSourceMessageHash());
    }
    if (dto.getSourceSignatureKeyId() != null) {
      builder.setSourceSignatureKeyId(dto.getSourceSignatureKeyId());
    }
    if (dto.getSourceSignature() != null) {
      builder.setSourceSignature(dto.getSourceSignature());
    }
    return builder.build();
  }

  public static DlqLineageDTO toDto(io.taktx.proto.DlqLineageMessage message) {
    if (message == null) {
      return null;
    }
    return DlqLineageDTO.builder()
        .sourceTopic(emptyToNull(message.getSourceTopic()))
        .sourcePartition(message.hasSourcePartition() ? message.getSourcePartition() : null)
        .sourceOffset(message.hasSourceOffset() ? message.getSourceOffset() : null)
        .sourceTimestampMs(message.hasSourceTimestampMs() ? message.getSourceTimestampMs() : null)
        .sourceMessageHash(emptyToNull(message.getSourceMessageHash()))
        .sourceSignatureKeyId(emptyToNull(message.getSourceSignatureKeyId()))
        .sourceSignature(emptyToNull(message.getSourceSignature()))
        .build();
  }

  private static io.taktx.proto.DlqCaptureStage toProto(DlqCaptureStage stage) {
    return switch (stage) {
      case DESERIALIZER -> io.taktx.proto.DlqCaptureStage.DLQ_CAPTURE_DESERIALIZER;
      case PROCESSOR -> io.taktx.proto.DlqCaptureStage.DLQ_CAPTURE_PROCESSOR;
      case ERROR_HANDLER -> io.taktx.proto.DlqCaptureStage.DLQ_CAPTURE_ERROR_HANDLER;
    };
  }

  private static DlqCaptureStage toDto(io.taktx.proto.DlqCaptureStage stage) {
    return switch (stage) {
      case DLQ_CAPTURE_DESERIALIZER -> DlqCaptureStage.DESERIALIZER;
      case DLQ_CAPTURE_ERROR_HANDLER -> DlqCaptureStage.ERROR_HANDLER;
      case DLQ_CAPTURE_STAGE_UNSPECIFIED, DLQ_CAPTURE_PROCESSOR, UNRECOGNIZED ->
          DlqCaptureStage.PROCESSOR;
    };
  }

  private static io.taktx.proto.DlqSeverity toProto(DlqSeverity severity) {
    return switch (severity) {
      case LOW -> io.taktx.proto.DlqSeverity.DLQ_SEVERITY_LOW;
      case MEDIUM -> io.taktx.proto.DlqSeverity.DLQ_SEVERITY_MEDIUM;
      case HIGH -> io.taktx.proto.DlqSeverity.DLQ_SEVERITY_HIGH;
      case CRITICAL -> io.taktx.proto.DlqSeverity.DLQ_SEVERITY_CRITICAL;
    };
  }

  private static DlqSeverity toDto(io.taktx.proto.DlqSeverity severity) {
    return switch (severity) {
      case DLQ_SEVERITY_LOW -> DlqSeverity.LOW;
      case DLQ_SEVERITY_MEDIUM -> DlqSeverity.MEDIUM;
      case DLQ_SEVERITY_HIGH -> DlqSeverity.HIGH;
      case DLQ_SEVERITY_CRITICAL -> DlqSeverity.CRITICAL;
      case DLQ_SEVERITY_UNSPECIFIED, UNRECOGNIZED -> DlqSeverity.LOW;
    };
  }

  private static io.taktx.proto.DlqReasonCode toProto(DlqReasonCode reasonCode) {
    return switch (reasonCode) {
      case CBOR_DECODE_ERROR -> io.taktx.proto.DlqReasonCode.DLQ_REASON_CBOR_DECODE_ERROR;
      case CBOR_TYPE_MISMATCH -> io.taktx.proto.DlqReasonCode.DLQ_REASON_CBOR_TYPE_MISMATCH;
      case SIGNATURE_MISSING -> io.taktx.proto.DlqReasonCode.DLQ_REASON_SIGNATURE_MISSING;
      case SIGNATURE_MALFORMED -> io.taktx.proto.DlqReasonCode.DLQ_REASON_SIGNATURE_MALFORMED;
      case SIGNATURE_KEY_UNKNOWN -> io.taktx.proto.DlqReasonCode.DLQ_REASON_SIGNATURE_KEY_UNKNOWN;
      case SIGNATURE_KEY_REVOKED -> io.taktx.proto.DlqReasonCode.DLQ_REASON_SIGNATURE_KEY_REVOKED;
      case SIGNATURE_VERIFICATION_FAILED ->
          io.taktx.proto.DlqReasonCode.DLQ_REASON_SIGNATURE_VERIFICATION_FAILED;
      case JWT_MISSING -> io.taktx.proto.DlqReasonCode.DLQ_REASON_JWT_MISSING;
      case JWT_MALFORMED -> io.taktx.proto.DlqReasonCode.DLQ_REASON_JWT_MALFORMED;
      case JWT_SIGNATURE_INVALID -> io.taktx.proto.DlqReasonCode.DLQ_REASON_JWT_SIGNATURE_INVALID;
      case AUTHORIZATION_FAILED -> io.taktx.proto.DlqReasonCode.DLQ_REASON_AUTHORIZATION_FAILED;
      case INSUFFICIENT_ROLE -> io.taktx.proto.DlqReasonCode.DLQ_REASON_INSUFFICIENT_ROLE;
      case INSUFFICIENT_SCOPE -> io.taktx.proto.DlqReasonCode.DLQ_REASON_INSUFFICIENT_SCOPE;
      case REPLAY_DETECTED -> io.taktx.proto.DlqReasonCode.DLQ_REASON_REPLAY_DETECTED;
      case PROCESSOR_EXCEPTION -> io.taktx.proto.DlqReasonCode.DLQ_REASON_PROCESSOR_EXCEPTION;
      case TOPIC_NOT_ALLOWED -> io.taktx.proto.DlqReasonCode.DLQ_REASON_TOPIC_NOT_ALLOWED;
      case UNKNOWN_REJECTION_REASON -> io.taktx.proto.DlqReasonCode.DLQ_REASON_UNKNOWN;
    };
  }

  private static DlqReasonCode toDto(io.taktx.proto.DlqReasonCode reasonCode) {
    return switch (reasonCode) {
      case DLQ_REASON_CBOR_DECODE_ERROR -> DlqReasonCode.CBOR_DECODE_ERROR;
      case DLQ_REASON_CBOR_TYPE_MISMATCH -> DlqReasonCode.CBOR_TYPE_MISMATCH;
      case DLQ_REASON_SIGNATURE_MISSING -> DlqReasonCode.SIGNATURE_MISSING;
      case DLQ_REASON_SIGNATURE_MALFORMED -> DlqReasonCode.SIGNATURE_MALFORMED;
      case DLQ_REASON_SIGNATURE_KEY_UNKNOWN -> DlqReasonCode.SIGNATURE_KEY_UNKNOWN;
      case DLQ_REASON_SIGNATURE_KEY_REVOKED -> DlqReasonCode.SIGNATURE_KEY_REVOKED;
      case DLQ_REASON_SIGNATURE_VERIFICATION_FAILED -> DlqReasonCode.SIGNATURE_VERIFICATION_FAILED;
      case DLQ_REASON_JWT_MISSING -> DlqReasonCode.JWT_MISSING;
      case DLQ_REASON_JWT_MALFORMED -> DlqReasonCode.JWT_MALFORMED;
      case DLQ_REASON_JWT_SIGNATURE_INVALID -> DlqReasonCode.JWT_SIGNATURE_INVALID;
      case DLQ_REASON_AUTHORIZATION_FAILED -> DlqReasonCode.AUTHORIZATION_FAILED;
      case DLQ_REASON_INSUFFICIENT_ROLE -> DlqReasonCode.INSUFFICIENT_ROLE;
      case DLQ_REASON_INSUFFICIENT_SCOPE -> DlqReasonCode.INSUFFICIENT_SCOPE;
      case DLQ_REASON_REPLAY_DETECTED -> DlqReasonCode.REPLAY_DETECTED;
      case DLQ_REASON_PROCESSOR_EXCEPTION -> DlqReasonCode.PROCESSOR_EXCEPTION;
      case DLQ_REASON_TOPIC_NOT_ALLOWED -> DlqReasonCode.TOPIC_NOT_ALLOWED;
      case DLQ_REASON_UNSPECIFIED, DLQ_REASON_UNKNOWN, UNRECOGNIZED ->
          DlqReasonCode.UNKNOWN_REJECTION_REASON;
    };
  }

  private static io.taktx.proto.ReplayValidationPolicy toProto(ReplayValidationPolicy policy) {
    return switch (policy) {
      case OPERATOR_OVERRIDE ->
          io.taktx.proto.ReplayValidationPolicy.REPLAY_VALIDATION_OPERATOR_OVERRIDE;
      case STRICT -> io.taktx.proto.ReplayValidationPolicy.REPLAY_VALIDATION_STRICT;
    };
  }

  private static ReplayValidationPolicy toDto(io.taktx.proto.ReplayValidationPolicy policy) {
    return switch (policy) {
      case REPLAY_VALIDATION_OPERATOR_OVERRIDE -> ReplayValidationPolicy.OPERATOR_OVERRIDE;
      case REPLAY_VALIDATION_STRICT, UNRECOGNIZED -> ReplayValidationPolicy.STRICT;
    };
  }

  private static String emptyToNull(String value) {
    return value == null || value.isEmpty() ? null : value;
  }

  private static byte[] emptyBytesToNull(byte[] value) {
    return value == null || value.length == 0 ? null : value;
  }
}
