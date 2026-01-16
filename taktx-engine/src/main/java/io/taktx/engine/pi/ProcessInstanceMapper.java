/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
 */

package io.taktx.engine.pi;

import io.taktx.dto.BoundaryEventInstanceDTO;
import io.taktx.dto.CallActivityInstanceDTO;
import io.taktx.dto.EndEventInstanceDTO;
import io.taktx.dto.EventBasedGatewayInstanceDTO;
import io.taktx.dto.ExclusiveGatewayInstanceDTO;
import io.taktx.dto.ExecutionState;
import io.taktx.dto.FlowNodeInstanceDTO;
import io.taktx.dto.InclusiveGatewayInstanceDTO;
import io.taktx.dto.IntermediateCatchEventInstanceDTO;
import io.taktx.dto.IntermediateThrowEventInstanceDTO;
import io.taktx.dto.IoVariableMappingDTO;
import io.taktx.dto.MessageEndEventInstanceDTO;
import io.taktx.dto.MessageIntermediateThrowEventInstanceDTO;
import io.taktx.dto.MultiInstanceInstanceDTO;
import io.taktx.dto.ParallelGatewayInstanceDTO;
import io.taktx.dto.ProcessInstanceDTO;
import io.taktx.dto.ReceiveTaskInstanceDTO;
import io.taktx.dto.ScopeDTO;
import io.taktx.dto.ScriptTaskInstanceDTO;
import io.taktx.dto.SendTaskInstanceDTO;
import io.taktx.dto.ServiceTaskInstanceDTO;
import io.taktx.dto.StartEventInstanceDTO;
import io.taktx.dto.SubProcessInstanceDTO;
import io.taktx.dto.SubscriptionDTO;
import io.taktx.dto.SubscriptionsDTO;
import io.taktx.dto.TaskInstanceDTO;
import io.taktx.dto.UserTaskInstanceDTO;
import io.taktx.dto.subscriptions.BoundaryEventCatchAllErrorSubscriptionDTO;
import io.taktx.dto.subscriptions.BoundaryEventCatchAllEscalationSubscriptionDTO;
import io.taktx.dto.subscriptions.BoundaryEventErrorSubscriptionDTO;
import io.taktx.dto.subscriptions.BoundaryEventEscalationSubscriptionDTO;
import io.taktx.dto.subscriptions.EventSubProcessCatchAllErrorSubscriptionDTO;
import io.taktx.dto.subscriptions.EventSubProcessCatchAllEscalationSubscriptionDTO;
import io.taktx.dto.subscriptions.EventSubProcessErrorSubscriptionDTO;
import io.taktx.dto.subscriptions.EventSubProcessEscalationSubscriptionDTO;
import io.taktx.engine.pd.model.FlowElements;
import io.taktx.engine.pd.model.FlowNode;
import io.taktx.engine.pd.model.IoVariableMapping;
import io.taktx.engine.pd.model.SubProcess;
import io.taktx.engine.pi.model.BoundaryEventInstance;
import io.taktx.engine.pi.model.CallActivityInstance;
import io.taktx.engine.pi.model.EndEventInstance;
import io.taktx.engine.pi.model.EventBasedGatewayInstance;
import io.taktx.engine.pi.model.ExclusiveGatewayInstance;
import io.taktx.engine.pi.model.FlowNodeInstance;
import io.taktx.engine.pi.model.InclusiveGatewayInstance;
import io.taktx.engine.pi.model.IntermediateCatchEventInstance;
import io.taktx.engine.pi.model.IntermediateThrowEventInstance;
import io.taktx.engine.pi.model.MessageEndEventInstance;
import io.taktx.engine.pi.model.MessageIntermediateThrowEventInstance;
import io.taktx.engine.pi.model.MultiInstanceInstance;
import io.taktx.engine.pi.model.ParallelGatewayInstance;
import io.taktx.engine.pi.model.ProcessInstance;
import io.taktx.engine.pi.model.ReceiveTaskInstance;
import io.taktx.engine.pi.model.Scope;
import io.taktx.engine.pi.model.ScriptTaskInstance;
import io.taktx.engine.pi.model.SendTaskInstance;
import io.taktx.engine.pi.model.ServiceTaskInstance;
import io.taktx.engine.pi.model.StartEventInstance;
import io.taktx.engine.pi.model.SubProcessInstance;
import io.taktx.engine.pi.model.TaskInstance;
import io.taktx.engine.pi.model.UserTaskInstance;
import io.taktx.engine.pi.model.subscriptions.BoundaryEventCatchAllErrorSubscription;
import io.taktx.engine.pi.model.subscriptions.BoundaryEventCatchAllEscalationSubscription;
import io.taktx.engine.pi.model.subscriptions.BoundaryEventErrorSubscription;
import io.taktx.engine.pi.model.subscriptions.BoundaryEventEscalationSubscription;
import io.taktx.engine.pi.model.subscriptions.EventSubProcessCatchAllErrorSubscription;
import io.taktx.engine.pi.model.subscriptions.EventSubProcessCatchAllEscalationSubscription;
import io.taktx.engine.pi.model.subscriptions.EventSubProcessErrorSubscription;
import io.taktx.engine.pi.model.subscriptions.EventSubProcessEscalationSubscription;
import io.taktx.engine.pi.model.subscriptions.Subscription;
import io.taktx.engine.pi.model.subscriptions.Subscriptions;
import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.mapstruct.AfterMapping;
import org.mapstruct.Context;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.Named;
import org.mapstruct.ObjectFactory;
import org.mapstruct.SubclassMapping;
import org.mapstruct.TargetType;

@Mapper
public interface ProcessInstanceMapper {
  @Mapping(
      target = "flowNode",
      expression =
          "java((io.taktx.engine.pd.model.ParallelGateway)flowElements.getFlowNode(flowElements.getIndex(source.getElementIndex())).orElseThrow())")
  @Mapping(target = "parentInstance", ignore = true)
  @Mapping(target = "dirty", ignore = true)
  @Mapping(target = "state", ignore = true)
  @Mapping(target = "stateNoChange", ignore = true)
  @Mapping(target = "stateChanged", ignore = true)
  @Mapping(target = "wasWaiting", ignore = true)
  @Mapping(target = "wasNew", ignore = true)
  @Mapping(target = "counted", ignore = true)
  ParallelGatewayInstance map(
      ParallelGatewayInstanceDTO source, @Context FlowElements flowElements);

