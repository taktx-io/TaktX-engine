package nl.qunit.bpmnmeister.pd.model.v_1_0_0;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.annotation.Nonnull;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

@Getter
@EqualsAndHashCode
@ToString
public class ProcessDefinitionKey {
  private final String processDefinitionId;
  private final Integer version;

  @JsonCreator
  public ProcessDefinitionKey(
      @Nonnull @JsonProperty("processDefinitionId") String processDefinitionId,
      @Nonnull @JsonProperty("version") Integer version) {
    this.processDefinitionId = processDefinitionId;
    this.version = version;
  }

  public static ProcessDefinitionKey of(ProcessDefinitionDTO processDefinition) {
    return new ProcessDefinitionKey(
        processDefinition.getDefinitions().getDefinitionsKey().getProcessDefinitionId(),
        processDefinition.getVersion());
  }
}
