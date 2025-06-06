package io.taktx.dto;

import java.util.List;
import java.util.Set;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class ScriptTaskDTO extends TaskDTO {

  private ScriptType scriptType;
  private List<String> scriptExpressions;
  private String resultVariableName;

  public ScriptTaskDTO(
      String id,
      String parentId,
      Set<String> incoming,
      Set<String> outgoing,
      LoopCharacteristicsDTO loopCharacteristics,
      InputOutputMappingDTO ioMapping,
      ScriptType scriptType,
      List<String> scriptExpressions,
      String resultVariableName) {
    super(id, parentId, incoming, outgoing, loopCharacteristics, ioMapping);
    this.scriptType = scriptType;
    this.scriptExpressions = scriptExpressions;
    this.resultVariableName = resultVariableName;
  }
}
