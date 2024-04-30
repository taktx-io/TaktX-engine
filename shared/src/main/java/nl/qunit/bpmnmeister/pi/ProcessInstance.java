package nl.qunit.bpmnmeister.pi;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.annotation.Nonnull;
import lombok.Getter;
import lombok.ToString;
import nl.qunit.bpmnmeister.pd.model.ProcessDefinitionKey;

@Getter
@ToString
public class ProcessInstance {
  private final String parentElementId;
  private final ProcessInstanceKey processInstanceKey;
  private final ProcessDefinitionKey processDefinitionKey;
  private final ElementStates elementStates;
  private final Variables variables;
  private final ProcessInstanceState processInstanceState;

  @JsonCreator
  public ProcessInstance(
      @Nonnull @JsonProperty("parentElementId") String parentElementId,
      @Nonnull @JsonProperty("processInstanceKey") ProcessInstanceKey processInstanceKey,
      @Nonnull @JsonProperty("processDefinitionKey") ProcessDefinitionKey processDefinitionKey,
      @Nonnull @JsonProperty("elementStates") ElementStates elementStates,
      @Nonnull @JsonProperty("variables") Variables variables,
      @Nonnull @JsonProperty("processInstanceState") ProcessInstanceState processInstanceState) {
    this.parentElementId = parentElementId;
    this.processInstanceKey = processInstanceKey;
    this.processDefinitionKey = processDefinitionKey;
    this.elementStates = elementStates;
    this.variables = variables;
    this.processInstanceState = processInstanceState;
  }
}
