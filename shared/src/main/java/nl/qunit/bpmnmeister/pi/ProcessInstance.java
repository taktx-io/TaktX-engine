package nl.qunit.bpmnmeister.pi;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import lombok.Getter;
import nl.qunit.bpmnmeister.pd.model.ProcessDefinition;
import nl.qunit.bpmnmeister.pi.state.BpmnElementState;

@Getter
public class ProcessInstance {
  private final ProcessInstanceKey processInstanceKey;
  private final ProcessDefinition processDefinition;
  private final Map<String, BpmnElementState> elementStates;
  private final Map<String, JsonNode> variables;

  @JsonCreator
  public ProcessInstance(
      @JsonProperty("processInstanceKey") ProcessInstanceKey processInstanceKey,
      @JsonProperty("processDefinition") ProcessDefinition processDefinition,
      @JsonProperty("elementStates") Map<String, BpmnElementState> elementStates,
      @JsonProperty("variables") Map<String, JsonNode> variables) {
    this.processInstanceKey = processInstanceKey;
    this.processDefinition = processDefinition;
    this.elementStates = elementStates;
    this.variables = variables;
  }
}
