package nl.qunit.bpmnmeister.pd.xml;

import java.util.Optional;
import nl.qunit.bpmnmeister.bpmn.CalledElement;
import nl.qunit.bpmnmeister.bpmn.TCallActivity;
import nl.qunit.bpmnmeister.pd.model.CallActivity;
import nl.qunit.bpmnmeister.pd.model.LoopCharacteristics;

public class ZeebeCallActivityMapper implements CallActivityMapper {

  @Override
  public CallActivity map(TCallActivity callActivity, String parentId, LoopCharacteristics loopCharacteristics) {
    Optional<CalledElement> optCalledElement = ExtensionElementHelper.extractExtensionElement(callActivity.getExtensionElements(), CalledElement.class);
    String calledElement =  optCalledElement.isEmpty() ? "" : optCalledElement.get().getProcessId();
    return new CallActivity(
        callActivity.getId(),
        parentId,
        mapQNameList(callActivity.getIncoming()),
        mapQNameList(callActivity.getOutgoing()),
        loopCharacteristics,
        calledElement);
  }
}
