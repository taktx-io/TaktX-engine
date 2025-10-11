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
import io.taktx.dto.ExclusiveGatewayInstanceDTO;
import io.taktx.dto.ExecutionState;
import io.taktx.dto.FlowNodeInstanceDTO;
import io.taktx.dto.InclusiveGatewayInstanceDTO;
import io.taktx.dto.IntermediateCatchEventInstanceDTO;
import io.taktx.dto.IntermediateThrowEventInstanceDTO;
import io.taktx.dto.IoVariableMappingDTO;
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
import io.taktx.dto.TaskInstanceDTO;
import io.taktx.dto.UserTaskInstanceDTO;
import io.taktx.engine.pd.model.FlowElements;
import io.taktx.engine.pd.model.FlowNode;
import io.taktx.engine.pd.model.IoVariableMapping;
import io.taktx.engine.pd.model.SubProcess;
import io.taktx.engine.pi.model.BoundaryEventInstance;
import io.taktx.engine.pi.model.CallActivityInstance;
import io.taktx.engine.pi.model.EndEventInstance;
import io.taktx.engine.pi.model.ExclusiveGatewayInstance;
import io.taktx.engine.pi.model.FlowNodeInstance;
import io.taktx.engine.pi.model.InclusiveGatewayInstance;
import io.taktx.engine.pi.model.IntermediateCatchEventInstance;
import io.taktx.engine.pi.model.IntermediateThrowEventInstance;
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
import java.lang.reflect.InvocationTargetException;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.mapstruct.AfterMapping;
import org.mapstruct.Context;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
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
  ParallelGatewayInstance map(
      ParallelGatewayInstanceDTO source, @Context FlowElements flowElements);

  @Mapping(
      target = "flowNode",
      expression =
          "java((io.taktx.engine.pd.model.InclusiveGateway)flowElements.getFlowNode(flowElements.getIndex(source.getElementIndex())).orElseThrow())")
  @Mapping(target = "parentInstance", ignore = true)
  @Mapping(target = "dirty", ignore = true)
  @Mapping(target = "state", ignore = true)
  InclusiveGatewayInstance map(
      InclusiveGatewayInstanceDTO source, @Context FlowElements flowElements);

  @Mapping(
      target = "flowNode",
      expression =
          "java((io.taktx.engine.pd.model.ExclusiveGateway)flowElements.getFlowNode(flowElements.getIndex(source.getElementIndex())).orElseThrow())")
  @Mapping(target = "parentInstance", ignore = true)
  @Mapping(target = "dirty", ignore = true)
  @Mapping(target = "state", ignore = true)
  ExclusiveGatewayInstance map(
      ExclusiveGatewayInstanceDTO source, @Context FlowElements flowElements);

  @Mapping(
      target = "flowNode",
      expression =
          "java((io.taktx.engine.pd.model.BoundaryEvent)flowElements.getFlowNode(flowElements.getIndex(source.getElementIndex())).orElseThrow())")
  @Mapping(target = "parentInstance", ignore = true)
  @Mapping(target = "dirty", ignore = true)
  @Mapping(target = "stateChanged", ignore = true)
  @Mapping(target = "wasWaiting", ignore = true)
  @Mapping(target = "wasNew", ignore = true)
  @Mapping(target = "state", ignore = true)
  BoundaryEventInstance map(BoundaryEventInstanceDTO source, @Context FlowElements flowElements);

  @Mapping(
      target = "flowNode",
      expression =
          "java((io.taktx.engine.pd.model.StartEvent)flowElements.getFlowNode(flowElements.getIndex(source.getElementIndex())).orElseThrow())")
  @Mapping(target = "parentInstance", ignore = true)
  @Mapping(target = "dirty", ignore = true)
  @Mapping(target = "stateChanged", ignore = true)
  @Mapping(target = "wasWaiting", ignore = true)
  @Mapping(target = "wasNew", ignore = true)
  @Mapping(target = "state", ignore = true)
  StartEventInstance map(StartEventInstanceDTO source, @Context FlowElements flowElements);

  @Mapping(
      target = "flowNode",
      expression =
          "java((io.taktx.engine.pd.model.IntermediateCatchEvent)flowElements.getFlowNode(flowElements.getIndex(source.getElementIndex())).orElseThrow())")
  @Mapping(target = "parentInstance", ignore = true)
  @Mapping(target = "dirty", ignore = true)
  @Mapping(target = "stateChanged", ignore = true)
  @Mapping(target = "wasWaiting", ignore = true)
  @Mapping(target = "wasNew", ignore = true)
  @Mapping(target = "state", ignore = true)
  IntermediateCatchEventInstance map(
      IntermediateCatchEventInstanceDTO source, @Context FlowElements flowElements);

  @Mapping(
      target = "flowNode",
      expression =
          "java((io.taktx.engine.pd.model.EndEvent)flowElements.getFlowNode(flowElements.getIndex(source.getElementIndex())).orElseThrow())")
  @Mapping(target = "parentInstance", ignore = true)
  @Mapping(target = "dirty", ignore = true)
  @Mapping(target = "state", ignore = true)
  EndEventInstance map(EndEventInstanceDTO source, @Context FlowElements flowElements);

  @Mapping(
      target = "flowNode",
      expression =
          "java((io.taktx.engine.pd.model.IntermediateThrowEvent)flowElements.getFlowNode(flowElements.getIndex(source.getElementIndex())).orElseThrow())")
  @Mapping(target = "parentInstance", ignore = true)
  @Mapping(target = "dirty", ignore = true)
  @Mapping(target = "state", ignore = true)
  IntermediateThrowEventInstance map(
      IntermediateThrowEventInstanceDTO source, @Context FlowElements flowElements);

  @Mapping(
      target = "flowNode",
      expression =
          "java((io.taktx.engine.pd.model.ServiceTask)flowElements.getFlowNode(flowElements.getIndex(source.getElementIndex())).orElseThrow())")
  @Mapping(target = "parentInstance", ignore = true)
  @Mapping(target = "dirty", ignore = true)
  @Mapping(target = "stateChanged", ignore = true)
  @Mapping(target = "wasWaiting", ignore = true)
  @Mapping(target = "wasNew", ignore = true)
  @Mapping(target = "state", ignore = true)
  ServiceTaskInstance map(ServiceTaskInstanceDTO source, @Context FlowElements flowElements);

  @Mapping(
      target = "flowNode",
      expression =
          "java((io.taktx.engine.pd.model.SendTask)flowElements.getFlowNode(flowElements.getIndex(source.getElementIndex())).orElseThrow())")
  @Mapping(target = "parentInstance", ignore = true)
  @Mapping(target = "dirty", ignore = true)
  @Mapping(target = "stateChanged", ignore = true)
  @Mapping(target = "wasWaiting", ignore = true)
  @Mapping(target = "wasNew", ignore = true)
  @Mapping(target = "state", ignore = true)
  SendTaskInstance map(SendTaskInstanceDTO source, @Context FlowElements flowElements);

  @Mapping(
      target = "flowNode",
      expression =
          "java((io.taktx.engine.pd.model.ScriptTask)flowElements.getFlowNode(flowElements.getIndex(source.getElementIndex())).orElseThrow())")
  @Mapping(target = "parentInstance", ignore = true)
  @Mapping(target = "dirty", ignore = true)
  @Mapping(target = "stateChanged", ignore = true)
  @Mapping(target = "wasWaiting", ignore = true)
  @Mapping(target = "wasNew", ignore = true)
  @Mapping(target = "state", ignore = true)
  ScriptTaskInstance map(ScriptTaskInstanceDTO source, @Context FlowElements flowElements);

  @Mapping(
      target = "flowNode",
      expression =
          "java((io.taktx.engine.pd.model.UserTask)flowElements.getFlowNode(flowElements.getIndex(source.getElementIndex())).orElseThrow())")
  @Mapping(target = "parentInstance", ignore = true)
  @Mapping(target = "dirty", ignore = true)
  @Mapping(target = "stateChanged", ignore = true)
  @Mapping(target = "wasWaiting", ignore = true)
  @Mapping(target = "wasNew", ignore = true)
  @Mapping(target = "state", ignore = true)
  UserTaskInstance map(UserTaskInstanceDTO source, @Context FlowElements flowElements);

  @Mapping(
      target = "flowNode",
      expression =
          "java((io.taktx.engine.pd.model.ReceiveTask)flowElements.getFlowNode(flowElements.getIndex(source.getElementIndex())).orElseThrow())")
  @Mapping(target = "parentInstance", ignore = true)
  @Mapping(target = "dirty", ignore = true)
  @Mapping(target = "stateChanged", ignore = true)
  @Mapping(target = "wasWaiting", ignore = true)
  @Mapping(target = "wasNew", ignore = true)
  @Mapping(target = "state", ignore = true)
  ReceiveTaskInstance map(ReceiveTaskInstanceDTO source, @Context FlowElements flowElements);

  @Mapping(
      target = "flowNode",
      expression =
          "java((io.taktx.engine.pd.model.SubProcess)flowElements.getFlowNode(flowElements.getIndex(source.getElementIndex())).orElseThrow())")
  @Mapping(target = "parentInstance", ignore = true)
  @Mapping(target = "dirty", ignore = true)
  @Mapping(target = "stateChanged", ignore = true)
  @Mapping(target = "wasWaiting", ignore = true)
  @Mapping(target = "wasNew", ignore = true)
  @Mapping(target = "state", ignore = true)
  SubProcessInstance map(SubProcessInstanceDTO source, @Context FlowElements flowElements);

  @Mapping(
      target = "flowNode",
      expression =
          "java((io.taktx.engine.pd.model.CallActivity)flowElements.getFlowNode(flowElements.getIndex(source.getElementIndex())).orElseThrow())")
  @Mapping(target = "parentInstance", ignore = true)
  @Mapping(target = "dirty", ignore = true)
  @Mapping(target = "stateChanged", ignore = true)
  @Mapping(target = "wasWaiting", ignore = true)
  @Mapping(target = "wasNew", ignore = true)
  @Mapping(target = "state", ignore = true)
  CallActivityInstance map(CallActivityInstanceDTO source, @Context FlowElements flowElements);

  @Mapping(
      target = "flowNode",
      expression =
          "java((io.taktx.engine.pd.model.Activity)flowElements.getFlowNode(flowElements.getIndex(source.getElementIndex())).orElseThrow())")
  @Mapping(target = "parentInstance", ignore = true)
  @Mapping(target = "dirty", ignore = true)
  @Mapping(target = "wasWaiting", ignore = true)
  @Mapping(target = "wasNew", ignore = true)
  @Mapping(target = "state", ignore = true)
  MultiInstanceInstance map(MultiInstanceInstanceDTO source, @Context FlowElements flowElements);

  @Mapping(
      target = "flowNode",
      expression =
          "java((io.taktx.engine.pd.model.Task)flowElements.getFlowNode(flowElements.getIndex(source.getElementIndex())).orElseThrow())")
  @Mapping(target = "parentInstance", ignore = true)
  @Mapping(target = "wasWaiting", ignore = true)
  @Mapping(target = "stateChanged", ignore = true)
  @Mapping(target = "dirty", ignore = true)
  @Mapping(target = "wasNew", ignore = true)
  @Mapping(target = "state", ignore = true)
  TaskInstance map(TaskInstanceDTO source, @Context FlowElements flowElements);

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
  // Task state should come last
  @SubclassMapping(target = TaskInstance.class, source = TaskInstanceDTO.class)
  @Mapping(target = "parentInstance", ignore = true)
  @Mapping(target = "flowNode", ignore = true)
  @Mapping(target = "dirty", ignore = true)
  @Mapping(target = "state", ignore = true)
  FlowNodeInstance<?> map(FlowNodeInstanceDTO source, @Context FlowElements flowElements);

  ProcessInstance map(ProcessInstanceDTO source, @Context FlowElements flowElements);

  @Mapping(
      target = "elementIndex",
      expression = "java(flowElements.indexOf(source.getFlowNode().getId()))")
  @Mapping(target = "parentElementInstanceId", ignore = true)
  TaskInstanceDTO map(TaskInstance source, @Context FlowElements flowElements);

  @Mapping(
      target = "elementIndex",
      expression = "java(flowElements.indexOf(source.getFlowNode().getId()))")
  @Mapping(target = "parentElementInstanceId", ignore = true)
  BoundaryEventInstanceDTO map(BoundaryEventInstance source, @Context FlowElements flowElements);

  @Mapping(
      target = "elementIndex",
      expression = "java(flowElements.indexOf(source.getFlowNode().getId()))")
  @Mapping(target = "parentElementInstanceId", ignore = true)
  StartEventInstanceDTO map(StartEventInstance source, @Context FlowElements flowElements);

  @Mapping(
      target = "elementIndex",
      expression = "java(flowElements.indexOf(source.getFlowNode().getId()))")
  @Mapping(target = "parentElementInstanceId", ignore = true)
  IntermediateCatchEventInstanceDTO map(
      IntermediateCatchEventInstance source, @Context FlowElements flowElements);

  @Mapping(
      target = "elementIndex",
      expression = "java(flowElements.indexOf(source.getFlowNode().getId()))")
  @Mapping(target = "parentElementInstanceId", ignore = true)
  EndEventInstanceDTO map(EndEventInstance source, @Context FlowElements flowElements);

  @Mapping(
      target = "elementIndex",
      expression = "java(flowElements.indexOf(source.getFlowNode().getId()))")
  @Mapping(target = "parentElementInstanceId", ignore = true)
  IntermediateThrowEventInstanceDTO map(
      IntermediateThrowEventInstance source, @Context FlowElements flowElements);

  @Mapping(
      target = "elementIndex",
      expression = "java(flowElements.indexOf(source.getFlowNode().getId()))")
  @Mapping(target = "parentElementInstanceId", ignore = true)
  ServiceTaskInstanceDTO map(ServiceTaskInstance source, @Context FlowElements flowElements);

  @Mapping(
      target = "elementIndex",
      expression = "java(flowElements.indexOf(source.getFlowNode().getId()))")
  @Mapping(target = "parentElementInstanceId", ignore = true)
  SendTaskInstanceDTO map(SendTaskInstance source, @Context FlowElements flowElements);

  @Mapping(
      target = "elementIndex",
      expression = "java(flowElements.indexOf(source.getFlowNode().getId()))")
  @Mapping(target = "parentElementInstanceId", ignore = true)
  ScriptTaskInstanceDTO map(ScriptTaskInstance source, @Context FlowElements flowElements);

  @Mapping(
      target = "elementIndex",
      expression = "java(flowElements.indexOf(source.getFlowNode().getId()))")
  @Mapping(target = "parentElementInstanceId", ignore = true)
  UserTaskInstanceDTO map(UserTaskInstance source, @Context FlowElements flowElements);

  @Mapping(
      target = "elementIndex",
      expression = "java(flowElements.indexOf(source.getFlowNode().getId()))")
  @Mapping(target = "parentElementInstanceId", ignore = true)
  ReceiveTaskInstanceDTO map(ReceiveTaskInstance source, @Context FlowElements flowElements);

  @Mapping(
      target = "elementIndex",
      expression = "java(flowElements.indexOf(source.getFlowNode().getId()))")
  @Mapping(target = "parentElementInstanceId", ignore = true)
  SubProcessInstanceDTO map(SubProcessInstance source, @Context FlowElements flowElements);

  @Mapping(
      target = "elementIndex",
      expression = "java(flowElements.indexOf(source.getFlowNode().getId()))")
  @Mapping(target = "parentElementInstanceId", ignore = true)
  CallActivityInstanceDTO map(CallActivityInstance source, @Context FlowElements flowElements);

  @Mapping(
      target = "elementIndex",
      expression = "java(flowElements.indexOf(source.getFlowNode().getId()))")
  @Mapping(target = "parentElementInstanceId", ignore = true)
  MultiInstanceInstanceDTO map(MultiInstanceInstance source, @Context FlowElements flowElements);

  @Mapping(
      target = "elementIndex",
      expression = "java(flowElements.indexOf(source.getFlowNode().getId()))")
  @Mapping(target = "parentElementInstanceId", ignore = true)
  ExclusiveGatewayInstanceDTO map(
      ExclusiveGatewayInstance source, @Context FlowElements flowElements);

  @Mapping(
      target = "elementIndex",
      expression = "java(flowElements.indexOf(source.getFlowNode().getId()))")
  @Mapping(target = "parentElementInstanceId", ignore = true)
  InclusiveGatewayInstanceDTO map(
      InclusiveGatewayInstance source, @Context FlowElements flowElements);

  @Mapping(
      target = "elementIndex",
      expression = "java(flowElements.indexOf(source.getFlowNode().getId()))")
  @Mapping(target = "parentElementInstanceId", ignore = true)
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
      source = ParallelGatewayInstance.class,
      target = ParallelGatewayInstanceDTO.class)
  // Task state should come last
  @SubclassMapping(source = TaskInstance.class, target = TaskInstanceDTO.class)
  @Mapping(target = "parentElementInstanceId", source = "parentInstance.elementInstanceId")
  FlowNodeInstanceDTO map(FlowNodeInstance source, @Context FlowElements flowElements);

  ProcessInstanceDTO map(ProcessInstance source);

  IoVariableMappingDTO map(IoVariableMapping value);

  @Mapping(target = "state", ignore = true)
  Scope map(ScopeDTO source);

  @AfterMapping
  default void mapState(ScopeDTO source, @MappingTarget Scope target) {
    if (source.getActivityToBoundaryEvents() != null) {
      for (Map.Entry<Long, Set<Long>> entry : source.getActivityToBoundaryEvents().entrySet()) {
        for (Long boundaryEvent : entry.getValue()) {
          target.getBoundaryEventToActivity().put(boundaryEvent, entry.getKey());
        }
      }
    }

    if (source.getState().isDone()) {
      target.setStateNoChange(source.getState());
    } else {
      target.setStateNoChange(ExecutionState.INITIALIZED);
    }
  }

  @AfterMapping
  default void mapState(FlowNodeInstanceDTO source, @MappingTarget FlowNodeInstance target) {
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

  Map<UUID, FlowNodeInstanceDTO> map(Map<UUID, FlowNodeInstance<?>> instances);
}
