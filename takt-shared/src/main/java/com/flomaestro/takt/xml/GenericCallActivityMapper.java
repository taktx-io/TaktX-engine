package com.flomaestro.takt.xml;

import com.flomaestro.bpmn.TCallActivity;
import com.flomaestro.takt.dto.v_1_0_0.CallActivityDTO;
import com.flomaestro.takt.dto.v_1_0_0.InputOutputMappingDTO;
import com.flomaestro.takt.dto.v_1_0_0.LoopCharacteristicsDTO;

public class GenericCallActivityMapper implements CallActivityMapper {

  @Override
  public CallActivityDTO map(
      TCallActivity callActivity,
      String parentId,
      LoopCharacteristicsDTO loopCharacteristics,
      InputOutputMappingDTO ioMapping) {
    return new CallActivityDTO(
        callActivity.getId(),
        parentId,
        mapQNameList(callActivity.getIncoming()),
        mapQNameList(callActivity.getOutgoing()),
        loopCharacteristics,
        callActivity.getCalledElement().toString(),
        true,
        false,
        ioMapping);
  }
}
