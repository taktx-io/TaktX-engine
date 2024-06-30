package nl.qunit.bpmnmeister.pi;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.UUID;
import lombok.Getter;
import lombok.ToString;
import nl.qunit.bpmnmeister.pd.model.ProcessDefinitionKey;
import nl.qunit.bpmnmeister.scheduler.SchedulableMessage;

@Getter
@ToString
public class ExternalTaskTrigger implements SchedulableMessage<UUID> {

  private final UUID rootInstanceKey;
  private final UUID processInstanceKey;
  private final ProcessDefinitionKey processDefinitionKey;
  private final String externalTaskId;
  private final String elementId;
  private final Variables variables;

  @JsonCreator
  public ExternalTaskTrigger(
      @JsonProperty("rootInstanceKey") UUID rootInstanceKey,
      @JsonProperty("processInstanceKey") UUID processInstanceKey,
      @JsonProperty("processDefinitionKey") ProcessDefinitionKey processDefinitionKey,
      @JsonProperty("externalTaskId") String externalTaskId,
      @JsonProperty("elementId") String elementId,
      @JsonProperty("variables") Variables variables) {
    this.rootInstanceKey = rootInstanceKey;
    this.processInstanceKey = processInstanceKey;
    this.processDefinitionKey = processDefinitionKey;
    this.externalTaskId = externalTaskId;
    this.elementId = elementId;
    this.variables = variables;
  }

  @Override
  public String toString() {
    return "ExternalTaskTrigger{"
        + "processInstanceKey="
        + processInstanceKey
        + ", processDefinitionKey="
        + processDefinitionKey
        + ", externalTaskId='"
        + elementId
        + '\''
        + ", variables="
        + variables
        + '}';
  }

  @JsonIgnore
  @Override
  public UUID getRecordKey(UUID rootInstanceKey) {
    return processInstanceKey;
  }
}
