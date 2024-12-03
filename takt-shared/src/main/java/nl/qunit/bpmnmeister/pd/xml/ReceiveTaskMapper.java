package nl.qunit.bpmnmeister.pd.xml;

import nl.qunit.bpmnmeister.bpmn.TReceiveTask;
import nl.qunit.bpmnmeister.pd.model.InputOutputMappingDTO;
import nl.qunit.bpmnmeister.pd.model.LoopCharacteristicsDTO;
import nl.qunit.bpmnmeister.pd.model.ReceiveTaskDTO;

public interface ReceiveTaskMapper extends Mapper {

  ReceiveTaskDTO map(
      TReceiveTask serviceTask,
      String parentId,
      LoopCharacteristicsDTO loopCharacteristics,
      InputOutputMappingDTO ioMapping);
}
