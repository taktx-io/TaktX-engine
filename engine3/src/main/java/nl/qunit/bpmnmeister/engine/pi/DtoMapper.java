package nl.qunit.bpmnmeister.engine.pi;

import java.lang.reflect.InvocationTargetException;
import nl.qunit.bpmnmeister.pd.model.BaseElement2;
import nl.qunit.bpmnmeister.pd.model.BaseElementDTO;
import nl.qunit.bpmnmeister.pd.model.CallActivity2;
import nl.qunit.bpmnmeister.pd.model.CallActivityDTO;
import nl.qunit.bpmnmeister.pd.model.EndEvent2;
import nl.qunit.bpmnmeister.pd.model.EndEventDTO;
import nl.qunit.bpmnmeister.pd.model.FlowElement2;
import nl.qunit.bpmnmeister.pd.model.FlowElementDTO;
import nl.qunit.bpmnmeister.pd.model.FlowElements2;
import nl.qunit.bpmnmeister.pd.model.FlowElementsDTO;
import nl.qunit.bpmnmeister.pd.model.SendTask2;
import nl.qunit.bpmnmeister.pd.model.SendTaskDTO;
import nl.qunit.bpmnmeister.pd.model.SequenceFlow2;
import nl.qunit.bpmnmeister.pd.model.SequenceFlowDTO;
import nl.qunit.bpmnmeister.pd.model.ServiceTask2;
import nl.qunit.bpmnmeister.pd.model.ServiceTaskDTO;
import nl.qunit.bpmnmeister.pd.model.StartEvent2;
import nl.qunit.bpmnmeister.pd.model.StartEventDTO;
import nl.qunit.bpmnmeister.pd.model.SubProcess2;
import nl.qunit.bpmnmeister.pd.model.SubProcessDTO;
import nl.qunit.bpmnmeister.pd.model.Task2;
import nl.qunit.bpmnmeister.pd.model.TaskDTO;
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
  FlowElements2 getFlowElements(FlowElementsDTO flowElements);

  @SubclassMapping(source = StartEventDTO.class, target = StartEvent2.class)
  @SubclassMapping(source = EndEventDTO.class, target = EndEvent2.class)
  @SubclassMapping(source = ServiceTaskDTO.class, target = ServiceTask2.class)
  @SubclassMapping(source = SendTaskDTO.class, target = SendTask2.class)
  @SubclassMapping(source = TaskDTO.class, target = Task2.class)
  @SubclassMapping(source = SubProcessDTO.class, target = SubProcess2.class)
  @SubclassMapping(source = SequenceFlowDTO.class, target = SequenceFlow2.class)
  @SubclassMapping(source = CallActivityDTO.class, target = CallActivity2.class)
  @Mapping(target = "parentElement", ignore = true)
  FlowElement2 getFlowElement(FlowElementDTO flowElements);

  @ObjectFactory
  default <T extends BaseElement2> T resolveEquipment(
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
