/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.xml;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.taktx.bpmn.TBoundaryEvent;
import io.taktx.bpmn.TBusinessRuleTask;
import io.taktx.bpmn.TEndEvent;
import io.taktx.bpmn.TEventBasedGateway;
import io.taktx.bpmn.TExclusiveGateway;
import io.taktx.bpmn.TExpression;
import io.taktx.bpmn.TIntermediateCatchEvent;
import io.taktx.bpmn.TIntermediateThrowEvent;
import io.taktx.bpmn.TManualTask;
import io.taktx.bpmn.TParallelGateway;
import io.taktx.bpmn.TReceiveTask;
import io.taktx.bpmn.TScriptTask;
import io.taktx.bpmn.TSendTask;
import io.taktx.bpmn.TSequenceFlow;
import io.taktx.bpmn.TServiceTask;
import io.taktx.bpmn.TStartEvent;
import io.taktx.bpmn.TSubProcess;
import io.taktx.bpmn.TTask;
import io.taktx.bpmn.TUserTask;
import io.taktx.dto.BoundaryEventDTO;
import io.taktx.dto.CatchEventDTO;
import io.taktx.dto.EndEventDTO;
import io.taktx.dto.EventBasedGatewayDTO;
import io.taktx.dto.EventDefinitionDTO;
import io.taktx.dto.ExclusiveGatewayDTO;
import io.taktx.dto.FlowConditionDTO;
import io.taktx.dto.FlowElementDTO;
import io.taktx.dto.InclusiveGatewayDTO;
import io.taktx.dto.InputOutputMappingDTO;
import io.taktx.dto.IntermediateCatchEventDTO;
import io.taktx.dto.IntermediateThrowEventDTO;
import io.taktx.dto.LoopCharacteristicsDTO;
import io.taktx.dto.MessageEndEventDTO;
import io.taktx.dto.MessageEventDefinitionDTO;
import io.taktx.dto.MessageIntermediateThrowEventDTO;
import io.taktx.dto.ParallelGatewayDTO;
import io.taktx.dto.SequenceFlowDTO;
import io.taktx.dto.StartEventDTO;
import io.taktx.dto.SubProcessDTO;
import io.taktx.dto.TaskDTO;
import io.taktx.dto.TimerEventDefinitionDTO;
import jakarta.xml.bind.JAXBElement;
import java.util.Set;
import javax.xml.namespace.QName;
import org.junit.jupiter.api.Test;

class GenericFlowElementMapperTest {

  private static final InputOutputMappingDTO IO_MAPPING =
      new InputOutputMappingDTO(Set.of(), Set.of());
  private static final LoopCharacteristicsDTO LOOP = LoopCharacteristicsDTO.NONE;

  @Test
  void mapSequenceFlow_mapsSourceTargetAndCondition() {
    GenericFlowElementMapper mapper = new GenericFlowElementMapper(mock(BpmnMapperFactory.class));
    TTask source = task("source-task", "Source");
    TTask target = task("target-task", "Target");
    TSequenceFlow flow = new TSequenceFlow();
    flow.setId("flow-1");
    flow.setName("Flow 1");
    flow.setSourceRef(source);
    flow.setTargetRef(target);
    flow.setConditionExpression(approvedExpression());

    SequenceFlowDTO result = (SequenceFlowDTO) mapper.map(flow, "parent-process");

    assertThat(result.getId()).isEqualTo("flow-1");
    assertThat(result.getParentId()).isEqualTo("parent-process");
    assertThat(result.getSource()).isEqualTo("source-task");
    assertThat(result.getTarget()).isEqualTo("target-task");
    assertThat(result.getCondition()).isEqualTo(new FlowConditionDTO("=approved && total > 5"));
  }

