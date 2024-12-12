package com.flomaestro.takt.xml;

import com.flomaestro.bpmn.TCallActivity;
import com.flomaestro.takt.dto.v_1_0_0.CallActivityDTO;
import com.flomaestro.takt.dto.v_1_0_0.InputOutputMappingDTO;
import com.flomaestro.takt.dto.v_1_0_0.LoopCharacteristicsDTO;

public interface CallActivityMapper extends Mapper {

  CallActivityDTO map(
      TCallActivity callActivity,
      String parentId,
      LoopCharacteristicsDTO loopCharacteristics,
      InputOutputMappingDTO ioMapping);
}
