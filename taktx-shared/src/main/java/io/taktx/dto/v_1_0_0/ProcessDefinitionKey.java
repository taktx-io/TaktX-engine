package io.taktx.dto.v_1_0_0;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Getter
@EqualsAndHashCode
@ToString
@NoArgsConstructor
public class ProcessDefinitionKey {
  @JsonProperty("pd")
  private String processDefinitionId;

  @JsonProperty("v")
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