  @Test
  void mapGateways_mapsAllGatewayVariants() {
    GenericFlowElementMapper mapper = new GenericFlowElementMapper(mock(BpmnMapperFactory.class));

    TParallelGateway parallelGateway = parallelGateway();
    ParallelGatewayDTO parallel = (ParallelGatewayDTO) mapper.map(parallelGateway, "parent");
    assertThat(parallel.getId()).isEqualTo("parallel-1");
    assertThat(parallel.getIncoming()).containsExactlyInAnyOrder("in-1");
    assertThat(parallel.getOutgoing()).containsExactlyInAnyOrder("out-1");

    TExclusiveGateway exclusiveGateway = exclusiveGateway();
    ExclusiveGatewayDTO exclusive = (ExclusiveGatewayDTO) mapper.map(exclusiveGateway, "parent");
    assertThat(exclusive.getDefaultFlow()).isEqualTo("default-flow-exclusive-1");

    io.taktx.bpmn.TInclusiveGateway inclusiveGateway = inclusiveGateway();
    InclusiveGatewayDTO inclusive = (InclusiveGatewayDTO) mapper.map(inclusiveGateway, "parent");
    assertThat(inclusive.getDefaultFlow()).isEqualTo("default-flow-inclusive-1");

    TEventBasedGateway eventBasedGateway = new TEventBasedGateway();
    eventBasedGateway.setId("event-gateway-1");
    eventBasedGateway.getIncoming().add(qname("in-1"));
    eventBasedGateway.getOutgoing().add(qname("out-1"));
    EventBasedGatewayDTO eventBased =
        (EventBasedGatewayDTO) mapper.map(eventBasedGateway, "parent");
    assertThat(eventBased.getId()).isEqualTo("event-gateway-1");
    assertThat(eventBased.getIncoming()).containsExactlyInAnyOrder("in-1");
    assertThat(eventBased.getOutgoing()).containsExactlyInAnyOrder("out-1");
  }

  @Test
  void mapCatchEvents_mapsStartBoundaryAndIntermediateCatch() {
    BpmnMapperFactory factory = mock(BpmnMapperFactory.class);
    GenericFlowElementMapper mapper = new GenericFlowElementMapper(factory);
    IoMappingMapper ioMappingMapper = mock(IoMappingMapper.class);
    EventDefinitionMapper eventDefinitionMapper = mock(EventDefinitionMapper.class);
    Set<EventDefinitionDTO> eventDefinitions =
        Set.of(new TimerEventDefinitionDTO("timer-1", "parent", "", "PT5M", ""));
    when(factory.getIoMappingMapper()).thenReturn(ioMappingMapper);
    when(factory.createEventDefinitionMapper()).thenReturn(eventDefinitionMapper);
    when(ioMappingMapper.map(any(io.taktx.bpmn.TCatchEvent.class))).thenReturn(IO_MAPPING);
    when(eventDefinitionMapper.map(anyList(), eq("parent"))).thenReturn(eventDefinitions);

    TStartEvent startEvent = new TStartEvent();
    startEvent.setId("start-1");
    startEvent.setName("Start");
    startEvent.getOutgoing().add(qname("flow-out"));
    startEvent
        .getEventDefinition()
        .add(
            eventDefinition(
                new io.taktx.bpmn.TTimerEventDefinition(),
                io.taktx.bpmn.TTimerEventDefinition.class));
    StartEventDTO start = (StartEventDTO) mapper.map(startEvent, "parent");
    assertThat(start.getEventDefinitions()).isEqualTo(eventDefinitions);

    TBoundaryEvent boundaryEvent = new TBoundaryEvent();
    boundaryEvent.setId("boundary-1");
    boundaryEvent.setName("Boundary");
    boundaryEvent.setAttachedToRef(new QName("urn:test", "task-1"));
    boundaryEvent.setCancelActivity(Boolean.FALSE);
    boundaryEvent.getIncoming().add(qname("flow-in"));
    boundaryEvent.getOutgoing().add(qname("flow-out"));
    boundaryEvent
        .getEventDefinition()
        .add(
            eventDefinition(
                new io.taktx.bpmn.TTimerEventDefinition(),
                io.taktx.bpmn.TTimerEventDefinition.class));
    CatchEventDTO boundary = (CatchEventDTO) mapper.map(boundaryEvent, "parent");
    assertThat(boundary).isInstanceOf(BoundaryEventDTO.class);
    assertThat(((BoundaryEventDTO) boundary).isCancelActivity()).isFalse();

    TIntermediateCatchEvent intermediateCatchEvent = new TIntermediateCatchEvent();
    intermediateCatchEvent.setId("catch-1");
    intermediateCatchEvent.setName("Catch");
    intermediateCatchEvent.getIncoming().add(qname("flow-in"));
    intermediateCatchEvent.getOutgoing().add(qname("flow-out"));
    intermediateCatchEvent
        .getEventDefinition()
        .add(
            eventDefinition(
                new io.taktx.bpmn.TTimerEventDefinition(),
                io.taktx.bpmn.TTimerEventDefinition.class));
    CatchEventDTO intermediate = (CatchEventDTO) mapper.map(intermediateCatchEvent, "parent");
    assertThat(intermediate).isInstanceOf(IntermediateCatchEventDTO.class);
  }

