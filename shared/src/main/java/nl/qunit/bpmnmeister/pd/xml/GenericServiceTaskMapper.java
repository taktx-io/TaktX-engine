package nl.qunit.bpmnmeister.pd.xml;

import java.util.Map;
import nl.qunit.bpmnmeister.bpmn.TServiceTask;
import nl.qunit.bpmnmeister.pd.model.InputOutputMappingDTO;
import nl.qunit.bpmnmeister.pd.model.LoopCharacteristicsDTO;
import nl.qunit.bpmnmeister.pd.model.ServiceTaskDTO;

public class GenericServiceTaskMapper implements ServiceTaskMapper {

  @Override
  public ServiceTaskDTO map(
      TServiceTask serviceTask,
      String parentId,
      LoopCharacteristicsDTO loopCharacteristics,
      InputOutputMappingDTO ioMapping) {
    return new ServiceTaskDTO(
        serviceTask.getId(),
        parentId,
        serviceTask.getId(),
        DEFAULT_RETRIES,
        mapQNameList(serviceTask.getIncoming()),
        mapQNameList(serviceTask.getOutgoing()),
        serviceTask.getImplementation(),
        loopCharacteristics,
        Map.of(),
        ioMapping);
  }
}
