package nl.qunit.bpmnmeister.pi;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.UUID;
import lombok.Getter;
import nl.qunit.bpmnmeister.pd.model.ProcessDefinition;

@Getter
public class ProcessInstanceMigrationTrigger {
  private final UUID processInstanceKey;
  private final ProcessDefinition newProcessDefinition;

  @JsonCreator
  public ProcessInstanceMigrationTrigger(
      @JsonProperty("processInstanceKey") UUID processInstanceKey,
      @JsonProperty("processDefinition") ProcessDefinition newProcessDefinition) {
    this.processInstanceKey = processInstanceKey;
    this.newProcessDefinition = newProcessDefinition;
  }
}