  @Mapping(
      target = "flowNode",
      expression =
          "java((io.taktx.engine.pd.model.InclusiveGateway)flowElements.getFlowNode(flowElements.getIndex(source.getElementIndex())).orElseThrow())")
  @Mapping(target = "parentInstance", ignore = true)
  @Mapping(target = "dirty", ignore = true)
  @Mapping(target = "state", ignore = true)
  @Mapping(target = "stateNoChange", ignore = true)
  @Mapping(target = "stateChanged", ignore = true)
  @Mapping(target = "wasWaiting", ignore = true)
  @Mapping(target = "wasNew", ignore = true)
  @Mapping(target = "counted", ignore = true)
  InclusiveGatewayInstance map(
      InclusiveGatewayInstanceDTO source, @Context FlowElements flowElements);

  @Mapping(
      target = "flowNode",
      expression =
          "java((io.taktx.engine.pd.model.EventBasedGateway)flowElements.getFlowNode(flowElements.getIndex(source.getElementIndex())).orElseThrow())")
  @Mapping(target = "parentInstance", ignore = true)
  @Mapping(target = "dirty", ignore = true)
  @Mapping(target = "state", ignore = true)
  @Mapping(target = "stateNoChange", ignore = true)
  @Mapping(target = "stateChanged", ignore = true)
  @Mapping(target = "wasWaiting", ignore = true)
  @Mapping(target = "wasNew", ignore = true)
  @Mapping(target = "counted", ignore = true)
  EventBasedGatewayInstance map(
      EventBasedGatewayInstanceDTO source, @Context FlowElements flowElements);

  @Mapping(
      target = "flowNode",
      expression =
          "java((io.taktx.engine.pd.model.ExclusiveGateway)flowElements.getFlowNode(flowElements.getIndex(source.getElementIndex())).orElseThrow())")
  @Mapping(target = "parentInstance", ignore = true)
  @Mapping(target = "dirty", ignore = true)
  @Mapping(target = "state", ignore = true)
  @Mapping(target = "stateNoChange", ignore = true)
  @Mapping(target = "stateChanged", ignore = true)
  @Mapping(target = "wasWaiting", ignore = true)
  @Mapping(target = "wasNew", ignore = true)
  @Mapping(target = "counted", ignore = true)
  ExclusiveGatewayInstance map(
      ExclusiveGatewayInstanceDTO source, @Context FlowElements flowElements);

  @Mapping(
      target = "flowNode",
      expression =
          "java((io.taktx.engine.pd.model.BoundaryEvent)flowElements.getFlowNode(flowElements.getIndex(source.getElementIndex())).orElseThrow())")
  @Mapping(target = "parentInstance", ignore = true)
  @Mapping(target = "dirty", ignore = true)
  @Mapping(target = "state", ignore = true)
  @Mapping(target = "stateNoChange", ignore = true)
  @Mapping(target = "stateChanged", ignore = true)
  @Mapping(target = "wasWaiting", ignore = true)
  @Mapping(target = "wasNew", ignore = true)
  @Mapping(target = "counted", ignore = true)
  BoundaryEventInstance map(BoundaryEventInstanceDTO source, @Context FlowElements flowElements);

  @Mapping(
      target = "flowNode",
      expression =
          "java((io.taktx.engine.pd.model.StartEvent)flowElements.getFlowNode(flowElements.getIndex(source.getElementIndex())).orElseThrow())")
  @Mapping(target = "parentInstance", ignore = true)
  @Mapping(target = "dirty", ignore = true)
  @Mapping(target = "state", ignore = true)
  @Mapping(target = "stateNoChange", ignore = true)
  @Mapping(target = "stateChanged", ignore = true)
  @Mapping(target = "wasWaiting", ignore = true)
  @Mapping(target = "wasNew", ignore = true)
  @Mapping(target = "counted", ignore = true)
  StartEventInstance map(StartEventInstanceDTO source, @Context FlowElements flowElements);

  @Mapping(
      target = "flowNode",
      expression =
          "java((io.taktx.engine.pd.model.IntermediateCatchEvent)flowElements.getFlowNode(flowElements.getIndex(source.getElementIndex())).orElseThrow())")
  @Mapping(target = "parentInstance", ignore = true)
  @Mapping(target = "dirty", ignore = true)
  @Mapping(target = "state", ignore = true)
  @Mapping(target = "stateNoChange", ignore = true)
  @Mapping(target = "stateChanged", ignore = true)
  @Mapping(target = "wasWaiting", ignore = true)
  @Mapping(target = "wasNew", ignore = true)
  @Mapping(target = "counted", ignore = true)
  IntermediateCatchEventInstance map(
      IntermediateCatchEventInstanceDTO source, @Context FlowElements flowElements);

  @Mapping(
      target = "flowNode",
      expression =
          "java((io.taktx.engine.pd.model.EndEvent)flowElements.getFlowNode(flowElements.getIndex(source.getElementIndex())).orElseThrow())")
  @Mapping(target = "parentInstance", ignore = true)
  @Mapping(target = "dirty", ignore = true)
  @Mapping(target = "state", ignore = true)
  @Mapping(target = "stateNoChange", ignore = true)
  @Mapping(target = "stateChanged", ignore = true)
  @Mapping(target = "wasWaiting", ignore = true)
  @Mapping(target = "wasNew", ignore = true)
  @Mapping(target = "counted", ignore = true)
  EndEventInstance map(EndEventInstanceDTO source, @Context FlowElements flowElements);

  @Mapping(
      target = "flowNode",
      expression =
          "java((io.taktx.engine.pd.model.IntermediateThrowEvent)flowElements.getFlowNode(flowElements.getIndex(source.getElementIndex())).orElseThrow())")
  @Mapping(target = "parentInstance", ignore = true)
  @Mapping(target = "dirty", ignore = true)
  @Mapping(target = "state", ignore = true)
  @Mapping(target = "stateNoChange", ignore = true)
  @Mapping(target = "stateChanged", ignore = true)
  @Mapping(target = "wasWaiting", ignore = true)
  @Mapping(target = "wasNew", ignore = true)
  @Mapping(target = "counted", ignore = true)
  IntermediateThrowEventInstance map(
      IntermediateThrowEventInstanceDTO source, @Context FlowElements flowElements);

