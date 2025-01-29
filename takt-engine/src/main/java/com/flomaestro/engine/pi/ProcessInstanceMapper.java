package com.flomaestro.engine.pi;

import com.flomaestro.engine.pd.model.FlowElements;
import com.flomaestro.engine.pd.model.FlowNode;
import com.flomaestro.engine.pd.model.IoVariableMapping;
import com.flomaestro.engine.pd.model.SubProcess;
import com.flomaestro.engine.pi.model.BoundaryEventInstance;
import com.flomaestro.engine.pi.model.CallActivityInstance;
import com.flomaestro.engine.pi.model.EndEventInstance;
import com.flomaestro.engine.pi.model.ExclusiveGatewayInstance;
import com.flomaestro.engine.pi.model.FlowNodeInstance;
import com.flomaestro.engine.pi.model.InclusiveGatewayInstance;
import com.flomaestro.engine.pi.model.IntermediateCatchEventInstance;
import com.flomaestro.engine.pi.model.IntermediateThrowEventInstance;
import com.flomaestro.engine.pi.model.MultiInstanceInstance;
import com.flomaestro.engine.pi.model.ParallelGatewayInstance;
import com.flomaestro.engine.pi.model.ProcessInstance;
import com.flomaestro.engine.pi.model.ReceiveTaskInstance;
import com.flomaestro.engine.pi.model.SendTaskInstance;
import com.flomaestro.engine.pi.model.ServiceTaskInstance;
import com.flomaestro.engine.pi.model.StartEventInstance;
import com.flomaestro.engine.pi.model.SubProcessInstance;
import com.flomaestro.engine.pi.model.TaskInstance;
import com.flomaestro.takt.dto.v_1_0_0.BoundaryEventInstanceDTO;
import com.flomaestro.takt.dto.v_1_0_0.CallActivityInstanceDTO;
import com.flomaestro.takt.dto.v_1_0_0.EndEventInstanceDTO;
import com.flomaestro.takt.dto.v_1_0_0.ExclusiveGatewayInstanceDTO;
import com.flomaestro.takt.dto.v_1_0_0.FlowNodeInstanceDTO;
import com.flomaestro.takt.dto.v_1_0_0.InclusiveGatewayInstanceDTO;
import com.flomaestro.takt.dto.v_1_0_0.IntermediateCatchEventInstanceDTO;
import com.flomaestro.takt.dto.v_1_0_0.IntermediateThrowEventInstanceDTO;
import com.flomaestro.takt.dto.v_1_0_0.IoVariableMappingDTO;
import com.flomaestro.takt.dto.v_1_0_0.MultiInstanceInstanceDTO;
import com.flomaestro.takt.dto.v_1_0_0.ParallelGatewayInstanceDTO;
import com.flomaestro.takt.dto.v_1_0_0.ProcessInstanceDTO;
import com.flomaestro.takt.dto.v_1_0_0.ReceiveTaskInstanceDTO;
import com.flomaestro.takt.dto.v_1_0_0.SendTaskInstanceDTO;
import com.flomaestro.takt.dto.v_1_0_0.ServiceTaskInstanceDTO;
import com.flomaestro.takt.dto.v_1_0_0.StartEventInstanceDTO;
import com.flomaestro.takt.dto.v_1_0_0.SubProcessInstanceDTO;
import com.flomaestro.takt.dto.v_1_0_0.TaskInstanceDTO;
import java.lang.reflect.InvocationTargetException;
import java.util.Map;
import java.util.UUID;
import org.mapstruct.Context;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ObjectFactory;
import org.mapstruct.SubclassMapping;
import org.mapstruct.TargetType;

@Mapper(componentModel = "jakarta")
public interface ProcessInstanceMapper {
  @Mapping(
      target = "flowNode",
      expression =
          "java((com.flomaestro.engine.pd.model.ParallelGateway)flowElements.getFlowNode(source.getElementId()).orElseThrow())")
  @Mapping(target = "parentInstance", ignore = true)
  @Mapping(target = "dirty", ignore = true)
  ParallelGatewayInstance map(
      ParallelGatewayInstanceDTO source, @Context FlowElements flowElements);

  @Mapping(
      target = "flowNode",
      expression =
          "java((com.flomaestro.engine.pd.model.InclusiveGateway)flowElements.getFlowNode(source.getElementId()).orElseThrow())")
  @Mapping(target = "parentInstance", ignore = true)
  @Mapping(target = "dirty", ignore = true)
  InclusiveGatewayInstance map(
      InclusiveGatewayInstanceDTO source, @Context FlowElements flowElements);

