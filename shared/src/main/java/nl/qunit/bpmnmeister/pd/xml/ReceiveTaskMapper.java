package nl.qunit.bpmnmeister.pd.xml;

import nl.qunit.bpmnmeister.bpmn.TReceiveTask;
import nl.qunit.bpmnmeister.pd.model.LoopCharacteristics;
import nl.qunit.bpmnmeister.pd.model.ReceiveTask;

public interface ReceiveTaskMapper extends Mapper {

  ReceiveTask map(
      TReceiveTask serviceTask, String parentId, LoopCharacteristics loopCharacteristics);
}