  @Mapping(
      target = "flowNode",
      expression =
          "java((io.taktx.engine.pd.model.ServiceTask)flowElements.getFlowNode(flowElements.getIndex(source.getElementIndex())).orElseThrow())")
  @Mapping(target = "parentInstance", ignore = true)
  @Mapping(target = "dirty", ignore = true)
  @Mapping(target = "state", ignore = true)
  @Mapping(target = "stateNoChange", ignore = true)
  @Mapping(target = "stateChanged", ignore = true)
  @Mapping(target = "wasWaiting", ignore = true)
  @Mapping(target = "wasNew", ignore = true)
  @Mapping(target = "counted", ignore = true)
  ServiceTaskInstance map(ServiceTaskInstanceDTO source, @Context FlowElements flowElements);

  @Mapping(
      target = "flowNode",
      expression =
          "java((io.taktx.engine.pd.model.SendTask)flowElements.getFlowNode(flowElements.getIndex(source.getElementIndex())).orElseThrow())")
  @Mapping(target = "parentInstance", ignore = true)
  @Mapping(target = "dirty", ignore = true)
  @Mapping(target = "state", ignore = true)
  @Mapping(target = "stateNoChange", ignore = true)
  @Mapping(target = "stateChanged", ignore = true)
  @Mapping(target = "wasWaiting", ignore = true)
  @Mapping(target = "wasNew", ignore = true)
  @Mapping(target = "counted", ignore = true)
  SendTaskInstance map(SendTaskInstanceDTO source, @Context FlowElements flowElements);

  @Mapping(
      target = "flowNode",
      expression =
          "java((io.taktx.engine.pd.model.MessageEndEvent)flowElements.getFlowNode(flowElements.getIndex(source.getElementIndex())).orElseThrow())")
  @Mapping(target = "parentInstance", ignore = true)
  @Mapping(target = "dirty", ignore = true)
  @Mapping(target = "state", ignore = true)
  @Mapping(target = "stateNoChange", ignore = true)
  @Mapping(target = "stateChanged", ignore = true)
  @Mapping(target = "wasWaiting", ignore = true)
  @Mapping(target = "wasNew", ignore = true)
  @Mapping(target = "counted", ignore = true)
  MessageEndEventInstance map(
      MessageEndEventInstanceDTO source, @Context FlowElements flowElements);

  @Mapping(
      target = "flowNode",
      expression =
          "java((io.taktx.engine.pd.model.MessageIntermediateThrowEvent)flowElements.getFlowNode(flowElements.getIndex(source.getElementIndex())).orElseThrow())")
  @Mapping(target = "parentInstance", ignore = true)
  @Mapping(target = "dirty", ignore = true)
  @Mapping(target = "state", ignore = true)
  @Mapping(target = "stateNoChange", ignore = true)
  @Mapping(target = "stateChanged", ignore = true)
  @Mapping(target = "wasWaiting", ignore = true)
  @Mapping(target = "wasNew", ignore = true)
  @Mapping(target = "counted", ignore = true)
  MessageIntermediateThrowEventInstance map(
      MessageIntermediateThrowEventInstanceDTO source, @Context FlowElements flowElements);

  @Mapping(
      target = "flowNode",
      expression =
          "java((io.taktx.engine.pd.model.ScriptTask)flowElements.getFlowNode(flowElements.getIndex(source.getElementIndex())).orElseThrow())")
  @Mapping(target = "parentInstance", ignore = true)
  @Mapping(target = "dirty", ignore = true)
  @Mapping(target = "state", ignore = true)
  @Mapping(target = "stateNoChange", ignore = true)
  @Mapping(target = "stateChanged", ignore = true)
  @Mapping(target = "wasWaiting", ignore = true)
  @Mapping(target = "wasNew", ignore = true)
  @Mapping(target = "counted", ignore = true)
  ScriptTaskInstance map(ScriptTaskInstanceDTO source, @Context FlowElements flowElements);

  @Mapping(
      target = "flowNode",
      expression =
          "java((io.taktx.engine.pd.model.UserTask)flowElements.getFlowNode(flowElements.getIndex(source.getElementIndex())).orElseThrow())")
  @Mapping(target = "parentInstance", ignore = true)
  @Mapping(target = "dirty", ignore = true)
  @Mapping(target = "state", ignore = true)
  @Mapping(target = "stateNoChange", ignore = true)
  @Mapping(target = "stateChanged", ignore = true)
  @Mapping(target = "wasWaiting", ignore = true)
  @Mapping(target = "wasNew", ignore = true)
  @Mapping(target = "counted", ignore = true)
  UserTaskInstance map(UserTaskInstanceDTO source, @Context FlowElements flowElements);

  @Mapping(
      target = "flowNode",
      expression =
          "java((io.taktx.engine.pd.model.ReceiveTask)flowElements.getFlowNode(flowElements.getIndex(source.getElementIndex())).orElseThrow())")
  @Mapping(target = "parentInstance", ignore = true)
  @Mapping(target = "dirty", ignore = true)
  @Mapping(target = "state", ignore = true)
  @Mapping(target = "stateNoChange", ignore = true)
  @Mapping(target = "stateChanged", ignore = true)
  @Mapping(target = "wasWaiting", ignore = true)
  @Mapping(target = "wasNew", ignore = true)
  @Mapping(target = "counted", ignore = true)
  ReceiveTaskInstance map(ReceiveTaskInstanceDTO source, @Context FlowElements flowElements);

