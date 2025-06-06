package io.taktx.xml;

import io.taktx.bpmn.TScriptTask;
import io.taktx.dto.InputOutputMappingDTO;
import io.taktx.dto.LoopCharacteristicsDTO;
import io.taktx.dto.ScriptTaskDTO;

public interface ScriptTaskMapper extends Mapper {
  ScriptTaskDTO map(
      TScriptTask userTask,
      String parentId,
      LoopCharacteristicsDTO loopCharacteristics,
      InputOutputMappingDTO ioMapping);
}
