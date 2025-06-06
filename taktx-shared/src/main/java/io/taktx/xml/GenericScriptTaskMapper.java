package io.taktx.xml;

import io.taktx.bpmn.TScriptTask;
import io.taktx.dto.InputOutputMappingDTO;
import io.taktx.dto.LoopCharacteristicsDTO;
import io.taktx.dto.ScriptTaskDTO;

public class GenericScriptTaskMapper implements ScriptTaskMapper {

  @Override
  public ScriptTaskDTO map(
      TScriptTask serviceTask,
      String parentId,
      LoopCharacteristicsDTO loopCharacteristics,
      InputOutputMappingDTO ioMapping) {
    throw new UnsupportedOperationException(
        "GenericScriptTaskMapper does not support mapping for TScriptTask: " + serviceTask);
  }
}
