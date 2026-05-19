/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.serdes;

import static org.assertj.core.api.Assertions.assertThat;

import io.taktx.CleanupPolicy;
import io.taktx.dto.ConfigurationEventDTO;
import io.taktx.dto.DlqCaptureStage;
import io.taktx.dto.DlqEnvelope;
import io.taktx.dto.DlqLineageDTO;
import io.taktx.dto.DlqReasonCode;
import io.taktx.dto.DlqReplayCommand;
import io.taktx.dto.DlqReplayResult;
import io.taktx.dto.DlqSeverity;
import io.taktx.dto.DmnCollectOperator;
import io.taktx.dto.DmnDecisionDTO;
import io.taktx.dto.DmnDecisionTableDTO;
import io.taktx.dto.DmnDefinitionDTO;
import io.taktx.dto.DmnDefinitionStateEnum;
import io.taktx.dto.DmnDefinitionsKey;
import io.taktx.dto.DmnHitPolicy;
import io.taktx.dto.DmnInputClauseDTO;
import io.taktx.dto.DmnOutputClauseDTO;
import io.taktx.dto.DmnRuleDTO;
import io.taktx.dto.GlobalConfigurationDTO;
import io.taktx.dto.KeyRole;
import io.taktx.dto.ParsedDmnDefinitionsDTO;
import io.taktx.dto.ReplayProtectionMode;
import io.taktx.dto.ReplayValidationPolicy;
import io.taktx.dto.SigningKeyDTO;
import io.taktx.dto.TopicMetaDTO;
import io.taktx.dto.XmlDmnDefinitionsDTO;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class Proto410MapperTest {

  @Test
  void configurationEvent_roundTripsThroughProto() {
    ConfigurationEventDTO dto =
        ConfigurationEventDTO.builder()
            .eventType(ConfigurationEventDTO.ConfigurationEventType.COMBINED_UPDATE)
            .configuration(
                GlobalConfigurationDTO.builder()
                    .signingEnabled(true)
                    .engineRequiresAuthorization(true)
                    .trustedKeyIds(List.of("worker-a", "worker-b"))
                    .replayProtectionMode(ReplayProtectionMode.STRICT)
                    .replayProtectionRetentionMs(123_456L)
                    .build())
            .timestamp(Instant.parse("2026-05-19T10:15:30Z"))
            .publishedByInstance("engine-a")
            .build();

    assertThat(ConfigurationProtoMapper.toDto(ConfigurationProtoMapper.toProto(dto)))
        .isEqualTo(dto);
  }

  @Test
  void topicMeta_roundTripsThroughProto() {
    TopicMetaDTO dto =
        new TopicMetaDTO(
            "tenant.default.external-task-trigger-billing",
            6,
            CleanupPolicy.COMPACT,
            (short) 3,
            "msg-42");

    assertThat(TopicMetaProtoMapper.toDto(TopicMetaProtoMapper.toProto(dto))).isEqualTo(dto);
  }

  @Test
  void signingKey_roundTripsThroughProtoIncludingPlatformRole() {
    SigningKeyDTO dto =
        SigningKeyDTO.builder()
            .keyId("platform-key")
            .publicKeyBase64("PUB")
            .algorithm("RSA")
            .createdAt(Instant.parse("2026-05-19T10:00:00Z"))
            .status(SigningKeyDTO.KeyStatus.TRUSTED)
            .owner("platform")
            .role(KeyRole.PLATFORM)
            .registrationSignature("sig==")
            .build();

    assertThat(SigningKeyProtoMapper.toDto(SigningKeyProtoMapper.toProto(dto))).isEqualTo(dto);
  }

  @Test
  void dlqEnvelope_roundTripsThroughProto() {
    DlqEnvelope dto =
        new DlqEnvelope(
            "process-instance-trigger",
            new byte[] {1, 2, 3},
            new byte[] {4, 5, 6},
            Map.of("tx-sig", "abc", "dlq-hint", "PROCESSOR_EXCEPTION"),
            DlqReasonCode.REPLAY_DETECTED,
            "duplicate audit id",
            DlqSeverity.CRITICAL,
            DlqCaptureStage.PROCESSOR,
            1_715_000_000_000L,
            "tenant.ns@host:8080",
            0,
            55L,
            99L,
            "sha256:abc",
            "ProcessInstanceDlqEntryDTO",
            3,
            "engine-v1",
            "fingerprint-1",
            "{\"summary\":true}",
            "{\"extra\":true}",
            DlqLineageDTO.builder()
                .sourceTopic("source-topic")
                .sourcePartition(1)
                .sourceOffset(2L)
                .sourceTimestampMs(3L)
                .sourceMessageHash("sha256:src")
                .sourceSignatureKeyId("worker-1")
                .sourceSignature("base64sig")
                .build(),
            "engine-a",
            "engine-key-1");

    assertThat(DlqProtoMapper.toDto(DlqProtoMapper.toProto(dto))).isEqualTo(dto);
  }

  @Test
  void dlqReplayCommand_roundTripsThroughProto() {
    DlqReplayCommand dto =
        DlqReplayCommand.builder()
            .dlqEntryRef("process-instance:1:2:sha256:abc")
            .operatorId("ops@example.com")
            .approvedAtMs(123L)
            .operatorNotes("repair")
            .correctedValueBytes(new byte[] {7, 8, 9})
            .correctedKeyBytes(new byte[] {1, 9})
            .correctedHeaders(Map.of("tx-sig", "resigned"))
            .destinationTopic("process-instance-trigger")
            .validationPolicy(ReplayValidationPolicy.OPERATOR_OVERRIDE)
            .lineage(DlqLineageDTO.builder().sourceTopic("dlq").sourceOffset(4L).build())
            .overrideReason("manual fix")
            .changedFields(List.of("payload.amount", "headers.tx-sig"))
            .dryRun(true)
            .expectedSchemaVersion(4)
            .build();

    assertThat(DlqProtoMapper.toDto(DlqProtoMapper.toProto(dto))).isEqualTo(dto);
  }

  @Test
  void dlqReplayResult_roundTripsThroughProto() {
    DlqReplayResult dto =
        DlqReplayResult.builder()
            .dlqEntryRef("process-instance:1:2:sha256:abc")
            .operatorId("ops@example.com")
            .replayAtMs(456L)
            .status("FAILED")
            .outcomeText("schema mismatch")
            .failureReasonCode(DlqReasonCode.TOPIC_NOT_ALLOWED)
            .replaySigner("engine-a")
            .replaySignatureKeyId("engine-key-1")
            .compatibilityDecision("INCOMPATIBLE")
            .overrideReason("still blocked")
            .dryRun(false)
            .lineageRef("process-instance:1:2:sha256:abc")
            .correctionId("corr-1")
            .build();

    assertThat(DlqProtoMapper.toDto(DlqProtoMapper.toProto(dto))).isEqualTo(dto);
  }

  @Test
  void dmnDefinition_roundTripsThroughProto() {
    ParsedDmnDefinitionsDTO parsed =
        ParsedDmnDefinitionsDTO.builder()
            .definitionsKey(new DmnDefinitionsKey("eligibility", "hash-1"))
            .name("Eligibility")
            .decisions(
                List.of(
                    new DmnDecisionDTO(
                        "decision-1",
                        "Eligibility",
                        new DmnDecisionTableDTO(
                            "table-1",
                            DmnHitPolicy.FIRST,
                            DmnCollectOperator.NONE,
                            List.of(new DmnInputClauseDTO("input-1", "Age", "age", "number")),
                            List.of(
                                new DmnOutputClauseDTO(
                                    "output-1", "Approved", "approved", "boolean")),
                            List.of(new DmnRuleDTO("rule-1", List.of(">= 18"), List.of("true")))),
                        null,
                        List.of())))
            .build();
    DmnDefinitionDTO dto = new DmnDefinitionDTO(parsed, 2, DmnDefinitionStateEnum.ACTIVE);

    assertThat(DmnDefinitionsProtoMapper.toDto(DmnDefinitionsProtoMapper.toProto(dto)))
        .isEqualTo(dto);
  }

  @Test
  void xmlDmnDefinitions_roundTripsThroughProto() {
    XmlDmnDefinitionsDTO dto = new XmlDmnDefinitionsDTO("<definitions id=\"demo\"/>");

    assertThat(DmnDefinitionsProtoMapper.toDto(DmnDefinitionsProtoMapper.toProto(dto)))
        .isEqualTo(dto);
  }
}
