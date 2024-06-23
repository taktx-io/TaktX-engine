package nl.qunit.bpmnmeister.pi;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.ToString;
import nl.qunit.bpmnmeister.pd.model.ProcessDefinitionKey;
import nl.qunit.bpmnmeister.scheduler.SchedulableMessage;

@Getter
@ToString
public class ExternalTaskTrigger implements SchedulableMessage<ProcessInstanceKey> {

  private final ProcessInstanceKey rootInstanceKey;
  private final ProcessInstanceKey processInstanceKey;
  private final ProcessDefinitionKey processDefinitionKey;
  private final String externalTaskId;
  private final String elementId;
  private final Variables variables;

  @JsonCreator
  public ExternalTaskTrigger(
      @JsonProperty("rootInstanceKey") ProcessInstanceKey rootInstanceKey,
      @JsonProperty("processInstanceKey") ProcessInstanceKey processInstanceKey,
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
  public ProcessInstanceKey getRecordKey() {
    return processInstanceKey;
  }
}
