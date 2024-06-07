package nl.qunit.bpmnmeister.pd.xml;

import nl.qunit.bpmnmeister.bpmn.TCallActivity;
import nl.qunit.bpmnmeister.pd.model.CallActivity;
import nl.qunit.bpmnmeister.pd.model.InputOutputMapping;
import nl.qunit.bpmnmeister.pd.model.LoopCharacteristics;

public interface CallActivityMapper extends Mapper {

  CallActivity map(
      TCallActivity callActivity, String parentId, LoopCharacteristics loopCharacteristics,
      InputOutputMapping ioMapping);
}
