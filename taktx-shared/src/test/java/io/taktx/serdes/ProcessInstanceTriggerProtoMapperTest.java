/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.serdes;

import static org.assertj.core.api.Assertions.assertThat;

import io.taktx.dto.AbortTriggerDTO;
import io.taktx.dto.CommandAuthMethod;
import io.taktx.dto.CommandTrustMetadataDTO;
import io.taktx.dto.CommandTrustVerificationResult;
import io.taktx.dto.ContinueFlowElementTriggerDTO;
import io.taktx.dto.ErrorEventSignalDTO;
import io.taktx.dto.EventSignalTriggerDTO;
import io.taktx.dto.ExternalTaskResponseResultDTO;
import io.taktx.dto.ExternalTaskResponseTriggerDTO;
import io.taktx.dto.ExternalTaskResponseType;
import io.taktx.dto.IoVariableMappingDTO;
import io.taktx.dto.ProcessDefinitionKey;
import io.taktx.dto.ProcessInstanceTriggerDTO;
import io.taktx.dto.SetVariableTriggerDTO;
import io.taktx.dto.StartCommandDTO;
import io.taktx.dto.StartFlowElementTriggerDTO;
import io.taktx.dto.UserTaskResponseResultDTO;
import io.taktx.dto.UserTaskResponseTriggerDTO;
import io.taktx.dto.UserTaskResponseType;
import io.taktx.dto.VariablesDTO;
import io.taktx.proto.ProcessInstanceTriggerEnvelope;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class ProcessInstanceTriggerProtoMapperTest {

  @ParameterizedTest(name = "{0} round-trips through ProcessInstanceTriggerEnvelope")
  @MethodSource("triggerCases")
  void triggerFamily_roundTripsThroughProtoEnvelope(String name, ProcessInstanceTriggerDTO trigger)
      throws Exception {
    ProcessInstanceTriggerEnvelope envelope = ProcessInstanceTriggerProtoMapper.toProto(trigger);

    ProcessInstanceTriggerEnvelope parsed =
        ProcessInstanceTriggerEnvelope.parseFrom(envelope.toByteArray());
    ProcessInstanceTriggerDTO restored = ProcessInstanceTriggerProtoMapper.toDto(parsed);

    assertThat(restored).as(name).usingRecursiveComparison().isEqualTo(trigger);
  }

  private static Stream<Arguments> triggerCases() {
    UUID processInstanceId = UUID.fromString("11111111-1111-1111-1111-111111111111");
    UUID parentProcessInstanceId = UUID.fromString("22222222-2222-2222-2222-222222222222");
    VariablesDTO variables = VariablesDTO.of("count", 3L, "status", "ready");
    CommandTrustMetadataDTO trustMetadata =
        CommandTrustMetadataDTO.builder()
            .authMethod(CommandAuthMethod.JWT_AND_ED25519)
            .verificationResult(CommandTrustVerificationResult.ENGINE_SIGNED)
            .trusted(null)
            .userId("alice")
            .issuer("issuer-a")
            .signerKeyId("engine-key")
            .signerOwner("engine")
            .signerAlgorithm("Ed25519")
            .build();

    StartCommandDTO startCommand =
        new StartCommandDTO(
            processInstanceId,
            parentProcessInstanceId,
            "start-event",
            null,
            new ProcessDefinitionKey("proc-a", 7),
            variables,
            true,
            Set.of(new IoVariableMappingDTO("payload.total", "total")),
            "order-42",
            Set.of("priority.high", "customer.vip"));
    startCommand.setCurrentTrustMetadata(trustMetadata);
    startCommand.setOriginTrustMetadata(trustMetadata);

    ContinueFlowElementTriggerDTO continueTrigger =
        new ContinueFlowElementTriggerDTO(
            processInstanceId, List.of(10L, 20L), "flow-1", VariablesDTO.of("approved", true));

    ExternalTaskResponseTriggerDTO externalTaskResponse =
        new ExternalTaskResponseTriggerDTO(
            processInstanceId,
            List.of(10L, 30L),
            "msg-1",
            new ExternalTaskResponseResultDTO(
                ExternalTaskResponseType.ERROR,
                null,
                "EXT-42",
                "worker failed",
                5_000L,
                new String[] {"stack-1", "stack-2"}),
            VariablesDTO.of("retries", 2L));

    StartFlowElementTriggerDTO startFlowElement =
        new StartFlowElementTriggerDTO(
            processInstanceId, List.of(99L), "service-task", VariablesDTO.of("input", "x"));

    SetVariableTriggerDTO setVariable =
        new SetVariableTriggerDTO(
            processInstanceId, List.of(44L), VariablesDTO.of("a", 1L, "b", "two"));

    AbortTriggerDTO abortTrigger = new AbortTriggerDTO(processInstanceId, List.of(77L, 88L));

    UserTaskResponseTriggerDTO userTaskResponse =
        new UserTaskResponseTriggerDTO(
            processInstanceId,
            List.of(55L, 66L),
            "msg-2",
            new UserTaskResponseResultDTO(
                UserTaskResponseType.ESCALATION, "USR-17", "need supervisor"),
            VariablesDTO.of("decision", "escalate"));

    ErrorEventSignalDTO eventSignal = new ErrorEventSignalDTO("ERR-1", "boom");
    eventSignal.setElementInstanceIdPath(List.of(9L, 8L, 7L));
    eventSignal.setVariables(VariablesDTO.of("errorContext", "subprocess"));
    EventSignalTriggerDTO eventSignalTrigger =
        new EventSignalTriggerDTO(processInstanceId, eventSignal);

    return Stream.of(
        Arguments.of("startCommand", startCommand),
        Arguments.of("continueFlow", continueTrigger),
        Arguments.of("externalTaskResponse", externalTaskResponse),
        Arguments.of("startFlowElement", startFlowElement),
        Arguments.of("setVariable", setVariable),
        Arguments.of("abort", abortTrigger),
        Arguments.of("userTaskResponse", userTaskResponse),
        Arguments.of("eventSignal", eventSignalTrigger));
  }
}



