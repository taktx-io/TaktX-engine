package com.flomaestro.takt.xml;

import com.flomaestro.bpmn.TServiceTask;
import com.flomaestro.takt.dto.v_1_0_0.InputOutputMappingDTO;
import com.flomaestro.takt.dto.v_1_0_0.LoopCharacteristicsDTO;
import com.flomaestro.takt.dto.v_1_0_0.ServiceTaskDTO;
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
