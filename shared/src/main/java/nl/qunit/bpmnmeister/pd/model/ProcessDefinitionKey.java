package nl.qunit.bpmnmeister.pd.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;

@Builder
@Getter
@EqualsAndHashCode
public class ProcessDefinitionKey {
  private final String processDefinitionId;
  private final String generation;
  private final Integer version;

  @JsonCreator
  public ProcessDefinitionKey(
      @JsonProperty("processDefinitionId") String processDefinitionId,
      @JsonProperty("generation") String generation,
      @JsonProperty("version") Integer version) {
    this.processDefinitionId = processDefinitionId;
    this.generation = generation;
    this.version = version;
  }

  public static ProcessDefinitionKey of(ProcessDefinition processDefinition) {
    return new ProcessDefinitionKey(
        processDefinition.getDefinitions().getProcessDefinitionId(),
        processDefinition.getDefinitions().getGeneration(),
        processDefinition.getVersion());
  }

  @Override
  public String toString() {
    return "ProcessDefinitionKey{"
        + "processDefinitionId='"
        + processDefinitionId
        + '\''
        + ", generation='"
        + generation
        + '\''
        + ", version="
        + version
        + '}';
  }
}
