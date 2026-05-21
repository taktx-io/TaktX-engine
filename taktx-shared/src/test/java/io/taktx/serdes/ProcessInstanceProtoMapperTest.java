/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.serdes;

import static org.assertj.core.api.Assertions.assertThat;

import io.taktx.dto.ExecutionState;
import io.taktx.dto.IncidentInfoDTO;
import io.taktx.dto.InstanceScheduleKeyDTO;
import io.taktx.dto.IoVariableMappingDTO;
import io.taktx.dto.ProcessDefinitionKey;
import io.taktx.dto.ProcessInstanceDTO;
import io.taktx.dto.ScopeDTO;
import io.taktx.dto.SubscriptionsDTO;
import io.taktx.dto.TimeBucket;
import io.taktx.dto.subscriptions.MessageSubscriptionDTO;
import io.taktx.dto.subscriptions.SignalSubscriptionDTO;
import io.taktx.dto.subscriptions.SubScriptionType;
import io.taktx.dto.subscriptions.TimerSubscriptionDTO;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ProcessInstanceProtoMapperTest {

  @Test
  void processInstance_roundTripsThroughProtoMapper() {
    UUID processInstanceId = UUID.fromString("11111111-1111-1111-1111-111111111111");
    UUID parentProcessInstanceId = UUID.fromString("22222222-2222-2222-2222-222222222222");

    MessageSubscriptionDTO messageSubscription = new MessageSubscriptionDTO();
    messageSubscription.setSubScriptionType(SubScriptionType.STARTING);
    messageSubscription.setElementId("message-catch-1");
    messageSubscription.setName("payment.received");
    messageSubscription.setCorrelationKey("order-42");

    SignalSubscriptionDTO signalSubscription = new SignalSubscriptionDTO();
    signalSubscription.setSubScriptionType(SubScriptionType.CONTINUING);
    signalSubscription.setElementId("signal-catch-1");
    signalSubscription.setName("cancel-order");

    TimerSubscriptionDTO timerSubscription = new TimerSubscriptionDTO();
    timerSubscription.setSubScriptionType(SubScriptionType.CONTINUING);
    timerSubscription.setElementId("timer-boundary-1");
    timerSubscription.setScheduledKey(
        new InstanceScheduleKeyDTO(
            processInstanceId, List.of(9L, 10L), "timer-boundary-1", TimeBucket.MINUTE));

    TimerSubscriptionDTO processLevelTimerSubscription = new TimerSubscriptionDTO();
    processLevelTimerSubscription.setSubScriptionType(SubScriptionType.STARTING);
    processLevelTimerSubscription.setElementId("process-level-timer-1");
    processLevelTimerSubscription.setScheduledKey(
        new InstanceScheduleKeyDTO(
            processInstanceId, List.of(), "process-level-timer-1", TimeBucket.MINUTE));

    SubscriptionsDTO subscriptions = new SubscriptionsDTO();
    subscriptions.setInstanceSubscriptions(
        new LinkedHashMap<>(
            Map.of(
                -1L, List.of(messageSubscription, processLevelTimerSubscription),
                200L, List.of(timerSubscription, signalSubscription))));

    ScopeDTO scope =
        new ScopeDTO(
            ExecutionState.ACTIVE,
            2,
            1,
            9L,
            new LinkedHashMap<>(Map.of("gateway-a", 4L, "gateway-b", 2L)),
            subscriptions);

    ProcessInstanceDTO original =
        new ProcessInstanceDTO(
            processInstanceId,
            parentProcessInstanceId,
            scope,
            List.of(10L, 20L),
            new ProcessDefinitionKey("order-process", 4),
            true,
            new LinkedHashSet<>(
                Set.of(
                    new IoVariableMappingDTO("payload.total", "total"),
                    new IoVariableMappingDTO("payload.customerId", "customerId"))),
            new IncidentInfoDTO(
                List.of(10L, 20L, 30L),
                "incident message",
                new String[] {"line 1", "line 2"},
                "process-instance-dlq:0:12:sha256:deadbeef"),
            "order-42",
            new LinkedHashSet<>(Set.of("vip", "priority")));

    ProcessInstanceDTO decoded =
        ProcessInstanceProtoMapper.toDto(ProcessInstanceProtoMapper.toProto(original));

    assertThat(decoded).isEqualTo(original);
    TimerSubscriptionDTO decodedProcessLevelTimer =
        (TimerSubscriptionDTO)
            decoded.getScope().getSubscriptions().getInstanceSubscriptions().get(-1L).stream()
                .filter(TimerSubscriptionDTO.class::isInstance)
                .findFirst()
                .orElseThrow();
    assertThat(decodedProcessLevelTimer.getScheduledKey().getElementInstanceIdPath()).isEmpty();
  }
}
