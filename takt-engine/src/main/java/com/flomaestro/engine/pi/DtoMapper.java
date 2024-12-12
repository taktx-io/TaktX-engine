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
import com.flomaestro.engine.pd.model.InclusiveGateway;
import com.flomaestro.engine.pd.model.IntermediateCatchEvent;
import com.flomaestro.engine.pd.model.IntermediateThrowEvent;
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
import com.flomaestro.takt.dto.v_1_0_0.InclusiveGatewayDTO;
import com.flomaestro.takt.dto.v_1_0_0.IntermediateCatchEventDTO;
import com.flomaestro.takt.dto.v_1_0_0.IntermediateThrowEventDTO;
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