  @Mapping(
      target = "flowNode",
      expression =
          "java((io.taktx.engine.pd.model.SubProcess)flowElements.getFlowNode(flowElements.getIndex(source.getElementIndex())).orElseThrow())")
  @Mapping(target = "parentInstance", ignore = true)
  @Mapping(target = "dirty", ignore = true)
  @Mapping(target = "state", ignore = true)
  @Mapping(target = "stateNoChange", ignore = true)
  @Mapping(target = "stateChanged", ignore = true)
  @Mapping(target = "wasWaiting", ignore = true)
  @Mapping(target = "wasNew", ignore = true)
  @Mapping(target = "counted", ignore = true)
  SubProcessInstance map(SubProcessInstanceDTO source, @Context FlowElements flowElements);

  @Mapping(
      target = "flowNode",
      expression =
          "java((io.taktx.engine.pd.model.CallActivity)flowElements.getFlowNode(flowElements.getIndex(source.getElementIndex())).orElseThrow())")
  @Mapping(target = "parentInstance", ignore = true)
  @Mapping(target = "dirty", ignore = true)
  @Mapping(target = "state", ignore = true)
  @Mapping(target = "stateNoChange", ignore = true)
  @Mapping(target = "stateChanged", ignore = true)
  @Mapping(target = "wasWaiting", ignore = true)
  @Mapping(target = "wasNew", ignore = true)
  @Mapping(target = "counted", ignore = true)
  CallActivityInstance map(CallActivityInstanceDTO source, @Context FlowElements flowElements);

  @Mapping(
      target = "flowNode",
      expression =
          "java((io.taktx.engine.pd.model.Activity)flowElements.getFlowNode(flowElements.getIndex(source.getElementIndex())).orElseThrow())")
  @Mapping(target = "parentInstance", ignore = true)
  @Mapping(target = "dirty", ignore = true)
  @Mapping(target = "state", ignore = true)
  @Mapping(target = "stateNoChange", ignore = true)
  @Mapping(target = "stateChanged", ignore = true)
  @Mapping(target = "wasWaiting", ignore = true)
  @Mapping(target = "wasNew", ignore = true)
  @Mapping(target = "counted", ignore = true)
  MultiInstanceInstance map(MultiInstanceInstanceDTO source, @Context FlowElements flowElements);

  @Mapping(
      target = "flowNode",
      expression =
          "java((io.taktx.engine.pd.model.Task)flowElements.getFlowNode(flowElements.getIndex(source.getElementIndex())).orElseThrow())")
  @Mapping(target = "parentInstance", ignore = true)
  @Mapping(target = "dirty", ignore = true)
  @Mapping(target = "state", ignore = true)
  @Mapping(target = "stateNoChange", ignore = true)
  @Mapping(target = "stateChanged", ignore = true)
  @Mapping(target = "wasWaiting", ignore = true)
  @Mapping(target = "wasNew", ignore = true)
  @Mapping(target = "counted", ignore = true)
  TaskInstance map(TaskInstanceDTO source, @Context FlowElements flowElements);

  @SuppressWarnings("unused")
  default FlowElements getChildElements(SubProcessInstanceDTO source, FlowElements flowElements) {
    FlowNode flowNode =
        flowElements.getFlowNode(flowElements.getIndex(source.getElementIndex())).orElseThrow();
    SubProcess subProcess2 = (SubProcess) flowNode;
    return subProcess2.getElements();
  }

  @SubclassMapping(target = BoundaryEventInstance.class, source = BoundaryEventInstanceDTO.class)
  @SubclassMapping(target = StartEventInstance.class, source = StartEventInstanceDTO.class)
  @SubclassMapping(
      target = IntermediateCatchEventInstance.class,
      source = IntermediateCatchEventInstanceDTO.class)
  @SubclassMapping(target = EndEventInstance.class, source = EndEventInstanceDTO.class)
  @SubclassMapping(
      target = IntermediateThrowEventInstance.class,
      source = IntermediateThrowEventInstanceDTO.class)
  @SubclassMapping(target = ServiceTaskInstance.class, source = ServiceTaskInstanceDTO.class)
  @SubclassMapping(target = SendTaskInstance.class, source = SendTaskInstanceDTO.class)
  @SubclassMapping(
      target = MessageEndEventInstance.class,
      source = MessageEndEventInstanceDTO.class)
  @SubclassMapping(
      target = MessageIntermediateThrowEventInstance.class,
      source = MessageIntermediateThrowEventInstanceDTO.class)
  @SubclassMapping(target = UserTaskInstance.class, source = UserTaskInstanceDTO.class)
  @SubclassMapping(target = ReceiveTaskInstance.class, source = ReceiveTaskInstanceDTO.class)
  @SubclassMapping(target = ScriptTaskInstance.class, source = ScriptTaskInstanceDTO.class)
  @SubclassMapping(target = SubProcessInstance.class, source = SubProcessInstanceDTO.class)
  @SubclassMapping(target = CallActivityInstance.class, source = CallActivityInstanceDTO.class)
  @SubclassMapping(target = MultiInstanceInstance.class, source = MultiInstanceInstanceDTO.class)
  @SubclassMapping(
      target = ExclusiveGatewayInstance.class,
      source = ExclusiveGatewayInstanceDTO.class)
  @SubclassMapping(
      target = InclusiveGatewayInstance.class,
      source = InclusiveGatewayInstanceDTO.class)
  @SubclassMapping(
      target = ParallelGatewayInstance.class,
      source = ParallelGatewayInstanceDTO.class)
  @SubclassMapping(
      target = EventBasedGatewayInstance.class,
      source = EventBasedGatewayInstanceDTO.class)
  // Task state should come last
  @SubclassMapping(target = TaskInstance.class, source = TaskInstanceDTO.class)
  @Mapping(target = "parentInstance", ignore = true)
  @Mapping(target = "flowNode", ignore = true)
  @Mapping(target = "dirty", ignore = true)
  @Mapping(target = "state", ignore = true)
  @Mapping(target = "stateNoChange", ignore = true)
  @Mapping(target = "stateChanged", ignore = true)
  @Mapping(target = "wasWaiting", ignore = true)
  @Mapping(target = "wasNew", ignore = true)
  @Mapping(target = "counted", ignore = true)
  FlowNodeInstance<?> map(FlowNodeInstanceDTO source, @Context FlowElements flowElements);

  ProcessInstance map(ProcessInstanceDTO source, @Context FlowElements flowElements);

