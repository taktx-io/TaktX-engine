/*
 *
 *  * TaktX - A high-performance BPMN engine
 *  * Copyright (c) 2025 TaktX B.V. All rights reserved.
 *  * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 *  * Free use is permitted with up to 3 Kafka partitions. See LICENSE file for details.
 *  * For commercial use or more partitions and features, contact [info@taktx.io] or [https://www.taktx.io/contact].
 *
 */

package io.taktx.engine.pi;

import io.taktx.dto.v_1_0_0.BaseElementDTO;
import io.taktx.dto.v_1_0_0.BoundaryEventDTO;
import io.taktx.dto.v_1_0_0.CallActivityDTO;
import io.taktx.dto.v_1_0_0.EndEventDTO;
import io.taktx.dto.v_1_0_0.ErrorEventDefinitionDTO;
import io.taktx.dto.v_1_0_0.EscalationEventDefinitionDTO;
import io.taktx.dto.v_1_0_0.EventDefinitionDTO;
import io.taktx.dto.v_1_0_0.ExclusiveGatewayDTO;
import io.taktx.dto.v_1_0_0.FlowElementDTO;
import io.taktx.dto.v_1_0_0.FlowElementsDTO;
import io.taktx.dto.v_1_0_0.FlowNodeDTO;
import io.taktx.dto.v_1_0_0.InclusiveGatewayDTO;
import io.taktx.dto.v_1_0_0.IntermediateCatchEventDTO;
import io.taktx.dto.v_1_0_0.IntermediateThrowEventDTO;
import io.taktx.dto.v_1_0_0.IoVariableMappingDTO;
import io.taktx.dto.v_1_0_0.LinkEventDefinitionDTO;
import io.taktx.dto.v_1_0_0.MessageEventDefinitionDTO;
import io.taktx.dto.v_1_0_0.ParallelGatewayDTO;
import io.taktx.dto.v_1_0_0.ReceiveTaskDTO;
import io.taktx.dto.v_1_0_0.SendTaskDTO;
import io.taktx.dto.v_1_0_0.SequenceFlowDTO;
import io.taktx.dto.v_1_0_0.ServiceTaskDTO;
import io.taktx.dto.v_1_0_0.StartEventDTO;
import io.taktx.dto.v_1_0_0.SubProcessDTO;
import io.taktx.dto.v_1_0_0.TaskDTO;
import io.taktx.dto.v_1_0_0.TimerEventDefinitionDTO;
import io.taktx.engine.pd.model.BaseElement;
import io.taktx.engine.pd.model.BoundaryEvent;
import io.taktx.engine.pd.model.CallActivity;
import io.taktx.engine.pd.model.EndEvent;
import io.taktx.engine.pd.model.ErrorEventDefinition;
import io.taktx.engine.pd.model.EscalationEventDefinition;
import io.taktx.engine.pd.model.EventDefinition;
import io.taktx.engine.pd.model.ExclusiveGateway;
import io.taktx.engine.pd.model.FlowElement;
import io.taktx.engine.pd.model.FlowElements;
import io.taktx.engine.pd.model.FlowNode;
import io.taktx.engine.pd.model.InclusiveGateway;
import io.taktx.engine.pd.model.IntermediateCatchEvent;
import io.taktx.engine.pd.model.IntermediateThrowEvent;
import io.taktx.engine.pd.model.IoVariableMapping;
import io.taktx.engine.pd.model.LinkEventDefinition;
import io.taktx.engine.pd.model.MessageEventDefinition;
import io.taktx.engine.pd.model.ParallelGateway;
import io.taktx.engine.pd.model.ReceiveTask;
import io.taktx.engine.pd.model.SendTask;
import io.taktx.engine.pd.model.SequenceFlow;
import io.taktx.engine.pd.model.ServiceTask;
import io.taktx.engine.pd.model.StartEvent;
import io.taktx.engine.pd.model.SubProcess;
import io.taktx.engine.pd.model.Task;
import io.taktx.engine.pd.model.TimerEventDefinition;
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
  @Mapping(target = "index", ignore = true)
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
