package nl.qunit.bpmnmeister.pd.xml;

import nl.qunit.bpmnmeister.bpmn.TSendTask;
import nl.qunit.bpmnmeister.pd.model.v_1_0_0.InputOutputMappingDTO;
import nl.qunit.bpmnmeister.pd.model.v_1_0_0.LoopCharacteristicsDTO;
import nl.qunit.bpmnmeister.pd.model.v_1_0_0.SendTaskDTO;

public interface SendTaskMapper extends Mapper {
  String DEFAULT_RETRIES = "3";

  SendTaskDTO map(
      TSendTask serviceTask,
      String parentId,
      LoopCharacteristicsDTO loopCharacteristics,
      InputOutputMappingDTO ioMapping);
}
