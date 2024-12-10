package nl.qunit.bpmnmeister.pd.xml;

import nl.qunit.bpmnmeister.bpmn.TServiceTask;
import nl.qunit.bpmnmeister.pd.model.v_1_0_0.InputOutputMappingDTO;
import nl.qunit.bpmnmeister.pd.model.v_1_0_0.LoopCharacteristicsDTO;
import nl.qunit.bpmnmeister.pd.model.v_1_0_0.ServiceTaskDTO;

public interface ServiceTaskMapper extends Mapper {
  String DEFAULT_RETRIES = "3";

  ServiceTaskDTO map(
      TServiceTask serviceTask,
      String parentId,
      LoopCharacteristicsDTO loopCharacteristics,
      InputOutputMappingDTO ioMapping);
}
