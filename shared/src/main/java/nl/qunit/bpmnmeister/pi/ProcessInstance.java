package nl.qunit.bpmnmeister.pi;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.annotation.Nonnull;
import lombok.Getter;
import nl.qunit.bpmnmeister.pd.model.ProcessDefinition;

@Getter
public class ProcessInstance {
  private final ProcessInstanceKey parentProcessInstanceKey;
  private final String parentElementId;
  private final ProcessInstanceKey processInstanceKey;
  private final ProcessDefinition processDefinition;
  private final ElementStates elementStates;
  private final Variables variables;
  private final ProcessInstanceState processInstanceState;

  @JsonCreator
  public ProcessInstance(
      @Nonnull @JsonProperty("parentProcessInstanceKey") ProcessInstanceKey parentProcessInstanceKey,
      @Nonnull @JsonProperty("parentElementId") String parentElementId,
      @Nonnull @JsonProperty("processInstanceKey") ProcessInstanceKey processInstanceKey,
      @Nonnull @JsonProperty("processDefinition") ProcessDefinition processDefinition,
      @Nonnull @JsonProperty("elementStates") ElementStates elementStates,
      @Nonnull @JsonProperty("variables") Variables variables,
      @Nonnull @JsonProperty("processInstanceState") ProcessInstanceState processInstanceState) {
    this.parentProcessInstanceKey = parentProcessInstanceKey;
    this.parentElementId = parentElementId;
    this.processInstanceKey = processInstanceKey;
    this.processDefinition = processDefinition;
    this.elementStates = elementStates;
    this.variables = variables;
    this.processInstanceState = processInstanceState;
  }

  @Override
  public String toString() {
    return "ProcessInstance{"
        + "parentProcessInstanceKey="
        + parentProcessInstanceKey
        + ", processInstanceKey="
        + processInstanceKey
        + ", processDefinition="
        + processDefinition
        + ", elementStates="
        + elementStates
        + ", variables="
        + variables
        + ", processInstanceState="
        + processInstanceState
        + '}';
  }
}
