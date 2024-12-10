package nl.qunit.bpmnmeister.pd.xml;

import nl.qunit.bpmnmeister.bpmn.TReceiveTask;
import nl.qunit.bpmnmeister.pd.model.v_1_0_0.InputOutputMappingDTO;
import nl.qunit.bpmnmeister.pd.model.v_1_0_0.LoopCharacteristicsDTO;
import nl.qunit.bpmnmeister.pd.model.v_1_0_0.ReceiveTaskDTO;

public interface ReceiveTaskMapper extends Mapper {

  ReceiveTaskDTO map(
      TReceiveTask serviceTask,
      String parentId,
      LoopCharacteristicsDTO loopCharacteristics,
      InputOutputMappingDTO ioMapping);
}
