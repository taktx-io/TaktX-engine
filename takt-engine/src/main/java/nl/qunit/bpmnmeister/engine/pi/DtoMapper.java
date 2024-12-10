package nl.qunit.bpmnmeister.engine.pi;

import java.lang.reflect.InvocationTargetException;
import nl.qunit.bpmnmeister.engine.pd.model.BaseElement;
import nl.qunit.bpmnmeister.engine.pd.model.BoundaryEvent;
import nl.qunit.bpmnmeister.engine.pd.model.CallActivity;
import nl.qunit.bpmnmeister.engine.pd.model.EndEvent;
import nl.qunit.bpmnmeister.engine.pd.model.ErrorEventDefinition;
import nl.qunit.bpmnmeister.engine.pd.model.EscalationEventDefinition;
import nl.qunit.bpmnmeister.engine.pd.model.EventDefinition;
import nl.qunit.bpmnmeister.engine.pd.model.ExclusiveGateway;
import nl.qunit.bpmnmeister.engine.pd.model.FlowElement;
import nl.qunit.bpmnmeister.engine.pd.model.FlowElements;
import nl.qunit.bpmnmeister.engine.pd.model.InclusiveGateway;
import nl.qunit.bpmnmeister.engine.pd.model.IntermediateCatchEvent;
import nl.qunit.bpmnmeister.engine.pd.model.IntermediateThrowEvent;
import nl.qunit.bpmnmeister.engine.pd.model.LinkEventDefinition;
import nl.qunit.bpmnmeister.engine.pd.model.MessageEventDefinition;
import nl.qunit.bpmnmeister.engine.pd.model.ParallelGateway;
import nl.qunit.bpmnmeister.engine.pd.model.ReceiveTask;
import nl.qunit.bpmnmeister.engine.pd.model.SendTask;
import nl.qunit.bpmnmeister.engine.pd.model.SequenceFlow;
import nl.qunit.bpmnmeister.engine.pd.model.ServiceTask;
import nl.qunit.bpmnmeister.engine.pd.model.StartEvent;
import nl.qunit.bpmnmeister.engine.pd.model.SubProcess;
import nl.qunit.bpmnmeister.engine.pd.model.Task;
import nl.qunit.bpmnmeister.engine.pd.model.TimerEventDefinition;
import nl.qunit.bpmnmeister.pd.model.v_1_0_0.BaseElementDTO;
import nl.qunit.bpmnmeister.pd.model.v_1_0_0.BoundaryEventDTO;
import nl.qunit.bpmnmeister.pd.model.v_1_0_0.CallActivityDTO;
import nl.qunit.bpmnmeister.pd.model.v_1_0_0.EndEventDTO;
import nl.qunit.bpmnmeister.pd.model.v_1_0_0.ErrorEventDefinitionDTO;
import nl.qunit.bpmnmeister.pd.model.v_1_0_0.EscalationEventDefinitionDTO;
import nl.qunit.bpmnmeister.pd.model.v_1_0_0.EventDefinitionDTO;
import nl.qunit.bpmnmeister.pd.model.v_1_0_0.ExclusiveGatewayDTO;
import nl.qunit.bpmnmeister.pd.model.v_1_0_0.FlowElementDTO;
import nl.qunit.bpmnmeister.pd.model.v_1_0_0.FlowElementsDTO;
import nl.qunit.bpmnmeister.pd.model.v_1_0_0.InclusiveGatewayDTO;
import nl.qunit.bpmnmeister.pd.model.v_1_0_0.IntermediateCatchEventDTO;
import nl.qunit.bpmnmeister.pd.model.v_1_0_0.IntermediateThrowEventDTO;
import nl.qunit.bpmnmeister.pd.model.v_1_0_0.LinkEventDefinitionDTO;
import nl.qunit.bpmnmeister.pd.model.v_1_0_0.MessageEventDefinitionDTO;
import nl.qunit.bpmnmeister.pd.model.v_1_0_0.ParallelGatewayDTO;
import nl.qunit.bpmnmeister.pd.model.v_1_0_0.ReceiveTaskDTO;
import nl.qunit.bpmnmeister.pd.model.v_1_0_0.SendTaskDTO;
import nl.qunit.bpmnmeister.pd.model.v_1_0_0.SequenceFlowDTO;
import nl.qunit.bpmnmeister.pd.model.v_1_0_0.ServiceTaskDTO;
import nl.qunit.bpmnmeister.pd.model.v_1_0_0.StartEventDTO;
import nl.qunit.bpmnmeister.pd.model.v_1_0_0.SubProcessDTO;
import nl.qunit.bpmnmeister.pd.model.v_1_0_0.TaskDTO;
import nl.qunit.bpmnmeister.pd.model.v_1_0_0.TimerEventDefinitionDTO;
import org.mapstruct.Builder;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ObjectFactory;
import org.mapstruct.SubclassMapping;
import org.mapstruct.TargetType;

