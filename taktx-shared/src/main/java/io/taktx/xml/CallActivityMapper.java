package io.taktx.xml;

import io.taktx.bpmn.TCallActivity;
import io.taktx.dto.CallActivityDTO;
import io.taktx.dto.InputOutputMappingDTO;
import io.taktx.dto.LoopCharacteristicsDTO;

public interface CallActivityMapper extends Mapper {

  CallActivityDTO map(
      TCallActivity callActivity,
      String parentId,
      LoopCharacteristicsDTO loopCharacteristics,
      InputOutputMappingDTO ioMapping);
}
