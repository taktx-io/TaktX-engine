package nl.qunit.bpmnmeister.pd.xml;

import nl.qunit.bpmnmeister.bpmn.TCallActivity;
import nl.qunit.bpmnmeister.pd.model.CallActivity;
import nl.qunit.bpmnmeister.pd.model.LoopCharacteristics;

public class GenericCallActivityMapper implements CallActivityMapper {

  @Override
  public CallActivity map(TCallActivity callActivity, String parentId,
      LoopCharacteristics loopCharacteristics) {
    return new CallActivity(
        callActivity.getId(),
        parentId,
        mapQNameList(callActivity.getIncoming()),
        mapQNameList(callActivity.getOutgoing()),
        loopCharacteristics,
        callActivity.getCalledElement().toString());
  }

}
