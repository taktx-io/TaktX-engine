package io.taktx.xml;

import io.taktx.bpmn.Script;
import io.taktx.bpmn.TScriptTask;
import io.taktx.dto.InputOutputMappingDTO;
import io.taktx.dto.LoopCharacteristicsDTO;
import io.taktx.dto.ScriptTaskDTO;
import io.taktx.dto.ScriptType;
import java.util.List;
import java.util.Optional;

public class ZeebeScriptTaskMapper implements ScriptTaskMapper {

  @Override
  public ScriptTaskDTO map(
      TScriptTask scriptTask,
      String parentId,
      LoopCharacteristicsDTO loopCharacteristics,
      InputOutputMappingDTO ioMapping) {
    Optional<Script> scriptElement =
        ExtensionElementHelper.extractExtensionElement(
            scriptTask.getExtensionElements(), Script.class);

    String scriptExpression = "";
    String resultVariableName = "";
    if (scriptElement.isPresent()) {
      scriptExpression = scriptElement.get().getExpression();
      resultVariableName = scriptElement.get().getResultVariable();
    }
    return new ScriptTaskDTO(
        scriptTask.getId(),
        parentId,
        mapQNameList(scriptTask.getIncoming()),
        mapQNameList(scriptTask.getOutgoing()),
        loopCharacteristics,
        ioMapping,
        ScriptType.FEEL,
        List.of(scriptExpression),
        resultVariableName);
  }
}