  @Mapping(
      target = "instanceSubscriptions",
      source = "instanceSubscriptions",
      qualifiedByName = "mapInstanceSubscriptions")
  Subscriptions map(SubscriptionsDTO source, @Context FlowElements flowElements);

  // Helper method to map the nested Map<Long, List<SubscriptionDTO>> to Map<Long,
  // List<Subscription>>
  @Named("mapInstanceSubscriptions")
  default Map<Long, List<Subscription>> mapInstanceSubscriptions(
      Map<Long, List<SubscriptionDTO>> subscriptions, @Context FlowElements flowElements) {
    if (subscriptions == null) {
      return null;
    }
    Map<Long, List<Subscription>> result = new java.util.HashMap<>();
    for (Map.Entry<Long, List<SubscriptionDTO>> entry : subscriptions.entrySet()) {
      List<Subscription> mappedList = new java.util.ArrayList<>();
      for (SubscriptionDTO dto : entry.getValue()) {
        mappedList.add(map(dto, flowElements));
      }
      result.put(entry.getKey(), mappedList);
    }
    return result;
  }

  @Mapping(
      target = "boundaryEvent",
      expression =
          "java((io.taktx.engine.pd.model.BoundaryEvent)flowElements.getFlowNode(flowElements.getIndex(source.getBoundaryEventIndex())).orElseThrow())")
  BoundaryEventCatchAllErrorSubscription map(
      BoundaryEventCatchAllErrorSubscriptionDTO source, @Context FlowElements flowElements);

  @Mapping(
      target = "boundaryEvent",
      expression =
          "java((io.taktx.engine.pd.model.BoundaryEvent)flowElements.getFlowNode(flowElements.getIndex(source.getBoundaryEventIndex())).orElseThrow())")
  BoundaryEventCatchAllEscalationSubscription map(
      BoundaryEventCatchAllEscalationSubscriptionDTO source, @Context FlowElements flowElements);

  @Mapping(
      target = "boundaryEvent",
      expression =
          "java((io.taktx.engine.pd.model.BoundaryEvent)flowElements.getFlowNode(flowElements.getIndex(source.getBoundaryEventIndex())).orElseThrow())")
  BoundaryEventErrorSubscription map(
      BoundaryEventErrorSubscriptionDTO source, @Context FlowElements flowElements);

  @Mapping(
      target = "boundaryEvent",
      expression =
          "java((io.taktx.engine.pd.model.BoundaryEvent)flowElements.getFlowNode(flowElements.getIndex(source.getBoundaryEventIndex())).orElseThrow())")
  BoundaryEventEscalationSubscription map(
      BoundaryEventEscalationSubscriptionDTO source, @Context FlowElements flowElements);

  @Mapping(
      target = "eventSubProcess",
      expression =
          "java((io.taktx.engine.pd.model.SubProcess)flowElements.getFlowNode(flowElements.getIndex(source.getEventSubprocessIndex())).orElseThrow())")
  @Mapping(
      target = "startEvent",
      expression =
          "java((io.taktx.engine.pd.model.StartEvent)flowElements.getFlowNode(flowElements.getIndex(source.getStartEventIndex())).orElseThrow())")
  EventSubProcessCatchAllErrorSubscription map(
      EventSubProcessCatchAllErrorSubscriptionDTO source, @Context FlowElements flowElements);

  @Mapping(
      target = "eventSubProcess",
      expression =
          "java((io.taktx.engine.pd.model.SubProcess)flowElements.getFlowNode(flowElements.getIndex(source.getEventSubprocessIndex())).orElseThrow())")
  @Mapping(
      target = "startEvent",
      expression =
          "java((io.taktx.engine.pd.model.StartEvent)flowElements.getFlowNode(flowElements.getIndex(source.getStartEventIndex())).orElseThrow())")
  EventSubProcessCatchAllEscalationSubscription map(
      EventSubProcessCatchAllEscalationSubscriptionDTO source, @Context FlowElements flowElements);

  @Mapping(
      target = "eventSubProcess",
      expression =
          "java((io.taktx.engine.pd.model.SubProcess)flowElements.getFlowNode(flowElements.getIndex(source.getEventSubprocessIndex())).orElseThrow())")
  @Mapping(
      target = "startEvent",
      expression =
          "java((io.taktx.engine.pd.model.StartEvent)flowElements.getFlowNode(flowElements.getIndex(source.getStartEventIndex())).orElseThrow())")
  EventSubProcessErrorSubscription map(
      EventSubProcessErrorSubscriptionDTO source, @Context FlowElements flowElements);

  @Mapping(
      target = "eventSubProcess",
      expression =
          "java((io.taktx.engine.pd.model.SubProcess)flowElements.getFlowNode(flowElements.getIndex(source.getEventSubprocessIndex())).orElseThrow())")
  @Mapping(
      target = "startEvent",
      expression =
          "java((io.taktx.engine.pd.model.StartEvent)flowElements.getFlowNode(flowElements.getIndex(source.getStartEventIndex())).orElseThrow())")
  EventSubProcessEscalationSubscription map(
      EventSubProcessEscalationSubscriptionDTO source, @Context FlowElements flowElements);

  @SubclassMapping(
      target = BoundaryEventCatchAllErrorSubscription.class,
      source = BoundaryEventCatchAllErrorSubscriptionDTO.class)
  @SubclassMapping(
      target = BoundaryEventCatchAllEscalationSubscription.class,
      source = BoundaryEventCatchAllEscalationSubscriptionDTO.class)
  @SubclassMapping(
      target = BoundaryEventErrorSubscription.class,
      source = BoundaryEventErrorSubscriptionDTO.class)
  @SubclassMapping(
      target = BoundaryEventEscalationSubscription.class,
      source = BoundaryEventEscalationSubscriptionDTO.class)
  @SubclassMapping(
      target = EventSubProcessCatchAllErrorSubscription.class,
      source = EventSubProcessCatchAllErrorSubscriptionDTO.class)
  @SubclassMapping(
      target = EventSubProcessCatchAllEscalationSubscription.class,
      source = EventSubProcessCatchAllEscalationSubscriptionDTO.class)
  @SubclassMapping(
      target = EventSubProcessErrorSubscription.class,
      source = EventSubProcessErrorSubscriptionDTO.class)
  @SubclassMapping(
      target = EventSubProcessEscalationSubscription.class,
      source = EventSubProcessEscalationSubscriptionDTO.class)
  Subscription map(SubscriptionDTO source, @Context FlowElements flowElements);