  @Test
  void mapThrowEvents_handlesPlainAndMessageThrowEvents() {
    BpmnMapperFactory factory = mock(BpmnMapperFactory.class);
    GenericFlowElementMapper mapper = new GenericFlowElementMapper(factory);
    IoMappingMapper ioMappingMapper = mock(IoMappingMapper.class);
    EventDefinitionMapper eventDefinitionMapper = mock(EventDefinitionMapper.class);
    MessageEndEventMapper messageEndEventMapper = mock(MessageEndEventMapper.class);
    MessageIntermediateThrowEventMapper messageIntermediateThrowEventMapper =
        mock(MessageIntermediateThrowEventMapper.class);
    MessageEndEventDTO expectedEnd = mock(MessageEndEventDTO.class);
    MessageIntermediateThrowEventDTO expectedIntermediate =
        mock(MessageIntermediateThrowEventDTO.class);
    when(factory.getIoMappingMapper()).thenReturn(ioMappingMapper);
    when(factory.createEventDefinitionMapper()).thenReturn(eventDefinitionMapper);
    when(factory.createMessageEndEventMapper()).thenReturn(messageEndEventMapper);
    when(factory.createMessageIntermediateThrowEventMapper())
        .thenReturn(messageIntermediateThrowEventMapper);
    when(ioMappingMapper.map(any(io.taktx.bpmn.TThrowEvent.class))).thenReturn(IO_MAPPING);
    when(eventDefinitionMapper.map(anyList(), eq("parent")))
        .thenReturn(Set.of(new MessageEventDefinitionDTO("message-def", "order-message")))
        .thenReturn(Set.of(new MessageEventDefinitionDTO("message-def-2", "notify")))
        .thenReturn(Set.of(new TimerEventDefinitionDTO("timer-def", "parent", "", "PT1M", "")))
        .thenReturn(Set.of(new TimerEventDefinitionDTO("timer-def-2", "parent", "", "PT2M", "")));
    when(messageEndEventMapper.map(any(TEndEvent.class), eq("parent"), same(IO_MAPPING)))
        .thenReturn(expectedEnd);
    when(messageIntermediateThrowEventMapper.map(
            any(TIntermediateThrowEvent.class), eq("parent"), same(IO_MAPPING)))
        .thenReturn(expectedIntermediate);

    TEndEvent messageEndEvent = new TEndEvent();
    messageEndEvent.setId("end-message");
    messageEndEvent
        .getEventDefinition()
        .add(
            eventDefinition(
                new io.taktx.bpmn.TMessageEventDefinition(),
                io.taktx.bpmn.TMessageEventDefinition.class));
    assertThat(mapper.map(messageEndEvent, "parent")).isSameAs(expectedEnd);

    TIntermediateThrowEvent messageIntermediate = new TIntermediateThrowEvent();
    messageIntermediate.setId("throw-message");
    messageIntermediate
        .getEventDefinition()
        .add(
            eventDefinition(
                new io.taktx.bpmn.TMessageEventDefinition(),
                io.taktx.bpmn.TMessageEventDefinition.class));
    assertThat(mapper.map(messageIntermediate, "parent")).isSameAs(expectedIntermediate);

    TEndEvent plainEndEvent = new TEndEvent();
    plainEndEvent.setId("end-plain");
    plainEndEvent.setName("End");
    plainEndEvent.getIncoming().add(qname("flow-in"));
    plainEndEvent
        .getEventDefinition()
        .add(
            eventDefinition(
                new io.taktx.bpmn.TTimerEventDefinition(),
                io.taktx.bpmn.TTimerEventDefinition.class));
    FlowElementDTO plainEnd = mapper.map(plainEndEvent, "parent");
    assertThat(plainEnd).isInstanceOf(EndEventDTO.class);

    TIntermediateThrowEvent plainIntermediate = new TIntermediateThrowEvent();
    plainIntermediate.setId("throw-plain");
    plainIntermediate.setName("Throw");
    plainIntermediate.getIncoming().add(qname("flow-in"));
    plainIntermediate.getOutgoing().add(qname("flow-out"));
    plainIntermediate
        .getEventDefinition()
        .add(
            eventDefinition(
                new io.taktx.bpmn.TTimerEventDefinition(),
                io.taktx.bpmn.TTimerEventDefinition.class));
    FlowElementDTO plainThrow = mapper.map(plainIntermediate, "parent");
    assertThat(plainThrow).isInstanceOf(IntermediateThrowEventDTO.class);
  }