  @Mapping(
      target = "flowNode",
      expression =
          "java((com.flomaestro.engine.pd.model.ExclusiveGateway)flowElements.getFlowNode(source.getElementId()).orElseThrow())")
  @Mapping(target = "parentInstance", ignore = true)
  @Mapping(target = "dirty", ignore = true)
  ExclusiveGatewayInstance map(
      ExclusiveGatewayInstanceDTO source, @Context FlowElements flowElements);

  @Mapping(
      target = "flowNode",
      expression =
          "java((com.flomaestro.engine.pd.model.BoundaryEvent)flowElements.getFlowNode(source.getElementId()).orElseThrow())")
  @Mapping(target = "parentInstance", ignore = true)
  @Mapping(target = "dirty", ignore = true)
  @Mapping(target = "stateChanged", ignore = true)
  @Mapping(target = "wasWaiting", ignore = true)
  @Mapping(target = "wasNew", ignore = true)
  BoundaryEventInstance map(BoundaryEventInstanceDTO source, @Context FlowElements flowElements);

  @Mapping(
      target = "flowNode",
      expression =
          "java((com.flomaestro.engine.pd.model.StartEvent)flowElements.getFlowNode(source.getElementId()).orElseThrow())")
  @Mapping(target = "parentInstance", ignore = true)
  @Mapping(target = "dirty", ignore = true)
  @Mapping(target = "stateChanged", ignore = true)
  @Mapping(target = "wasWaiting", ignore = true)
  @Mapping(target = "wasNew", ignore = true)
  StartEventInstance map(StartEventInstanceDTO source, @Context FlowElements flowElements);

  @Mapping(
      target = "flowNode",
      expression =
          "java((com.flomaestro.engine.pd.model.IntermediateCatchEvent)flowElements.getFlowNode(source.getElementId()).orElseThrow())")
  @Mapping(target = "parentInstance", ignore = true)
  @Mapping(target = "dirty", ignore = true)
  @Mapping(target = "stateChanged", ignore = true)
  @Mapping(target = "wasWaiting", ignore = true)
  @Mapping(target = "wasNew", ignore = true)
  IntermediateCatchEventInstance map(
      IntermediateCatchEventInstanceDTO source, @Context FlowElements flowElements);

  @Mapping(
      target = "flowNode",
      expression =
          "java((com.flomaestro.engine.pd.model.EndEvent)flowElements.getFlowNode(source.getElementId()).orElseThrow())")
  @Mapping(target = "parentInstance", ignore = true)
  @Mapping(target = "dirty", ignore = true)
  EndEventInstance map(EndEventInstanceDTO source, @Context FlowElements flowElements);

  @Mapping(
      target = "flowNode",
      expression =
          "java((com.flomaestro.engine.pd.model.IntermediateThrowEvent)flowElements.getFlowNode(source.getElementId()).orElseThrow())")
  @Mapping(target = "parentInstance", ignore = true)
  @Mapping(target = "dirty", ignore = true)
  IntermediateThrowEventInstance map(
      IntermediateThrowEventInstanceDTO source, @Context FlowElements flowElements);

  @Mapping(
      target = "flowNode",
      expression =
          "java((com.flomaestro.engine.pd.model.ServiceTask)flowElements.getFlowNode(source.getElementId()).orElseThrow())")
  @Mapping(target = "parentInstance", ignore = true)
  @Mapping(target = "dirty", ignore = true)
  @Mapping(target = "stateChanged", ignore = true)
  @Mapping(target = "wasWaiting", ignore = true)
  @Mapping(target = "wasNew", ignore = true)
  ServiceTaskInstance map(ServiceTaskInstanceDTO source, @Context FlowElements flowElements);

  @Mapping(
      target = "flowNode",
      expression =
          "java((com.flomaestro.engine.pd.model.SendTask)flowElements.getFlowNode(source.getElementId()).orElseThrow())")
  @Mapping(target = "parentInstance", ignore = true)
  @Mapping(target = "dirty", ignore = true)
  @Mapping(target = "stateChanged", ignore = true)
  @Mapping(target = "wasWaiting", ignore = true)
  @Mapping(target = "wasNew", ignore = true)
  SendTaskInstance map(SendTaskInstanceDTO source, @Context FlowElements flowElements);

