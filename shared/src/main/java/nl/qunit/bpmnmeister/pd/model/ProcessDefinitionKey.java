package nl.qunit.bpmnmeister.pd.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.annotation.Nonnull;
import lombok.EqualsAndHashCode;
import lombok.Getter;

@Getter
@EqualsAndHashCode
public class ProcessDefinitionKey {
  private final BaseElementId processDefinitionId;
  private final Integer version;

  @JsonCreator
  public ProcessDefinitionKey(
      @Nonnull @JsonProperty("processDefinitionId") BaseElementId processDefinitionId,
      @Nonnull @JsonProperty("version") Integer version) {
    this.processDefinitionId = processDefinitionId;
    this.version = version;
  }

  public static ProcessDefinitionKey of(ProcessDefinition processDefinition) {
    return new ProcessDefinitionKey(
        processDefinition.getDefinitions().getDefinitionsKey().getProcessDefinitionId(),
        processDefinition.getVersion());
  }

  @Override
  public String toString() {
    return "ProcessDefinitionKey{"
        + "processDefinitionId='"
        + processDefinitionId
        + '\''
        + ", version="
        + version
        + '}';
  }
}
