package nl.qunit.bpmnmeister.engine.pi;

import java.lang.reflect.InvocationTargetException;
import java.util.Map;
import java.util.UUID;
import nl.qunit.bpmnmeister.engine.pd.model.FlowElements;
import nl.qunit.bpmnmeister.engine.pd.model.FlowNode;
import nl.qunit.bpmnmeister.engine.pd.model.SubProcess;
import nl.qunit.bpmnmeister.engine.pi.model.ActivityInstance;
import nl.qunit.bpmnmeister.engine.pi.model.BoundaryEventInstance;
import nl.qunit.bpmnmeister.engine.pi.model.CallActivityInstance;
import nl.qunit.bpmnmeister.engine.pi.model.EndEventInstance;
import nl.qunit.bpmnmeister.engine.pi.model.ExclusiveGatewayInstance;
import nl.qunit.bpmnmeister.engine.pi.model.FLowNodeInstance;
import nl.qunit.bpmnmeister.engine.pi.model.FlowNodeInstances;
import nl.qunit.bpmnmeister.engine.pi.model.InclusiveGatewayInstance;
import nl.qunit.bpmnmeister.engine.pi.model.IntermediateCatchEventInstance;
import nl.qunit.bpmnmeister.engine.pi.model.IntermediateThrowEventInstance;
import nl.qunit.bpmnmeister.engine.pi.model.MultiInstanceInstance;
import nl.qunit.bpmnmeister.engine.pi.model.ParallelGatewayInstance;
import nl.qunit.bpmnmeister.engine.pi.model.ProcessInstance;
import nl.qunit.bpmnmeister.engine.pi.model.ReceiveTaskInstance;
import nl.qunit.bpmnmeister.engine.pi.model.SendTaskInstance;
import nl.qunit.bpmnmeister.engine.pi.model.ServiceTaskInstance;
import nl.qunit.bpmnmeister.engine.pi.model.StartEventInstance;
import nl.qunit.bpmnmeister.engine.pi.model.SubProcessInstance;
import nl.qunit.bpmnmeister.engine.pi.model.TaskInstance;
import nl.qunit.bpmnmeister.engine.pi.model.WithFlowNodeInstances;
import nl.qunit.bpmnmeister.pi.state.BoundaryEventInstanceDTO;
import nl.qunit.bpmnmeister.pi.state.CallActivityInstanceDTO;
import nl.qunit.bpmnmeister.pi.state.EndEventInstanceDTO;
import nl.qunit.bpmnmeister.pi.state.ExclusiveGatewayInstanceDTO;
import nl.qunit.bpmnmeister.pi.state.FlowNodeInstanceDTO;
import nl.qunit.bpmnmeister.pi.state.FlowNodeInstancesDTO;
import nl.qunit.bpmnmeister.pi.state.InclusiveGatewayInstanceDTO;
import nl.qunit.bpmnmeister.pi.state.IntermediateCatchEventInstanceDTO;
import nl.qunit.bpmnmeister.pi.state.IntermediateThrowEventInstanceDTO;
import nl.qunit.bpmnmeister.pi.state.MultiInstanceInstanceDTO;
import nl.qunit.bpmnmeister.pi.state.ParallelGatewayInstanceDTO;
import nl.qunit.bpmnmeister.pi.state.ProcessInstanceDTO;
import nl.qunit.bpmnmeister.pi.state.ReceiveTaskInstanceDTO;
import nl.qunit.bpmnmeister.pi.state.SendTaskInstanceDTO;
import nl.qunit.bpmnmeister.pi.state.ServiceTaskInstanceDTO;
import nl.qunit.bpmnmeister.pi.state.StartEventInstanceDTO;
import nl.qunit.bpmnmeister.pi.state.SubProcessInstanceDTO;
import nl.qunit.bpmnmeister.pi.state.TaskInstanceDTO;
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
          "java((nl.qunit.bpmnmeister.engine.pd.model.ParallelGateway)flowElements.getFlowNode(source.getElementId()).orElseThrow())")
  @Mapping(target = "parentInstance", ignore = true)
  ParallelGatewayInstance map(
      ParallelGatewayInstanceDTO source, @Context FlowElements flowElements);

  @Mapping(
      target = "flowNode",
      expression =
          "java((nl.qunit.bpmnmeister.engine.pd.model.InclusiveGateway)flowElements.getFlowNode(source.getElementId()).orElseThrow())")
  @Mapping(target = "parentInstance", ignore = true)
  InclusiveGatewayInstance map(
      InclusiveGatewayInstanceDTO source, @Context FlowElements flowElements);

  @Mapping(
      target = "flowNode",
      expression =
          "java((nl.qunit.bpmnmeister.engine.pd.model.ExclusiveGateway)flowElements.getFlowNode(source.getElementId()).orElseThrow())")
  @Mapping(target = "parentInstance", ignore = true)
  ExclusiveGatewayInstance map(
      ExclusiveGatewayInstanceDTO source, @Context FlowElements flowElements);

  @Mapping(
      target = "flowNode",
      expression =
          "java((nl.qunit.bpmnmeister.engine.pd.model.BoundaryEvent)flowElements.getFlowNode(source.getElementId()).orElseThrow())")
  @Mapping(target = "parentInstance", ignore = true)
  BoundaryEventInstance map(BoundaryEventInstanceDTO source, @Context FlowElements flowElements);

  @Mapping(
      target = "flowNode",
      expression =
          "java((nl.qunit.bpmnmeister.engine.pd.model.StartEvent)flowElements.getFlowNode(source.getElementId()).orElseThrow())")
  @Mapping(target = "parentInstance", ignore = true)
  StartEventInstance map(StartEventInstanceDTO source, @Context FlowElements flowElements);

  @Mapping(
      target = "flowNode",
      expression =
          "java((nl.qunit.bpmnmeister.engine.pd.model.IntermediateCatchEvent)flowElements.getFlowNode(source.getElementId()).orElseThrow())")
  @Mapping(target = "parentInstance", ignore = true)
  IntermediateCatchEventInstance map(
      IntermediateCatchEventInstanceDTO source, @Context FlowElements flowElements);

  @Mapping(
      target = "flowNode",
      expression =
          "java((nl.qunit.bpmnmeister.engine.pd.model.EndEvent)flowElements.getFlowNode(source.getElementId()).orElseThrow())")
  @Mapping(target = "parentInstance", ignore = true)
  EndEventInstance map(EndEventInstanceDTO source, @Context FlowElements flowElements);

  @Mapping(
      target = "flowNode",
      expression =
          "java((nl.qunit.bpmnmeister.engine.pd.model.IntermediateThrowEvent)flowElements.getFlowNode(source.getElementId()).orElseThrow())")
  @Mapping(target = "parentInstance", ignore = true)
  IntermediateThrowEventInstance map(
      IntermediateThrowEventInstanceDTO source, @Context FlowElements flowElements);

  @Mapping(
      target = "flowNode",
      expression =
          "java((nl.qunit.bpmnmeister.engine.pd.model.ServiceTask)flowElements.getFlowNode(source.getElementId()).orElseThrow())")
  @Mapping(target = "parentInstance", ignore = true)
  @Mapping(target = "attachedBoundaryEventInstances", ignore = true)
  ServiceTaskInstance map(ServiceTaskInstanceDTO source, @Context FlowElements flowElements);

  @Mapping(
      target = "flowNode",
      expression =
          "java((nl.qunit.bpmnmeister.engine.pd.model.SendTask)flowElements.getFlowNode(source.getElementId()).orElseThrow())")
  @Mapping(target = "parentInstance", ignore = true)
  @Mapping(target = "attachedBoundaryEventInstances", ignore = true)
  SendTaskInstance map(SendTaskInstanceDTO source, @Context FlowElements flowElements);

  @Mapping(
      target = "flowNode",
      expression =
          "java((nl.qunit.bpmnmeister.engine.pd.model.ReceiveTask)flowElements.getFlowNode(source.getElementId()).orElseThrow())")
  @Mapping(target = "parentInstance", ignore = true)
  @Mapping(target = "attachedBoundaryEventInstances", ignore = true)
  ReceiveTaskInstance map(ReceiveTaskInstanceDTO source, @Context FlowElements flowElements);

  @Mapping(
      target = "flowNode",
      expression =
          "java((nl.qunit.bpmnmeister.engine.pd.model.SubProcess)flowElements.getFlowNode(source.getElementId()).orElseThrow())")
  @Mapping(target = "parentInstance", ignore = true)
  @Mapping(target = "attachedBoundaryEventInstances", ignore = true)
  @Mapping(
      target = "flowNodeInstances",
      expression =
          "java(flowNodeInstancesDTOToFlowNodeInstances( source.getFlowNodeInstances(), getChildElements(source, flowElements)))")
  SubProcessInstance map(SubProcessInstanceDTO source, @Context FlowElements flowElements);

  @Mapping(
      target = "flowNode",
      expression =
          "java((nl.qunit.bpmnmeister.engine.pd.model.CallActivity)flowElements.getFlowNode(source.getElementId()).orElseThrow())")
  @Mapping(target = "parentInstance", ignore = true)
  @Mapping(target = "attachedBoundaryEventInstances", ignore = true)
  CallActivityInstance map(CallActivityInstanceDTO source, @Context FlowElements flowElements);

  @Mapping(
      target = "flowNode",
      expression =
          "java((nl.qunit.bpmnmeister.engine.pd.model.Activity)flowElements.getFlowNode(source.getElementId()).orElseThrow())")
  @Mapping(target = "parentInstance", ignore = true)
  @Mapping(target = "attachedBoundaryEventInstances", ignore = true)
  MultiInstanceInstance map(MultiInstanceInstanceDTO source, @Context FlowElements flowElements);

  @Mapping(
      target = "flowNode",
      expression =
          "java((nl.qunit.bpmnmeister.engine.pd.model.Task)flowElements.getFlowNode(source.getElementId()).orElseThrow())")
  @Mapping(target = "parentInstance", ignore = true)
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
  FLowNodeInstance map(FlowNodeInstanceDTO source, @Context FlowElements flowElements);

  ProcessInstance map(ProcessInstanceDTO source, @Context FlowElements flowElements);

  default ProcessInstance mapAndSetReferences(
      ProcessInstanceDTO source, FlowNodeInstances flowNodeInstances, FlowElements flowElements) {
    ProcessInstance processInstance = map(source, flowElements);
    processInstance.getFlowNodeInstances().getInstances().putAll(flowNodeInstances.getInstances());
    setParentInstances(processInstance.getFlowNodeInstances(), null, null);
    setAttachedBoundaryEventInstances(processInstance.getFlowNodeInstances());
    return processInstance;
  }

  default void setAttachedBoundaryEventInstances(FlowNodeInstances flowNodeInstances) {
    for (FLowNodeInstance<?> value : flowNodeInstances.getInstances().values()) {
      if (value instanceof ActivityInstance activityInstance) {
        activityInstance
            .getBoundaryEventIds()
            .forEach(
                boundaryEventId -> {
                  BoundaryEventInstance boundaryEventInstance =
                      (BoundaryEventInstance) flowNodeInstances.getInstances().get(boundaryEventId);
                  activityInstance.addBoundaryEvent(boundaryEventInstance);
                });
      }
      if (value instanceof WithFlowNodeInstances withFlowNodeInstances) {
        setAttachedBoundaryEventInstances(withFlowNodeInstances.getFlowNodeInstances());
      }
    }
  }

  private static void setParentInstances(
      FlowNodeInstances flowNodeInstances,
      FLowNodeInstance<?> parentInstance,
      FlowNodeInstances parentInstances) {
    flowNodeInstances.setParentFlowNodeInstances(parentInstances);
    for (FLowNodeInstance<?> value : flowNodeInstances.getInstances().values()) {
      value.setParentInstance(parentInstance);
      if (value instanceof WithFlowNodeInstances withFlowNodeInstances) {
        setParentInstances(withFlowNodeInstances.getFlowNodeInstances(), value, flowNodeInstances);
      }
    }
  }

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
  FlowNodeInstanceDTO map(FLowNodeInstance source);

  FlowNodeInstancesDTO map(FlowNodeInstances source);

  ProcessInstanceDTO map(ProcessInstance source);

  @ObjectFactory
  default <T extends FLowNodeInstance<?>> T resolveEquipment(
      FlowNodeInstanceDTO sourceDto, @TargetType Class<T> type) {
    return getNewInstance(type);
  }

  @ObjectFactory
  default <T extends FlowNodeInstanceDTO> T resolveEquipment(
      FLowNodeInstance<?> sourceDto, @TargetType Class<T> type) {
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

  Map<UUID, FlowNodeInstanceDTO> map(Map<UUID, FLowNodeInstance<?>> instances);
}
