package nl.qunit.bpmnmeister.pd.xml;

import nl.qunit.bpmnmeister.bpmn.TSendTask;
import nl.qunit.bpmnmeister.pd.model.InputOutputMapping;
import nl.qunit.bpmnmeister.pd.model.LoopCharacteristics;
import nl.qunit.bpmnmeister.pd.model.SendTask;

public interface SendTaskMapper extends Mapper {
  String DEFAULT_RETRIES = "3";

  SendTask map(TSendTask serviceTask, String parentId, LoopCharacteristics loopCharacteristics,
      InputOutputMapping ioMapping);
}