  @Mapping(
      target = "flowNode",
      expression =
          "java((com.flomaestro.engine.pd.model.ReceiveTask)flowElements.getFlowNode(source.getElementId()).orElseThrow())")
  @Mapping(target = "parentInstance", ignore = true)
  @Mapping(target = "dirty", ignore = true)
  @Mapping(target = "stateChanged", ignore = true)
  @Mapping(target = "wasWaiting", ignore = true)
  @Mapping(target = "wasNew", ignore = true)
  ReceiveTaskInstance map(ReceiveTaskInstanceDTO source, @Context FlowElements flowElements);

  @Mapping(
      target = "flowNode",
      expression =
          "java((com.flomaestro.engine.pd.model.SubProcess)flowElements.getFlowNode(source.getElementId()).orElseThrow())")
  @Mapping(target = "parentInstance", ignore = true)
  @Mapping(target = "dirty", ignore = true)
  @Mapping(target = "stateChanged", ignore = true)
  @Mapping(target = "wasWaiting", ignore = true)
  @Mapping(target = "wasNew", ignore = true)
  @Mapping(
      target = "flowNodeInstances",
      expression =
          "java(flowNodeInstancesDTOToFlowNodeInstances( source.getFlowNodeInstances(), getChildElements(source, flowElements)))")
  SubProcessInstance map(SubProcessInstanceDTO source, @Context FlowElements flowElements);

  @Mapping(
      target = "flowNode",
      expression =
          "java((com.flomaestro.engine.pd.model.CallActivity)flowElements.getFlowNode(source.getElementId()).orElseThrow())")
  @Mapping(target = "parentInstance", ignore = true)
  @Mapping(target = "dirty", ignore = true)
  @Mapping(target = "stateChanged", ignore = true)
  @Mapping(target = "wasWaiting", ignore = true)
  @Mapping(target = "wasNew", ignore = true)
  CallActivityInstance map(CallActivityInstanceDTO source, @Context FlowElements flowElements);

  @Mapping(
      target = "flowNode",
      expression =
          "java((com.flomaestro.engine.pd.model.Activity)flowElements.getFlowNode(source.getElementId()).orElseThrow())")
  @Mapping(target = "parentInstance", ignore = true)
  @Mapping(target = "dirty", ignore = true)
  @Mapping(target = "stateChanged", ignore = true)
  @Mapping(target = "flowNodeInstances.stateChanged", ignore = true)
  @Mapping(target = "flowNodeInstances.instances", ignore = true)
  @Mapping(target = "flowNodeInstances.parentFlowNodeInstances", ignore = true)
  @Mapping(target = "wasWaiting", ignore = true)
  @Mapping(target = "wasNew", ignore = true)
  MultiInstanceInstance map(MultiInstanceInstanceDTO source, @Context FlowElements flowElements);

  @Mapping(
      target = "flowNode",
      expression =
          "java((com.flomaestro.engine.pd.model.Task)flowElements.getFlowNode(source.getElementId()).orElseThrow())")
  @Mapping(target = "parentInstance", ignore = true)
  @Mapping(target = "wasWaiting", ignore = true)
  @Mapping(target = "stateChanged", ignore = true)
  @Mapping(target = "dirty", ignore = true)
  @Mapping(target = "wasNew", ignore = true)
  TaskInstance map(TaskInstanceDTO source, @Context FlowElements flowElements);

  default FlowElements getChildElements(SubProcessInstanceDTO source, FlowElements flowElements) {
    FlowNode flowNode = flowElements.getFlowNode(source.getElementId()).orElseThrow();
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
  @SubclassMapping(target = ReceiveTaskInstance.class, source = ReceiveTaskInstanceDTO.class)
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
  FlowNodeInstance<?> map(FlowNodeInstanceDTO source, @Context FlowElements flowElements);

  @Mapping(target = "flowNodeInstances.stateChanged", ignore = true)
  @Mapping(target = "flowNodeInstances.instances", ignore = true)
  @Mapping(target = "flowNodeInstances.parentFlowNodeInstances", ignore = true)
  ProcessInstance map(ProcessInstanceDTO source, @Context FlowElements flowElements);

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
  @SubclassMapping(source = ReceiveTaskInstance.class, target = ReceiveTaskInstanceDTO.class)
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
  @Mapping(target = "elementId", source = "flowNode.id")
  @Mapping(target = "parentElementInstanceId", source = "parentInstance.elementInstanceId")
  FlowNodeInstanceDTO map(FlowNodeInstance source);

  ProcessInstanceDTO map(ProcessInstance source);

  IoVariableMappingDTO map(IoVariableMapping value);

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
