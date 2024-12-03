package nl.qunit.bpmnmeister.pd.xml;

import nl.qunit.bpmnmeister.bpmn.TServiceTask;
import nl.qunit.bpmnmeister.pd.model.InputOutputMappingDTO;
import nl.qunit.bpmnmeister.pd.model.LoopCharacteristicsDTO;
import nl.qunit.bpmnmeister.pd.model.ServiceTaskDTO;

public interface ServiceTaskMapper extends Mapper {
  String DEFAULT_RETRIES = "3";

  ServiceTaskDTO map(
      TServiceTask serviceTask,
      String parentId,
      LoopCharacteristicsDTO loopCharacteristics,
      InputOutputMappingDTO ioMapping);
}
