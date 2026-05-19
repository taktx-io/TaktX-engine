/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.serdes;

import static org.assertj.core.api.Assertions.assertThat;

import io.taktx.dto.ActivityInstanceDTO;
import io.taktx.dto.BoundaryEventInstanceDTO;
import io.taktx.dto.BusinessRuleTaskInstanceDTO;
import io.taktx.dto.CallActivityInstanceDTO;
import io.taktx.dto.DefinitionScheduleKeyDTO;
import io.taktx.dto.EventBasedGatewayInstanceDTO;
import io.taktx.dto.ExclusiveGatewayInstanceDTO;
import io.taktx.dto.ExecutionState;
import io.taktx.dto.FlowNodeInstanceDTO;
import io.taktx.dto.InclusiveGatewayInstanceDTO;
import io.taktx.dto.InstanceScheduleKeyDTO;
import io.taktx.dto.IntermediateCatchEventInstanceDTO;
import io.taktx.dto.IntermediateThrowEventInstanceDTO;
import io.taktx.dto.MessageEndEventInstanceDTO;
import io.taktx.dto.MessageEventKeyDTO;
import io.taktx.dto.MessageIntermediateThrowEventInstanceDTO;
import io.taktx.dto.MultiInstanceInstanceDTO;
import io.taktx.dto.ParallelGatewayInstanceDTO;
import io.taktx.dto.ProcessDefinitionKey;
import io.taktx.dto.ReceiveTaskInstanceDTO;
import io.taktx.dto.ScopeDTO;
import io.taktx.dto.ScriptTaskInstanceDTO;
import io.taktx.dto.SendTaskInstanceDTO;
import io.taktx.dto.ServiceTaskInstanceDTO;
import io.taktx.dto.StartEventInstanceDTO;
import io.taktx.dto.SubProcessInstanceDTO;
import io.taktx.dto.SubscriptionsDTO;
import io.taktx.dto.TaskInstanceDTO;
import io.taktx.dto.TimeBucket;
import io.taktx.dto.UserTaskInstanceDTO;
import io.taktx.dto.subscriptions.MessageSubscriptionDTO;
import io.taktx.dto.subscriptions.SignalSubscriptionDTO;
import io.taktx.dto.subscriptions.SubScriptionType;
import io.taktx.dto.subscriptions.TimerSubscriptionDTO;
import io.taktx.proto.FlowNodeInstanceEnvelope;
import io.taktx.variables.Variables;
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

class FlowNodeInstanceProtoMapperTest {

  @ParameterizedTest(name = "{0} round-trips through FlowNodeInstanceEnvelope")
  @MethodSource("instanceCases")
  void flowNodeInstanceFamily_roundTripsThroughProtoEnvelope(
      String name, FlowNodeInstanceDTO instance) throws Exception {
    FlowNodeInstanceEnvelope envelope = FlowNodeInstanceProtoMapper.toProto(instance);

    FlowNodeInstanceEnvelope parsed = FlowNodeInstanceEnvelope.parseFrom(envelope.toByteArray());
    FlowNodeInstanceDTO restored = FlowNodeInstanceProtoMapper.toDto(parsed);

    assertThat(restored).as(name).usingRecursiveComparison().isEqualTo(instance);
  }

