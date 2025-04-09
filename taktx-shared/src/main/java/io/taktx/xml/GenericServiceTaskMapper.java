package io.taktx.xml;

import io.taktx.bpmn.TServiceTask;
import io.taktx.dto.InputOutputMappingDTO;
import io.taktx.dto.LoopCharacteristicsDTO;
import io.taktx.dto.ServiceTaskDTO;
import java.util.Map;

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
