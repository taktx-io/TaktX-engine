package io.taktx.xml;

import io.taktx.bpmn.TCallActivity;
import io.taktx.dto.CallActivityDTO;
import io.taktx.dto.InputOutputMappingDTO;
import io.taktx.dto.LoopCharacteristicsDTO;

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