  private static Stream<Arguments> instanceCases() {
    StartEventInstanceDTO startEvent = base(new StartEventInstanceDTO(), 10L, "start-event");

    BoundaryEventInstanceDTO boundaryEvent =
        base(new BoundaryEventInstanceDTO(), 11L, "boundary-event");
    boundaryEvent.setAttachedInstanceId(501L);

    TaskInstanceDTO task = activity(new TaskInstanceDTO(), 12L, "task");

    UserTaskInstanceDTO userTask = activity(new UserTaskInstanceDTO(), 13L, "user-task");

    BusinessRuleTaskInstanceDTO businessRuleTask =
        activity(new BusinessRuleTaskInstanceDTO(), 14L, "business-rule-task");

    CallActivityInstanceDTO callActivity =
        activity(new CallActivityInstanceDTO(), 15L, "call-activity");
    callActivity.setChildProcessInstanceId(UUID.fromString("11111111-1111-1111-1111-111111111111"));

    SendTaskInstanceDTO sendTask = externalTask(new SendTaskInstanceDTO(), 16L, "send-task");
    ScriptTaskInstanceDTO scriptTask =
        externalTask(new ScriptTaskInstanceDTO(), 17L, "script-task");
    MessageEndEventInstanceDTO messageEndEvent =
        externalTask(new MessageEndEventInstanceDTO(), 18L, "message-end-event");
    MessageIntermediateThrowEventInstanceDTO messageThrowEvent =
        externalTask(new MessageIntermediateThrowEventInstanceDTO(), 19L, "message-throw-event");
    ServiceTaskInstanceDTO serviceTask =
        externalTask(new ServiceTaskInstanceDTO(), 20L, "service-task");

    IntermediateCatchEventInstanceDTO catchEvent =
        base(new IntermediateCatchEventInstanceDTO(), 21L, "catch-event");

    io.taktx.dto.EndEventInstanceDTO endEvent =
        base(new io.taktx.dto.EndEventInstanceDTO(), 22L, "end-event");
    IntermediateThrowEventInstanceDTO throwEvent =
        base(new IntermediateThrowEventInstanceDTO(), 23L, "throw-event");

    ExclusiveGatewayInstanceDTO exclusiveGateway =
        gateway(new ExclusiveGatewayInstanceDTO(), 24L, "exclusive-gateway");

    InclusiveGatewayInstanceDTO inclusiveGateway =
        gateway(new InclusiveGatewayInstanceDTO(), 25L, "inclusive-gateway");
    inclusiveGateway.setTriggeredInputFlows(new LinkedHashSet<>(Set.of("in-a", "in-b")));

    ParallelGatewayInstanceDTO parallelGateway =
        gateway(new ParallelGatewayInstanceDTO(), 26L, "parallel-gateway");
    parallelGateway.setTriggeredFlows(new LinkedHashSet<>(Set.of("p-in-a", "p-in-b")));

    EventBasedGatewayInstanceDTO eventBasedGateway =
        gateway(new EventBasedGatewayInstanceDTO(), 27L, "event-based-gateway");
    eventBasedGateway.setConnectedFlowNodeInstanceIds(List.of(111L, 222L));

    ReceiveTaskInstanceDTO receiveTask =
        activity(new ReceiveTaskInstanceDTO(), 28L, "receive-task");
    receiveTask.setCorrelationKey("order-42");
    receiveTask.setMessageEventKeys(
        new LinkedHashMap<>(
            Map.of(
                new MessageEventKeyDTO("payment.received"), new LinkedHashSet<>(Set.of("order-42")),
                new MessageEventKeyDTO("payment.cancelled"),
                    new LinkedHashSet<>(Set.of("order-43", "order-44")))));

    MultiInstanceInstanceDTO multiInstance =
        activity(new MultiInstanceInstanceDTO(), 29L, "multi-instance");
    multiInstance.setScope(sampleScope());

    SubProcessInstanceDTO subProcess = activity(new SubProcessInstanceDTO(), 30L, "sub-process");
    subProcess.setScope(emptyScope());

    return Stream.of(
        Arguments.of("startEvent", startEvent),
        Arguments.of("boundaryEvent", boundaryEvent),
        Arguments.of("callActivity", callActivity),
        Arguments.of("sendTask", sendTask),
        Arguments.of("endEvent", endEvent),
        Arguments.of("scriptTask", scriptTask),
        Arguments.of("messageEndEvent", messageEndEvent),
        Arguments.of("messageIntermediateThrowEvent", messageThrowEvent),
        Arguments.of("intermediateCatchEvent", catchEvent),
        Arguments.of("eventBasedGateway", eventBasedGateway),
        Arguments.of("businessRuleTask", businessRuleTask),
        Arguments.of("multiInstance", multiInstance),
        Arguments.of("inclusiveGateway", inclusiveGateway),
        Arguments.of("parallelGateway", parallelGateway),
        Arguments.of("receiveTask", receiveTask),
        Arguments.of("subProcess", subProcess),
        Arguments.of("task", task),
        Arguments.of("userTask", userTask),
        Arguments.of("serviceTask", serviceTask),
        Arguments.of("intermediateThrowEvent", throwEvent),
        Arguments.of("exclusiveGateway", exclusiveGateway));
  }

  private static <T extends FlowNodeInstanceDTO> T base(
      T dto, long elementInstanceId, String elementId) {
    dto.setState(ExecutionState.ACTIVE);
    dto.setElementInstanceId(elementInstanceId);
    dto.setParentElementInstanceId(elementInstanceId - 1);
    dto.setElementIndex((int) (elementInstanceId % 7));
    dto.setElementId(elementId);
    dto.setPassedCnt((int) elementInstanceId);
    dto.setIncident((elementInstanceId & 1L) == 0L);
    return dto;
  }