  @Mapping(
      target = "elementIndex",
      expression = "java(flowElements.indexOf(source.getFlowNode().getId()))")
  @Mapping(target = "parentElementInstanceId", ignore = true)
  @Mapping(target = "elementId", ignore = true)
  TaskInstanceDTO map(TaskInstance source, @Context FlowElements flowElements);

  @Mapping(
      target = "elementIndex",
      expression = "java(flowElements.indexOf(source.getFlowNode().getId()))")
  @Mapping(target = "parentElementInstanceId", ignore = true)
  @Mapping(target = "elementId", ignore = true)
  @Mapping(target = "attachedInstanceId", ignore = true)
  BoundaryEventInstanceDTO map(BoundaryEventInstance source, @Context FlowElements flowElements);

  @Mapping(
      target = "elementIndex",
      expression = "java(flowElements.indexOf(source.getFlowNode().getId()))")
  @Mapping(target = "parentElementInstanceId", ignore = true)
  @Mapping(target = "elementId", ignore = true)
  StartEventInstanceDTO map(StartEventInstance source, @Context FlowElements flowElements);

  @Mapping(
      target = "elementIndex",
      expression = "java(flowElements.indexOf(source.getFlowNode().getId()))")
  @Mapping(target = "parentElementInstanceId", ignore = true)
  @Mapping(target = "elementId", ignore = true)
  IntermediateCatchEventInstanceDTO map(
      IntermediateCatchEventInstance source, @Context FlowElements flowElements);

  @Mapping(
      target = "elementIndex",
      expression = "java(flowElements.indexOf(source.getFlowNode().getId()))")
  @Mapping(target = "parentElementInstanceId", ignore = true)
  @Mapping(target = "elementId", ignore = true)
  EndEventInstanceDTO map(EndEventInstance source, @Context FlowElements flowElements);

  @Mapping(
      target = "elementIndex",
      expression = "java(flowElements.indexOf(source.getFlowNode().getId()))")
  @Mapping(target = "parentElementInstanceId", ignore = true)
  @Mapping(target = "elementId", ignore = true)
  IntermediateThrowEventInstanceDTO map(
      IntermediateThrowEventInstance source, @Context FlowElements flowElements);

  @Mapping(
      target = "elementIndex",
      expression = "java(flowElements.indexOf(source.getFlowNode().getId()))")
  @Mapping(target = "parentElementInstanceId", ignore = true)
  @Mapping(target = "elementId", ignore = true)
  ServiceTaskInstanceDTO map(ServiceTaskInstance source, @Context FlowElements flowElements);

  @Mapping(
      target = "elementIndex",
      expression = "java(flowElements.indexOf(source.getFlowNode().getId()))")
  @Mapping(target = "parentElementInstanceId", ignore = true)
  @Mapping(target = "elementId", ignore = true)
  SendTaskInstanceDTO map(SendTaskInstance source, @Context FlowElements flowElements);

  @Mapping(
      target = "elementIndex",
      expression = "java(flowElements.indexOf(source.getFlowNode().getId()))")
  @Mapping(target = "parentElementInstanceId", ignore = true)
  @Mapping(target = "elementId", ignore = true)
  MessageEndEventInstanceDTO map(
      MessageEndEventInstance source, @Context FlowElements flowElements);

  @Mapping(
      target = "elementIndex",
      expression = "java(flowElements.indexOf(source.getFlowNode().getId()))")
  @Mapping(target = "parentElementInstanceId", ignore = true)
  @Mapping(target = "elementId", ignore = true)
  MessageIntermediateThrowEventInstanceDTO map(
      MessageIntermediateThrowEventInstance source, @Context FlowElements flowElements);

  @Mapping(
      target = "elementIndex",
      expression = "java(flowElements.indexOf(source.getFlowNode().getId()))")
  @Mapping(target = "parentElementInstanceId", ignore = true)
  @Mapping(target = "elementId", ignore = true)
  ScriptTaskInstanceDTO map(ScriptTaskInstance source, @Context FlowElements flowElements);

  @Mapping(
      target = "elementIndex",
      expression = "java(flowElements.indexOf(source.getFlowNode().getId()))")
  @Mapping(target = "parentElementInstanceId", ignore = true)
  @Mapping(target = "elementId", ignore = true)
  UserTaskInstanceDTO map(UserTaskInstance source, @Context FlowElements flowElements);

  @Mapping(
      target = "elementIndex",
      expression = "java(flowElements.indexOf(source.getFlowNode().getId()))")
  @Mapping(target = "parentElementInstanceId", ignore = true)
  @Mapping(target = "elementId", ignore = true)
  ReceiveTaskInstanceDTO map(ReceiveTaskInstance source, @Context FlowElements flowElements);

  @Mapping(
      target = "elementIndex",
      expression = "java(flowElements.indexOf(source.getFlowNode().getId()))")
  @Mapping(target = "parentElementInstanceId", ignore = true)
  @Mapping(target = "elementId", ignore = true)
  SubProcessInstanceDTO map(SubProcessInstance source, @Context FlowElements flowElements);

  @Mapping(
      target = "elementIndex",
      expression = "java(flowElements.indexOf(source.getFlowNode().getId()))")
  @Mapping(target = "parentElementInstanceId", ignore = true)
  @Mapping(target = "elementId", ignore = true)
  CallActivityInstanceDTO map(CallActivityInstance source, @Context FlowElements flowElements);

  @Mapping(
      target = "elementIndex",
      expression = "java(flowElements.indexOf(source.getFlowNode().getId()))")
  @Mapping(target = "parentElementInstanceId", ignore = true)
  @Mapping(target = "elementId", ignore = true)
  MultiInstanceInstanceDTO map(MultiInstanceInstance source, @Context FlowElements flowElements);

