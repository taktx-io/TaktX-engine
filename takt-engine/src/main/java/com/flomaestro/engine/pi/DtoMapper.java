package com.flomaestro.engine.pi;

import com.flomaestro.engine.pd.model.BaseElement;
import com.flomaestro.engine.pd.model.BoundaryEvent;
import com.flomaestro.engine.pd.model.CallActivity;
import com.flomaestro.engine.pd.model.EndEvent;
import com.flomaestro.engine.pd.model.ErrorEventDefinition;
import com.flomaestro.engine.pd.model.EscalationEventDefinition;
import com.flomaestro.engine.pd.model.EventDefinition;
import com.flomaestro.engine.pd.model.ExclusiveGateway;
import com.flomaestro.engine.pd.model.FlowElement;
import com.flomaestro.engine.pd.model.FlowElements;
import com.flomaestro.engine.pd.model.FlowNode;
import com.flomaestro.engine.pd.model.InclusiveGateway;
import com.flomaestro.engine.pd.model.IntermediateCatchEvent;
import com.flomaestro.engine.pd.model.IntermediateThrowEvent;
import com.flomaestro.engine.pd.model.IoVariableMapping;
import com.flomaestro.engine.pd.model.LinkEventDefinition;
import com.flomaestro.engine.pd.model.MessageEventDefinition;
import com.flomaestro.engine.pd.model.ParallelGateway;
import com.flomaestro.engine.pd.model.ReceiveTask;
import com.flomaestro.engine.pd.model.SendTask;
import com.flomaestro.engine.pd.model.SequenceFlow;
import com.flomaestro.engine.pd.model.ServiceTask;
import com.flomaestro.engine.pd.model.StartEvent;
import com.flomaestro.engine.pd.model.SubProcess;
import com.flomaestro.engine.pd.model.Task;
import com.flomaestro.engine.pd.model.TimerEventDefinition;
import com.flomaestro.takt.dto.v_1_0_0.BaseElementDTO;
import com.flomaestro.takt.dto.v_1_0_0.BoundaryEventDTO;
import com.flomaestro.takt.dto.v_1_0_0.CallActivityDTO;
import com.flomaestro.takt.dto.v_1_0_0.EndEventDTO;
import com.flomaestro.takt.dto.v_1_0_0.ErrorEventDefinitionDTO;
import com.flomaestro.takt.dto.v_1_0_0.EscalationEventDefinitionDTO;
import com.flomaestro.takt.dto.v_1_0_0.EventDefinitionDTO;
import com.flomaestro.takt.dto.v_1_0_0.ExclusiveGatewayDTO;
import com.flomaestro.takt.dto.v_1_0_0.FlowElementDTO;
import com.flomaestro.takt.dto.v_1_0_0.FlowElementsDTO;
import com.flomaestro.takt.dto.v_1_0_0.FlowNodeDTO;
import com.flomaestro.takt.dto.v_1_0_0.InclusiveGatewayDTO;
import com.flomaestro.takt.dto.v_1_0_0.IntermediateCatchEventDTO;
import com.flomaestro.takt.dto.v_1_0_0.IntermediateThrowEventDTO;
import com.flomaestro.takt.dto.v_1_0_0.IoVariableMappingDTO;
import com.flomaestro.takt.dto.v_1_0_0.LinkEventDefinitionDTO;
import com.flomaestro.takt.dto.v_1_0_0.MessageEventDefinitionDTO;
import com.flomaestro.takt.dto.v_1_0_0.ParallelGatewayDTO;
import com.flomaestro.takt.dto.v_1_0_0.ReceiveTaskDTO;
import com.flomaestro.takt.dto.v_1_0_0.SendTaskDTO;
import com.flomaestro.takt.dto.v_1_0_0.SequenceFlowDTO;
import com.flomaestro.takt.dto.v_1_0_0.ServiceTaskDTO;
import com.flomaestro.takt.dto.v_1_0_0.StartEventDTO;
import com.flomaestro.takt.dto.v_1_0_0.SubProcessDTO;
import com.flomaestro.takt.dto.v_1_0_0.TaskDTO;
import com.flomaestro.takt.dto.v_1_0_0.TimerEventDefinitionDTO;
import java.lang.reflect.InvocationTargetException;
import java.util.Set;
import org.mapstruct.Builder;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ObjectFactory;
import org.mapstruct.SubclassMapping;
import org.mapstruct.TargetType;

