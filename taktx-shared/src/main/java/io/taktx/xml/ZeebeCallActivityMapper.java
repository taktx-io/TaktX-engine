package io.taktx.xml;

import io.taktx.bpmn.CalledElement;
import io.taktx.bpmn.TCallActivity;
import io.taktx.dto.CallActivityDTO;
import io.taktx.dto.InputOutputMappingDTO;
import io.taktx.dto.LoopCharacteristicsDTO;
import java.util.Optional;

public class ZeebeCallActivityMapper implements CallActivityMapper {

  @Override
  public CallActivityDTO map(
      TCallActivity callActivity,
      String parentId,
      LoopCharacteristicsDTO loopCharacteristics,
      InputOutputMappingDTO ioMapping) {
    Optional<CalledElement> optCalledElement =
        ExtensionElementHelper.extractExtensionElement(
            callActivity.getExtensionElements(), CalledElement.class);
    String calledElementId = "";
    boolean propagateAllParentVariables = false;
    boolean propagateAllChildVariables = false;
    if (optCalledElement.isPresent()) {
      CalledElement calledElement = optCalledElement.get();
      if (calledElement.getProcessId().isEmpty()) {
        throw new IllegalArgumentException("Called element must not be empty");
      }
      calledElementId = calledElement.getProcessId();
      propagateAllParentVariables =
          calledElement.isPropagateAllParentVariables() != null
              ? calledElement.isPropagateAllParentVariables()
              : true;
      propagateAllChildVariables =
          calledElement.isPropagateAllChildVariables() != null
              ? calledElement.isPropagateAllChildVariables()
              : true;
    }

    return new CallActivityDTO(
        callActivity.getId(),
        parentId,
        mapQNameList(callActivity.getIncoming()),
        mapQNameList(callActivity.getOutgoing()),
        loopCharacteristics,
        calledElementId,
        propagateAllParentVariables,
        propagateAllChildVariables,
        ioMapping);
  }
}
