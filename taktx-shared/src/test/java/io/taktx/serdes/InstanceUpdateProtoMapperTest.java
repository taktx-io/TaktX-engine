/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.serdes;

import static org.assertj.core.api.Assertions.assertThat;

import io.taktx.dto.CommandAuthMethod;
import io.taktx.dto.CommandTrustMetadataDTO;
import io.taktx.dto.CommandTrustVerificationResult;
import io.taktx.dto.ExecutionState;
import io.taktx.dto.FlowNodeInstanceUpdateDTO;
import io.taktx.dto.IncidentInfoDTO;
import io.taktx.dto.InstanceScheduleKeyDTO;
import io.taktx.dto.InstanceUpdateDTO;
import io.taktx.dto.ProcessDefinitionKey;
import io.taktx.dto.ProcessInstanceUpdateDTO;
import io.taktx.dto.ScopeDTO;
import io.taktx.dto.SubscriptionsDTO;
import io.taktx.dto.TaskInstanceDTO;
import io.taktx.dto.TimeBucket;
import io.taktx.dto.VariablesDTO;
import io.taktx.dto.subscriptions.MessageSubscriptionDTO;
import io.taktx.dto.subscriptions.SignalSubscriptionDTO;
import io.taktx.dto.subscriptions.SubScriptionType;
import io.taktx.dto.subscriptions.TimerSubscriptionDTO;
import io.taktx.proto.InstanceUpdateEnvelope;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class InstanceUpdateProtoMapperTest {

  @ParameterizedTest(name = "{0} round-trips through InstanceUpdateEnvelope")
  @MethodSource("updateCases")
  void updateFamily_roundTripsThroughProtoEnvelope(String name, InstanceUpdateDTO update)
      throws Exception {
    InstanceUpdateEnvelope envelope = InstanceUpdateProtoMapper.toProto(update);

    InstanceUpdateEnvelope parsed = InstanceUpdateEnvelope.parseFrom(envelope.toByteArray());
    InstanceUpdateDTO restored = InstanceUpdateProtoMapper.toDto(parsed);

    assertThat(restored).as(name).usingRecursiveComparison().isEqualTo(update);
    assertThat(restored)
        .extracting(
            InstanceUpdateDTO::getCurrentTrustMetadata, InstanceUpdateDTO::getOriginTrustMetadata)
        .containsExactly(update.getCurrentTrustMetadata(), update.getOriginTrustMetadata());
  }

  private static Stream<Arguments> updateCases() {
    CommandTrustMetadataDTO currentTrust =
        CommandTrustMetadataDTO.builder()
            .authMethod(CommandAuthMethod.JWT)
            .verificationResult(CommandTrustVerificationResult.JWT_AUTHORIZED)
            .trusted(true)
            .userId("alice")
            .issuer("issuer-a")
            .signerKeyId("engine-key-a")
            .signerOwner("engine")
            .signerAlgorithm("Ed25519")
            .build();
    CommandTrustMetadataDTO originTrust =
        CommandTrustMetadataDTO.builder()
            .authMethod(CommandAuthMethod.JWT_AND_ED25519)
            .verificationResult(CommandTrustVerificationResult.ENGINE_SIGNED)
            .trusted(true)
            .userId("bob")
            .issuer("issuer-b")
            .signerKeyId("engine-key-b")
            .signerOwner("engine")
            .signerAlgorithm("Ed25519")
            .build();

    TaskInstanceDTO task = new TaskInstanceDTO();
    task.setState(ExecutionState.ACTIVE);
    task.setElementInstanceId(101L);
    task.setParentElementInstanceId(100L);
    task.setElementIndex(7);
    task.setElementId("service-task-a");
    task.setPassedCnt(2);
    task.setIncident(false);
    task.setIteration(true);
    task.setNextIterationId(102L);
    task.setInputElement(VariablesDTO.OBJECT_MAPPER.valueToTree(Map.of("input", "value")));
    task.setOutputElement(VariablesDTO.OBJECT_MAPPER.valueToTree(Map.of("result", 42L)));
    task.setLoopCnt(3);

    FlowNodeInstanceUpdateDTO flowNodeUpdate =
        new FlowNodeInstanceUpdateDTO(
            List.of(10L, 20L, 101L),
            task,
            VariablesDTO.of("approved", true, "total", 99L),
            1_713_000_001_234L,
            "flow-in-1",
            List.of("flow-out-1", "flow-out-2"));
    flowNodeUpdate.setCurrentTrustMetadata(currentTrust);
    flowNodeUpdate.setOriginTrustMetadata(originTrust);

    TimerSubscriptionDTO timerSubscription = new TimerSubscriptionDTO();
    timerSubscription.setSubScriptionType(SubScriptionType.CONTINUING);
    timerSubscription.setElementId("boundary-timer-1");
    timerSubscription.setScheduledKey(
        new InstanceScheduleKeyDTO(
            UUID.fromString("33333333-3333-3333-3333-333333333333"),
            List.of(10L, 20L),
            "boundary-timer-1",
            TimeBucket.MINUTE));

    MessageSubscriptionDTO messageSubscription = new MessageSubscriptionDTO();
    messageSubscription.setSubScriptionType(SubScriptionType.STARTING);
    messageSubscription.setElementId("message-catch-1");
    messageSubscription.setName("payment.received");
    messageSubscription.setCorrelationKey("order-42");

    SignalSubscriptionDTO signalSubscription = new SignalSubscriptionDTO();
    signalSubscription.setSubScriptionType(SubScriptionType.CONTINUING);
    signalSubscription.setElementId("signal-catch-1");
    signalSubscription.setName("cancel-order");

    SubscriptionsDTO subscriptions = new SubscriptionsDTO();
    subscriptions.setInstanceSubscriptions(
        new LinkedHashMap<>(
            Map.of(
                -1L, List.of(messageSubscription),
                200L, List.of(timerSubscription, signalSubscription))));

    ScopeDTO scope =
        new ScopeDTO(
            ExecutionState.ACTIVE,
            2,
            1,
            9L,
            new LinkedHashMap<>(Map.of("gateway-a", 4L, "gateway-b", 2L)),
            subscriptions);

    ProcessInstanceUpdateDTO processUpdate =
        new ProcessInstanceUpdateDTO(
            UUID.fromString("22222222-2222-2222-2222-222222222222"),
            List.of(10L, 20L),
            new ProcessDefinitionKey("order-process", 4),
            new IncidentInfoDTO(
                List.of(10L, 20L, 30L),
                "incident message",
                new String[] {"line 1", "line 2"},
                "instance-update-dlq:0:12:sha256:deadbeef"),
            scope,
            VariablesDTO.of("status", "RUNNING", "attempt", 3L),
            1_713_000_000_000L,
            1_713_000_009_999L,
            "order-42",
            new LinkedHashSet<>(Set.of("vip", "priority")));
    processUpdate.setCurrentTrustMetadata(currentTrust);
    processUpdate.setOriginTrustMetadata(originTrust);

    return Stream.of(
        Arguments.of("flowNodeUpdate", flowNodeUpdate),
        Arguments.of("processUpdate", processUpdate));
  }
}
