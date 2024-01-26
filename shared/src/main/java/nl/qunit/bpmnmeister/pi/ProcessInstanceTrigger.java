package nl.qunit.bpmnmeister.pi;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import nl.qunit.bpmnmeister.pd.model.ProcessDefinition;

@Getter
public class ProcessInstanceTrigger {
  private final ProcessInstanceKey processInstanceKey;
  private final ProcessDefinition processDefinition;
  private final String elementId;
  private final String inputFlowId;

  @JsonCreator
  public ProcessInstanceTrigger(
      @JsonProperty("processInstanceKey") ProcessInstanceKey processInstanceKey,
      @JsonProperty("processDefinition") ProcessDefinition processDefinition,
      @JsonProperty("elementId") String elementId,
      @JsonProperty("inputFlowId") String inputFlowId) {
    this.processInstanceKey = processInstanceKey;
    this.processDefinition = processDefinition;
    this.elementId = elementId;
    this.inputFlowId = inputFlowId;
  }
}
