package nl.qunit.bpmnmeister.pi;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;
import lombok.Builder;
import lombok.Getter;
import nl.qunit.bpmnmeister.pd.model.ProcessDefinitionKey;

@Builder
@Getter
public class ExternalTaskTrigger {
  private final ProcessInstanceKey processInstanceKey;
  private final ProcessDefinitionKey processDefinitionKey;
  private final String externalTaskId;
  private final Map<String, Object> variables;

  @JsonCreator
  public ExternalTaskTrigger(
      @JsonProperty("processInstanceKey") ProcessInstanceKey processInstanceKey,
      @JsonProperty("processDefinitionKey") ProcessDefinitionKey processDefinitionKey,
      @JsonProperty("externalTaskId") String externalTaskId,
      @JsonProperty("variables") Map<String, Object> variables) {
    this.processInstanceKey = processInstanceKey;
    this.processDefinitionKey = processDefinitionKey;
    this.externalTaskId = externalTaskId;
    this.variables = variables;
  }
}
