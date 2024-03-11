package nl.qunit.bpmnmeister.pi;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.annotation.Nonnull;
import java.util.Map;
import lombok.Getter;
import nl.qunit.bpmnmeister.pd.model.BaseElementId;
import nl.qunit.bpmnmeister.pd.model.ProcessDefinition;
import nl.qunit.bpmnmeister.pi.state.BpmnElementState;

@Getter
public class ProcessInstance {
  private final ProcessInstanceKey parentProcessInstanceKey;
  private final ProcessInstanceKey processInstanceKey;
  private final ProcessDefinition processDefinition;
  private final Map<BaseElementId, BpmnElementState> elementStates;
  private final Map<String, JsonNode> variables;

  @JsonCreator
  public ProcessInstance(
      @Nonnull @JsonProperty("parentProcessInstanceKey")
          ProcessInstanceKey parentProcessInstanceKey,
      @Nonnull @JsonProperty("processInstanceKey") ProcessInstanceKey processInstanceKey,
      @Nonnull @JsonProperty("processDefinition") ProcessDefinition processDefinition,
      @Nonnull @JsonProperty("elementStates") Map<BaseElementId, BpmnElementState> elementStates,
      @Nonnull @JsonProperty("variables") Map<String, JsonNode> variables) {
    this.parentProcessInstanceKey = parentProcessInstanceKey;
    this.processInstanceKey = processInstanceKey;
    this.processDefinition = processDefinition;
    this.elementStates = elementStates;
    this.variables = variables;
  }
}