  private static <T extends ActivityInstanceDTO> T activity(
      T dto, long elementInstanceId, String elementId) {
    base(dto, elementInstanceId, elementId);
    dto.setIteration(true);
    dto.setNextIterationId(elementInstanceId + 1000L);
    dto.setInputElement(Variables.of(Map.of("input", elementId, "count", elementInstanceId)));
    dto.setOutputElement(Variables.of(Map.of("result", true, "sequence", elementInstanceId + 1L)));
    dto.setLoopCnt((int) (elementInstanceId % 5));
    return dto;
  }

  private static <T extends io.taktx.dto.ExternalTaskInstanceDTO> T externalTask(
      T dto, long elementInstanceId, String elementId) {
    activity(dto, elementInstanceId, elementId);
    dto.setAttempt(2);
    dto.setScheduledKeys(
        List.of(
            new InstanceScheduleKeyDTO(
                UUID.fromString("22222222-2222-2222-2222-222222222222"),
                List.of(10L, 20L),
                elementId,
                TimeBucket.MINUTE),
            new DefinitionScheduleKeyDTO(
                new ProcessDefinitionKey("proc-" + elementId, 3), elementId, TimeBucket.HOURLY)));
    return dto;
  }

  private static <T extends ExclusiveGatewayInstanceDTO> T gateway(
      T dto, long elementInstanceId, String elementId) {
    base(dto, elementInstanceId, elementId);
    dto.setSelectedOutputFlows(new LinkedHashSet<>(Set.of("out-a", "out-b")));
    return dto;
  }

  private static <T extends EventBasedGatewayInstanceDTO> T gateway(
      T dto, long elementInstanceId, String elementId) {
    base(dto, elementInstanceId, elementId);
    dto.setSelectedOutputFlows(new LinkedHashSet<>(Set.of("out-a", "out-b")));
    return dto;
  }

  private static <T extends InclusiveGatewayInstanceDTO> T gateway(
      T dto, long elementInstanceId, String elementId) {
    base(dto, elementInstanceId, elementId);
    dto.setSelectedOutputFlows(new LinkedHashSet<>(Set.of("out-a", "out-b")));
    return dto;
  }

  private static <T extends ParallelGatewayInstanceDTO> T gateway(
      T dto, long elementInstanceId, String elementId) {
    base(dto, elementInstanceId, elementId);
    dto.setSelectedOutputFlows(new LinkedHashSet<>(Set.of("out-a", "out-b")));
    return dto;
  }

  private static ScopeDTO sampleScope() {
    TimerSubscriptionDTO timerSubscription = new TimerSubscriptionDTO();
    timerSubscription.setSubScriptionType(SubScriptionType.CONTINUING);
    timerSubscription.setElementId("timer-1");
    timerSubscription.setScheduledKey(
        new InstanceScheduleKeyDTO(
            UUID.fromString("33333333-3333-3333-3333-333333333333"),
            List.of(1L, 2L, 3L),
            "timer-1",
            TimeBucket.MINUTE));

    MessageSubscriptionDTO messageSubscription = new MessageSubscriptionDTO();
    messageSubscription.setSubScriptionType(SubScriptionType.STARTING);
    messageSubscription.setElementId("message-1");
    messageSubscription.setName("payment.received");
    messageSubscription.setCorrelationKey("order-42");

    SignalSubscriptionDTO signalSubscription = new SignalSubscriptionDTO();
    signalSubscription.setSubScriptionType(SubScriptionType.CONTINUING);
    signalSubscription.setElementId("signal-1");
    signalSubscription.setName("cancel-order");

    SubscriptionsDTO subscriptions = new SubscriptionsDTO();
    subscriptions.setInstanceSubscriptions(
        new LinkedHashMap<>(
            Map.of(
                -1L, List.of(messageSubscription),
                200L, List.of(timerSubscription, signalSubscription))));

    return new ScopeDTO(
        ExecutionState.ACTIVE,
        2,
        1,
        9L,
        new LinkedHashMap<>(Map.of("gateway-a", 4L, "gateway-b", 2L)),
        subscriptions);
  }

  private static ScopeDTO emptyScope() {
    return new ScopeDTO(
        ExecutionState.ACTIVE, 0, 1, 0L, new LinkedHashMap<>(), new SubscriptionsDTO());
  }
}
