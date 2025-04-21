package io.taktx.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonFormat.Shape;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Getter
@EqualsAndHashCode
@ToString
@NoArgsConstructor
@JsonFormat(shape = Shape.ARRAY)
public class ProcessDefinitionKey {
  private String processDefinitionId;

  private Integer version;

  public ProcessDefinitionKey(String processDefinitionId) {
    this(processDefinitionId, -1);
  }

  public ProcessDefinitionKey(String processDefinitionId, Integer version) {
    this.processDefinitionId = processDefinitionId;
    this.version = version;
  }

  public static ProcessDefinitionKey of(ProcessDefinitionDTO processDefinition) {
    return new ProcessDefinitionKey(
        processDefinition.getDefinitions().getDefinitionsKey().getProcessDefinitionId(),
        processDefinition.getVersion());
  }
}