@Mapper(componentModel = "jakarta", builder = @Builder(disableBuilder = false))
public interface DtoMapper {
  @Mapping(target = "startEvents", ignore = true)
  @Mapping(target = "flowNodes", ignore = true)
  @Mapping(target = "sequenceFlows", ignore = true)
  @Mapping(target = "parentElements", ignore = true)
  FlowElements getFlowElements(FlowElementsDTO flowElements);

  @SubclassMapping(source = ExclusiveGatewayDTO.class, target = ExclusiveGateway.class)
  @SubclassMapping(source = ParallelGatewayDTO.class, target = ParallelGateway.class)
  @SubclassMapping(source = InclusiveGatewayDTO.class, target = InclusiveGateway.class)
  @SubclassMapping(source = StartEventDTO.class, target = StartEvent.class)
  @SubclassMapping(source = EndEventDTO.class, target = EndEvent.class)
  @SubclassMapping(source = ServiceTaskDTO.class, target = ServiceTask.class)
  @SubclassMapping(source = SendTaskDTO.class, target = SendTask.class)
  @SubclassMapping(source = ReceiveTaskDTO.class, target = ReceiveTask.class)
  @SubclassMapping(source = TaskDTO.class, target = Task.class)
  @SubclassMapping(source = SubProcessDTO.class, target = SubProcess.class)
  @SubclassMapping(source = SequenceFlowDTO.class, target = SequenceFlow.class)
  @SubclassMapping(source = CallActivityDTO.class, target = CallActivity.class)
  @SubclassMapping(source = BoundaryEventDTO.class, target = BoundaryEvent.class)
  @SubclassMapping(source = IntermediateCatchEventDTO.class, target = IntermediateCatchEvent.class)
  @SubclassMapping(source = IntermediateThrowEventDTO.class, target = IntermediateThrowEvent.class)
  @Mapping(target = "parentElement", ignore = true)
  FlowElement getFlowElement(FlowElementDTO flowElement);

  @SubclassMapping(source = MessageEventDefinitionDTO.class, target = MessageEventDefinition.class)
  @SubclassMapping(source = TimerEventDefinitionDTO.class, target = TimerEventDefinition.class)
  @SubclassMapping(source = LinkEventDefinitionDTO.class, target = LinkEventDefinition.class)
  @SubclassMapping(
      source = EscalationEventDefinitionDTO.class,
      target = EscalationEventDefinition.class)
  @SubclassMapping(source = ErrorEventDefinitionDTO.class, target = ErrorEventDefinition.class)
  EventDefinition map(EventDefinitionDTO eventDefinition);

  TimerEventDefinitionDTO map(TimerEventDefinition eventDefinition);

  @ObjectFactory
  default <T extends BaseElement> T resolveEquipment(
      BaseElementDTO sourceDto, @TargetType Class<T> type) {
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