  @Test
  void mapActivities_routesAcrossManualTaskDelegatesAndSubProcess() {
    BpmnMapperFactory factory = mock(BpmnMapperFactory.class);
    GenericFlowElementMapper mapper = new GenericFlowElementMapper(factory);
    IoMappingMapper ioMappingMapper = mock(IoMappingMapper.class);
    LoopCharacteristicsMapper loopCharacteristicsMapper = mock(LoopCharacteristicsMapper.class);
    BusinessRuleTaskMapper businessRuleTaskMapper = mock(BusinessRuleTaskMapper.class);
    ServiceTaskMapper serviceTaskMapper = mock(ServiceTaskMapper.class);
    SendTaskMapper sendTaskMapper = mock(SendTaskMapper.class);
    ReceiveTaskMapper receiveTaskMapper = mock(ReceiveTaskMapper.class);
    UserTaskMapper userTaskMapper = mock(UserTaskMapper.class);
    ScriptTaskMapper scriptTaskMapper = mock(ScriptTaskMapper.class);
    CallActivityMapper callActivityMapper = mock(CallActivityMapper.class);
    FlowElementMapper childMapper = mock(FlowElementMapper.class);
    FlowElementDTO nested = mock(FlowElementDTO.class);

    when(factory.getIoMappingMapper()).thenReturn(ioMappingMapper);
    when(factory.createLoopCharacteristicsMapper()).thenReturn(loopCharacteristicsMapper);
    when(factory.createBusinessRuleTaskMapper()).thenReturn(businessRuleTaskMapper);
    when(factory.createServiceTaskMapper()).thenReturn(serviceTaskMapper);
    when(factory.createSendTaskMapper()).thenReturn(sendTaskMapper);
    when(factory.createReceiveTaskMapper()).thenReturn(receiveTaskMapper);
    when(factory.createUserTaskMapper()).thenReturn(userTaskMapper);
    when(factory.createScriptTaskMapper()).thenReturn(scriptTaskMapper);
    when(factory.createCallActivityMapper()).thenReturn(callActivityMapper);
    when(factory.createFlowElementMapper()).thenReturn(childMapper);
    when(ioMappingMapper.map(any(io.taktx.bpmn.TActivity.class))).thenReturn(IO_MAPPING);
    when(loopCharacteristicsMapper.map(any())).thenReturn(LOOP);
    when(businessRuleTaskMapper.map(
            any(TBusinessRuleTask.class), eq("parent"), same(LOOP), same(IO_MAPPING)))
        .thenReturn(mock(io.taktx.dto.BusinessRuleTaskDTO.class));
    when(serviceTaskMapper.map(any(TServiceTask.class), eq("parent"), same(LOOP), same(IO_MAPPING)))
        .thenReturn(mock(io.taktx.dto.ServiceTaskDTO.class));
    when(sendTaskMapper.map(any(TSendTask.class), eq("parent"), same(LOOP), same(IO_MAPPING)))
        .thenReturn(mock(io.taktx.dto.SendTaskDTO.class));
    when(receiveTaskMapper.map(any(TReceiveTask.class), eq("parent"), same(LOOP), same(IO_MAPPING)))
        .thenReturn(mock(io.taktx.dto.ReceiveTaskDTO.class));
    when(userTaskMapper.map(any(TUserTask.class), eq("parent"), same(LOOP), same(IO_MAPPING)))
        .thenReturn(mock(io.taktx.dto.UserTaskDTO.class));
    when(scriptTaskMapper.map(any(TScriptTask.class), eq("parent"), same(LOOP), same(IO_MAPPING)))
        .thenReturn(mock(io.taktx.dto.ScriptTaskDTO.class));
    when(callActivityMapper.map(
            any(io.taktx.bpmn.TCallActivity.class), eq("parent"), same(LOOP), same(IO_MAPPING)))
        .thenReturn(mock(io.taktx.dto.CallActivityDTO.class));
    when(childMapper.map(any(), eq("sub-1"))).thenReturn(nested);
    when(nested.getId()).thenReturn("nested-task");

    assertThat(mapper.map(new TBusinessRuleTask(), "parent"))
        .isInstanceOf(io.taktx.dto.BusinessRuleTaskDTO.class);
    assertThat(mapper.map(new TServiceTask(), "parent"))
        .isInstanceOf(io.taktx.dto.ServiceTaskDTO.class);
    assertThat(mapper.map(new TSendTask(), "parent")).isInstanceOf(io.taktx.dto.SendTaskDTO.class);
    assertThat(mapper.map(new TReceiveTask(), "parent"))
        .isInstanceOf(io.taktx.dto.ReceiveTaskDTO.class);
    assertThat(mapper.map(new TUserTask(), "parent")).isInstanceOf(io.taktx.dto.UserTaskDTO.class);
    assertThat(mapper.map(new TScriptTask(), "parent"))
        .isInstanceOf(io.taktx.dto.ScriptTaskDTO.class);
    assertThat(mapper.map(new io.taktx.bpmn.TCallActivity(), "parent"))
        .isInstanceOf(io.taktx.dto.CallActivityDTO.class);

    TManualTask manualTask = new TManualTask();
    manualTask.setId("manual-1");
    manualTask.setName("Manual");
    manualTask.getIncoming().add(qname("in-1"));
    manualTask.getOutgoing().add(qname("out-1"));
    FlowElementDTO manual = mapper.map(manualTask, "parent");
    assertThat(manual).isInstanceOf(TaskDTO.class);
    assertThat(manual.getId()).isEqualTo("manual-1");

    TTask task = task("task-1", "Task");
    FlowElementDTO basicTask = mapper.map(task, "parent");
    assertThat(basicTask).isInstanceOf(TaskDTO.class);
    assertThat(basicTask.getId()).isEqualTo("task-1");

    TSubProcess subProcess = new TSubProcess();
    subProcess.setId("sub-1");
    subProcess.setName("SubProcess");
    subProcess.getIncoming().add(qname("in-1"));
    subProcess.getOutgoing().add(qname("out-1"));
    TTask nestedTask = task("nested-task", "Nested");
    subProcess.getFlowElement().add(taskElement(nestedTask));
    FlowElementDTO subprocess = mapper.map(subProcess, "parent");
    assertThat(subprocess).isInstanceOf(SubProcessDTO.class);
    assertThat(((SubProcessDTO) subprocess).getElements().getElements()).containsKey("nested-task");
  }

