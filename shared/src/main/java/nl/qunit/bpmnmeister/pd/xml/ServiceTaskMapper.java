package nl.qunit.bpmnmeister.pd.xml;

import nl.qunit.bpmnmeister.bpmn.TServiceTask;
import nl.qunit.bpmnmeister.pd.model.InputOutputMapping;
import nl.qunit.bpmnmeister.pd.model.LoopCharacteristics;
import nl.qunit.bpmnmeister.pd.model.ServiceTask;

public interface ServiceTaskMapper extends Mapper {
  String DEFAULT_RETRIES = "3";

  ServiceTask map(
      TServiceTask serviceTask, String parentId, LoopCharacteristics loopCharacteristics,
      InputOutputMapping ioMapping);
}