  @Mapping(
      target = "elementIndex",
      expression = "java(flowElements.indexOf(source.getFlowNode().getId()))")
  @Mapping(target = "parentElementInstanceId", ignore = true)
  @Mapping(target = "elementId", ignore = true)
  ExclusiveGatewayInstanceDTO map(
      ExclusiveGatewayInstance source, @Context FlowElements flowElements);

  @Mapping(
      target = "elementIndex",
      expression = "java(flowElements.indexOf(source.getFlowNode().getId()))")
  @Mapping(target = "parentElementInstanceId", ignore = true)
  @Mapping(target = "elementId", ignore = true)
  InclusiveGatewayInstanceDTO map(
      InclusiveGatewayInstance source, @Context FlowElements flowElements);

  @Mapping(
      target = "elementIndex",
      expression = "java(flowElements.indexOf(source.getFlowNode().getId()))")
  @Mapping(target = "parentElementInstanceId", ignore = true)
  @Mapping(target = "elementId", ignore = true)
  EventBasedGatewayInstanceDTO map(
      EventBasedGatewayInstance source, @Context FlowElements flowElements);

  @Mapping(
      target = "elementIndex",
      expression = "java(flowElements.indexOf(source.getFlowNode().getId()))")
  @Mapping(target = "parentElementInstanceId", ignore = true)
  @Mapping(target = "elementId", ignore = true)
  ParallelGatewayInstanceDTO map(
      ParallelGatewayInstance source, @Context FlowElements flowElements);

  @SubclassMapping(source = BoundaryEventInstance.class, target = BoundaryEventInstanceDTO.class)
  @SubclassMapping(source = StartEventInstance.class, target = StartEventInstanceDTO.class)
  @SubclassMapping(
      source = IntermediateCatchEventInstance.class,
      target = IntermediateCatchEventInstanceDTO.class)
  @SubclassMapping(source = EndEventInstance.class, target = EndEventInstanceDTO.class)
  @SubclassMapping(
      source = IntermediateThrowEventInstance.class,
      target = IntermediateThrowEventInstanceDTO.class)
  @SubclassMapping(source = ServiceTaskInstance.class, target = ServiceTaskInstanceDTO.class)
  @SubclassMapping(source = SendTaskInstance.class, target = SendTaskInstanceDTO.class)
  @SubclassMapping(
      source = MessageEndEventInstance.class,
      target = MessageEndEventInstanceDTO.class)
  @SubclassMapping(
      source = MessageIntermediateThrowEventInstance.class,
      target = MessageIntermediateThrowEventInstanceDTO.class)
  @SubclassMapping(source = UserTaskInstance.class, target = UserTaskInstanceDTO.class)
  @SubclassMapping(source = ReceiveTaskInstance.class, target = ReceiveTaskInstanceDTO.class)
  @SubclassMapping(source = ScriptTaskInstance.class, target = ScriptTaskInstanceDTO.class)
  @SubclassMapping(source = SubProcessInstance.class, target = SubProcessInstanceDTO.class)
  @SubclassMapping(source = CallActivityInstance.class, target = CallActivityInstanceDTO.class)
  @SubclassMapping(source = MultiInstanceInstance.class, target = MultiInstanceInstanceDTO.class)
  @SubclassMapping(
      source = ExclusiveGatewayInstance.class,
      target = ExclusiveGatewayInstanceDTO.class)
  @SubclassMapping(
      source = InclusiveGatewayInstance.class,
      target = InclusiveGatewayInstanceDTO.class)
  @SubclassMapping(
      source = EventBasedGatewayInstance.class,
      target = EventBasedGatewayInstanceDTO.class)
  @SubclassMapping(
      source = ParallelGatewayInstance.class,
      target = ParallelGatewayInstanceDTO.class)
  // Task state should come last
  @SubclassMapping(source = TaskInstance.class, target = TaskInstanceDTO.class)
  @Mapping(target = "parentElementInstanceId", source = "parentInstance.elementInstanceId")
  @Mapping(target = "elementIndex", ignore = true)
  @Mapping(target = "elementId", ignore = true)
  FlowNodeInstanceDTO map(FlowNodeInstance<?> source, @Context FlowElements flowElements);

  ProcessInstanceDTO map(ProcessInstance source, @Context FlowElements flowElements);

  IoVariableMappingDTO map(IoVariableMapping value);

  @Mapping(target = "state", ignore = true)
  @Mapping(target = "stateNoChange", ignore = true)
  @Mapping(target = "parentScope", ignore = true)
  @Mapping(target = "processInstanceId", ignore = true)
  @Mapping(target = "flowNodeInstances", ignore = true)
  @Mapping(target = "flowNodeInstanceStore", ignore = true)
  @Mapping(target = "initialState", ignore = true)
  @Mapping(target = "stateChanged", ignore = true)
  @Mapping(target = "parentFlowNodeInstance", ignore = true)
  @Mapping(target = "processInstanceMapper", ignore = true)
  @Mapping(target = "flowElements", ignore = true)
  @Mapping(target = "scopePath", ignore = true)
  Scope map(ScopeDTO source, @Context FlowElements flowElements);

  @Mapping(
      target = "instanceSubscriptions",
      source = "instanceSubscriptions",
      qualifiedByName = "mapInstanceSubscriptionsDTO")
  SubscriptionsDTO map(Subscriptions source, @Context FlowElements flowElements);

  // Helper method to map the nested Map<Long, List<Subscription>> to Map<Long,
  // List<SubscriptionDTO>>
  @Named("mapInstanceSubscriptionsDTO")
  default Map<Long, List<SubscriptionDTO>> mapInstanceSubscriptionsDTO(
      Map<Long, List<Subscription>> subscriptions, @Context FlowElements flowElements) {
    if (subscriptions == null) {
      return null;
    }
    Map<Long, List<SubscriptionDTO>> result = new java.util.HashMap<>();
    for (Map.Entry<Long, List<Subscription>> entry : subscriptions.entrySet()) {
      List<SubscriptionDTO> mappedList = new java.util.ArrayList<>();
      for (Subscription subscription : entry.getValue()) {
        mappedList.add(map(subscription, flowElements));
      }
      result.put(entry.getKey(), mappedList);
    }
    return result;
  }