  private static TTask task(String id, String name) {
    TTask task = new TTask();
    task.setId(id);
    task.setName(name);
    task.getIncoming().add(qname("in-1"));
    task.getOutgoing().add(qname("out-1"));
    return task;
  }

  private static TParallelGateway parallelGateway() {
    TParallelGateway gateway = new TParallelGateway();
    gateway.setId("parallel-1");
    gateway.getIncoming().add(qname("in-1"));
    gateway.getOutgoing().add(qname("out-1"));
    return gateway;
  }

  private static TExclusiveGateway exclusiveGateway() {
    TExclusiveGateway gateway = new TExclusiveGateway();
    gateway.setId("exclusive-1");
    gateway.getIncoming().add(qname("in-1"));
    gateway.getOutgoing().add(qname("out-1"));
    TSequenceFlow defaultFlow = new TSequenceFlow();
    defaultFlow.setId("default-flow-exclusive-1");
    gateway.setDefault(defaultFlow);
    return gateway;
  }

  private static io.taktx.bpmn.TInclusiveGateway inclusiveGateway() {
    io.taktx.bpmn.TInclusiveGateway gateway = new io.taktx.bpmn.TInclusiveGateway();
    gateway.setId("inclusive-1");
    gateway.getIncoming().add(qname("in-1"));
    gateway.getOutgoing().add(qname("out-1"));
    TSequenceFlow defaultFlow = new TSequenceFlow();
    defaultFlow.setId("default-flow-inclusive-1");
    gateway.setDefault(defaultFlow);
    return gateway;
  }

  private static TExpression approvedExpression() {
    TExpression expression = new TExpression();
    expression.getContent().add("=approved && total > 5");
    return expression;
  }

  private static QName qname(String localPart) {
    return new QName("urn:test", localPart);
  }

  private static <T extends io.taktx.bpmn.TEventDefinition> JAXBElement<T> eventDefinition(
      T value, Class<T> type) {
    return new JAXBElement<>(new QName("urn:test", "eventDefinition"), type, value);
  }

  private static JAXBElement<TTask> taskElement(TTask value) {
    return new JAXBElement<>(new QName("urn:test", "task"), TTask.class, value);
  }
}