@Mapper(componentModel = "jakarta", builder = @Builder())
public interface DtoMapper {
  @Mapping(target = "startEvents", ignore = true)
  @Mapping(target = "flowNodes", ignore = true)
  @Mapping(target = "sequenceFlows", ignore = true)
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

  @Mapping(target = "attachedActivity", ignore = true)
  @Mapping(target = "parentElement", ignore = true)
  BoundaryEvent map(BoundaryEventDTO boundaryEventDTO);

  @Mapping(target = "boundaryEvents", ignore = true)
  @Mapping(target = "parentElement", ignore = true)
  ServiceTask map(ServiceTaskDTO serviceTask);

  @Mapping(target = "boundaryEvents", ignore = true)
  @Mapping(target = "parentElement", ignore = true)
  SendTask map(SendTaskDTO sendTask);

  @Mapping(target = "boundaryEvents", ignore = true)
  @Mapping(target = "parentElement", ignore = true)
  @Mapping(target = "referencedMessage", ignore = true)
  ReceiveTask map(ReceiveTaskDTO receiveTask);

  @Mapping(target = "boundaryEvents", ignore = true)
  @Mapping(target = "parentElement", ignore = true)
  Task map(TaskDTO task);

  @Mapping(target = "boundaryEvents", ignore = true)
  @Mapping(target = "parentElement", ignore = true)
  SubProcess map(SubProcessDTO subProcess);

  @Mapping(target = "boundaryEvents", ignore = true)
  @Mapping(target = "parentElement", ignore = true)
  CallActivity map(CallActivityDTO callActivity);

  @Mapping(target = "referencedError", ignore = true)
  ErrorEventDefinition map(ErrorEventDefinitionDTO errorEventDefinitionDTO);

  @Mapping(target = "referencedEscalation", ignore = true)
  EscalationEventDefinition map(EscalationEventDefinitionDTO escalationEventDefinitionDTO);

  @Mapping(target = "referencedMessage", ignore = true)
  MessageEventDefinition map(MessageEventDefinitionDTO messageEventDefinition);

  @Mapping(target = "sourceNode", ignore = true)
  @Mapping(target = "targetNode", ignore = true)
  @Mapping(target = "parentElement", ignore = true)
  SequenceFlow map(SequenceFlowDTO sequenceFlow);

  @Mapping(target = "incomingSequenceFlows", ignore = true)
  @Mapping(target = "outGoingSequenceFlows", ignore = true)
  @Mapping(target = "parentElement", ignore = true)
  FlowNode map(FlowNodeDTO flowNode);

  @Mapping(target = "defaultSequenceFlow", ignore = true)
  @Mapping(target = "parentElement", ignore = true)
  ExclusiveGateway map(ExclusiveGatewayDTO gateway);

  @Mapping(target = "defaultSequenceFlow", ignore = true)
  @Mapping(target = "parentElement", ignore = true)
  ParallelGateway map(ParallelGatewayDTO gateway);

  @Mapping(target = "defaultSequenceFlow", ignore = true)
  @Mapping(target = "parentElement", ignore = true)
  InclusiveGateway map(InclusiveGatewayDTO gateway);

  @SubclassMapping(source = MessageEventDefinitionDTO.class, target = MessageEventDefinition.class)
  @SubclassMapping(source = TimerEventDefinitionDTO.class, target = TimerEventDefinition.class)
  @SubclassMapping(source = LinkEventDefinitionDTO.class, target = LinkEventDefinition.class)
  @SubclassMapping(
      source = EscalationEventDefinitionDTO.class,
      target = EscalationEventDefinition.class)
  @SubclassMapping(source = ErrorEventDefinitionDTO.class, target = ErrorEventDefinition.class)
  EventDefinition map(EventDefinitionDTO eventDefinition);

  @Mapping(target = "parentId", ignore = true)
  TimerEventDefinitionDTO map(TimerEventDefinition eventDefinition);

  @ObjectFactory
  default <T extends BaseElement> T resolveEquipment(
      BaseElementDTO ignoredSourceDto, @TargetType Class<T> type) {
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

  IoVariableMappingDTO toDto(IoVariableMapping ioVariableMappings);

  IoVariableMapping map(IoVariableMappingDTO ioVariableMappings);

  Set<IoVariableMappingDTO> toDto(Set<IoVariableMapping> ioVariableMappings);

  Set<IoVariableMapping> map(Set<IoVariableMappingDTO> ioVariableMappings);
}
