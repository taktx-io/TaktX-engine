package com.flomaestro.takt.xml;

import com.flomaestro.bpmn.CalledElement;
import com.flomaestro.bpmn.TCallActivity;
import com.flomaestro.takt.dto.v_1_0_0.CallActivityDTO;
import com.flomaestro.takt.dto.v_1_0_0.InputOutputMappingDTO;
import com.flomaestro.takt.dto.v_1_0_0.LoopCharacteristicsDTO;
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
