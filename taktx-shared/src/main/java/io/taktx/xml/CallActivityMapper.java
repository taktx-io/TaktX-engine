package io.taktx.xml;

import io.taktx.bpmn.TCallActivity;
import io.taktx.dto.v_1_0_0.CallActivityDTO;
import io.taktx.dto.v_1_0_0.InputOutputMappingDTO;
import io.taktx.dto.v_1_0_0.LoopCharacteristicsDTO;

public interface CallActivityMapper extends Mapper {

  CallActivityDTO map(
      TCallActivity callActivity,
      String parentId,
      LoopCharacteristicsDTO loopCharacteristics,
      InputOutputMappingDTO ioMapping);
}