  @Mapping(
      target = "boundaryEventIndex",
      expression = "java(flowElements.indexOf(source.getBoundaryEvent().getId()))")
  BoundaryEventCatchAllErrorSubscriptionDTO map(
      BoundaryEventCatchAllErrorSubscription source, @Context FlowElements flowElements);

  @Mapping(
      target = "boundaryEventIndex",
      expression = "java(flowElements.indexOf(source.getBoundaryEvent().getId()))")
  BoundaryEventCatchAllEscalationSubscriptionDTO map(
      BoundaryEventCatchAllEscalationSubscription source, @Context FlowElements flowElements);

  @Mapping(
      target = "boundaryEventIndex",
      expression = "java(flowElements.indexOf(source.getBoundaryEvent().getId()))")
  BoundaryEventErrorSubscriptionDTO map(
      BoundaryEventErrorSubscription source, @Context FlowElements flowElements);

  @Mapping(
      target = "boundaryEventIndex",
      expression = "java(flowElements.indexOf(source.getBoundaryEvent().getId()))")
  BoundaryEventEscalationSubscriptionDTO map(
      BoundaryEventEscalationSubscription source, @Context FlowElements flowElements);

  @Mapping(
      target = "eventSubprocessIndex",
      expression = "java(flowElements.indexOf(source.getEventSubProcess().getId()))")
  @Mapping(
      target = "startEventIndex",
      expression = "java(flowElements.indexOf(source.getStartEvent().getId()))")
  EventSubProcessCatchAllErrorSubscriptionDTO map(
      EventSubProcessCatchAllErrorSubscription source, @Context FlowElements flowElements);

  @Mapping(
      target = "eventSubprocessIndex",
      expression = "java(flowElements.indexOf(source.getEventSubProcess().getId()))")
  @Mapping(
      target = "startEventIndex",
      expression = "java(flowElements.indexOf(source.getStartEvent().getId()))")
  EventSubProcessCatchAllEscalationSubscriptionDTO map(
      EventSubProcessCatchAllEscalationSubscription source, @Context FlowElements flowElements);

  @Mapping(
      target = "eventSubprocessIndex",
      expression = "java(flowElements.indexOf(source.getEventSubProcess().getId()))")
  @Mapping(
      target = "startEventIndex",
      expression = "java(flowElements.indexOf(source.getStartEvent().getId()))")
  EventSubProcessErrorSubscriptionDTO map(
      EventSubProcessErrorSubscription source, @Context FlowElements flowElements);

  @Mapping(
      target = "eventSubprocessIndex",
      expression = "java(flowElements.indexOf(source.getEventSubProcess().getId()))")
  @Mapping(
      target = "startEventIndex",
      expression = "java(flowElements.indexOf(source.getStartEvent().getId()))")
  EventSubProcessEscalationSubscriptionDTO map(
      EventSubProcessEscalationSubscription source, @Context FlowElements flowElements);

  @SubclassMapping(
      source = BoundaryEventCatchAllErrorSubscription.class,
      target = BoundaryEventCatchAllErrorSubscriptionDTO.class)
  @SubclassMapping(
      source = BoundaryEventCatchAllEscalationSubscription.class,
      target = BoundaryEventCatchAllEscalationSubscriptionDTO.class)
  @SubclassMapping(
      source = BoundaryEventErrorSubscription.class,
      target = BoundaryEventErrorSubscriptionDTO.class)
  @SubclassMapping(
      source = BoundaryEventEscalationSubscription.class,
      target = BoundaryEventEscalationSubscriptionDTO.class)
  @SubclassMapping(
      source = EventSubProcessCatchAllErrorSubscription.class,
      target = EventSubProcessCatchAllErrorSubscriptionDTO.class)
  @SubclassMapping(
      source = EventSubProcessCatchAllEscalationSubscription.class,
      target = EventSubProcessCatchAllEscalationSubscriptionDTO.class)
  @SubclassMapping(
      source = EventSubProcessErrorSubscription.class,
      target = EventSubProcessErrorSubscriptionDTO.class)
  @SubclassMapping(
      source = EventSubProcessEscalationSubscription.class,
      target = EventSubProcessEscalationSubscriptionDTO.class)
  SubscriptionDTO map(Subscription source, @Context FlowElements flowElements);

  @AfterMapping
  default void mapState(ScopeDTO source, @MappingTarget Scope target) {
    if (source.getState().isDone()) {
      target.setStateNoChange(source.getState());
    } else {
      target.setStateNoChange(ExecutionState.INITIALIZED);
    }
  }

  @AfterMapping
  default void mapState(FlowNodeInstanceDTO source, @MappingTarget FlowNodeInstance<?> target) {
    target.setStateNoChange(source.getState());
  }

  @ObjectFactory
  default <T extends FlowNodeInstance<?>> T resolveEquipment(
      FlowNodeInstanceDTO ignoredSourceDto, @TargetType Class<T> type) {
    return getNewInstance(type);
  }

  @ObjectFactory
  default <T extends FlowNodeInstanceDTO> T resolveEquipment(
      FlowNodeInstance<?> ignoredSourceDto, @TargetType Class<T> type) {
    return getNewInstance(type);
  }

  @ObjectFactory
  default <T extends Subscription> T resolveSubscription(
      SubscriptionDTO ignoredSourceDto, @TargetType Class<T> type) {
    return getNewInstance(type);
  }

  @ObjectFactory
  default <T extends SubscriptionDTO> T resolveSubscription(
      Subscription ignoredSourceDto, @TargetType Class<T> type) {
    return getNewInstance(type);
  }

  // NOSONAR
  private static <T> T getNewInstance(Class<T> type) {
    try {
      return type.getDeclaredConstructor().newInstance();
    } catch (InstantiationException
        | IllegalAccessException
        | NoSuchMethodException
        | InvocationTargetException e) {
      throw new IllegalStateException(e);
    }
  }

  // Include FlowElements as a @Context so MapStruct will use the FlowElements-aware
  // mapping method when mapping the Map values (FlowNodeInstance -> FlowNodeInstanceDTO)
  Map<UUID, FlowNodeInstanceDTO> map(
      Map<UUID, FlowNodeInstance<?>> instances, @Context FlowElements flowElements);
}
