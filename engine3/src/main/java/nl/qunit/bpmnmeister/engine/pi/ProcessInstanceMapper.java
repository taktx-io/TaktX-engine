package nl.qunit.bpmnmeister.engine.pi;

import java.lang.reflect.InvocationTargetException;
import nl.qunit.bpmnmeister.pi.FlowNodeStates2;
import nl.qunit.bpmnmeister.pi.FlowNodeStatesDTO;
import nl.qunit.bpmnmeister.pi.ProcessInstance2;
import nl.qunit.bpmnmeister.pi.ProcessInstanceDTO;
import nl.qunit.bpmnmeister.pi.TaskInstance;
import nl.qunit.bpmnmeister.pi.instances.BoundaryEventInstance;
import nl.qunit.bpmnmeister.pi.instances.CallActivityInstance;
import nl.qunit.bpmnmeister.pi.instances.EndEventInstance;
import nl.qunit.bpmnmeister.pi.instances.FLowNodeInstance;
import nl.qunit.bpmnmeister.pi.instances.IntermediateCatchEventInstance;
import nl.qunit.bpmnmeister.pi.instances.IntermediateThrowEventInstance;
import nl.qunit.bpmnmeister.pi.instances.MultiInstanceInstance;
import nl.qunit.bpmnmeister.pi.instances.SendTaskInstance;
import nl.qunit.bpmnmeister.pi.instances.ServiceTaskInstance;
import nl.qunit.bpmnmeister.pi.instances.StartEventInstance;
import nl.qunit.bpmnmeister.pi.instances.SubProcessInstance;
import nl.qunit.bpmnmeister.pi.state.BoundaryEventState;
import nl.qunit.bpmnmeister.pi.state.CallActivityState;
import nl.qunit.bpmnmeister.pi.state.EndEventState;
import nl.qunit.bpmnmeister.pi.state.FlowNodeStateDTO;
import nl.qunit.bpmnmeister.pi.state.IntermediateCatchEventState;
import nl.qunit.bpmnmeister.pi.state.IntermediateThrowEventState;
import nl.qunit.bpmnmeister.pi.state.MultiInstanceState;
import nl.qunit.bpmnmeister.pi.state.SendTaskState;
import nl.qunit.bpmnmeister.pi.state.ServiceTaskState;
import nl.qunit.bpmnmeister.pi.state.StartEventState;
import nl.qunit.bpmnmeister.pi.state.SubProcessState;
import nl.qunit.bpmnmeister.pi.state.TaskState;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ObjectFactory;
import org.mapstruct.SubclassMapping;
import org.mapstruct.TargetType;

@Mapper(componentModel = "jakarta")
public interface ProcessInstanceMapper {
  @SubclassMapping(target = BoundaryEventInstance.class, source = BoundaryEventState.class)
  @SubclassMapping(target = StartEventInstance.class, source = StartEventState.class)
  @SubclassMapping(
      target = IntermediateCatchEventInstance.class,
      source = IntermediateCatchEventState.class)
  @SubclassMapping(target = EndEventInstance.class, source = EndEventState.class)
  @SubclassMapping(
      target = IntermediateThrowEventInstance.class,
      source = IntermediateThrowEventState.class)
  @SubclassMapping(target = ServiceTaskInstance.class, source = ServiceTaskState.class)
  @SubclassMapping(target = SendTaskInstance.class, source = SendTaskState.class)
  @SubclassMapping(target = TaskInstance.class, source = TaskState.class)
  @SubclassMapping(target = SubProcessInstance.class, source = SubProcessState.class)
  @SubclassMapping(target = CallActivityInstance.class, source = CallActivityState.class)
  @SubclassMapping(target = MultiInstanceInstance.class, source = MultiInstanceState.class)
  @Mapping(target = "parentInstance", ignore = true)
  FLowNodeInstance map(FlowNodeStateDTO sourceDto);

  @SubclassMapping(source = BoundaryEventInstance.class, target = BoundaryEventState.class)
  @SubclassMapping(source = StartEventInstance.class, target = StartEventState.class)
  @SubclassMapping(
      source = IntermediateCatchEventInstance.class,
      target = IntermediateCatchEventState.class)
  @SubclassMapping(source = EndEventInstance.class, target = EndEventState.class)
  @SubclassMapping(
      source = IntermediateThrowEventInstance.class,
      target = IntermediateThrowEventState.class)
  @SubclassMapping(source = ServiceTaskInstance.class, target = ServiceTaskState.class)
  @SubclassMapping(source = SendTaskInstance.class, target = SendTaskState.class)
  @SubclassMapping(source = TaskInstance.class, target = TaskState.class)
  @SubclassMapping(source = SubProcessInstance.class, target = SubProcessState.class)
  @SubclassMapping(source = CallActivityInstance.class, target = CallActivityState.class)
  @SubclassMapping(source = MultiInstanceInstance.class, target = MultiInstanceState.class)
  FlowNodeStateDTO map(FLowNodeInstance source);

  FlowNodeStatesDTO map(FlowNodeStates2 source);

  ProcessInstanceDTO map(ProcessInstance2 source);

  ProcessInstance2 map(ProcessInstanceDTO source);

  @ObjectFactory
  default <T extends FLowNodeInstance> T resolveEquipment(
      FlowNodeStateDTO sourceDto, @TargetType Class<T> type) {
    return getNewInstance(type);
  }

  @ObjectFactory
  default <T extends FlowNodeStateDTO> T resolveEquipment(
      FLowNodeInstance sourceDto, @TargetType Class<T> type) {
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
}
