package nl.qunit.bpmnmeister.pi;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.annotation.Nonnull;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;
import nl.qunit.bpmnmeister.pd.model.ProcessDefinitionKey;

@Getter
@ToString
@EqualsAndHashCode
@SuperBuilder(toBuilder = true)
public class ProcessInstance {

  @Nonnull
  private final ProcessInstanceKey rootInstanceKey;
  private final String parentElementId;
  private final ProcessInstanceKey processInstanceKey;
  private final ProcessInstanceKey parentInstanceKey;
  private final ProcessDefinitionKey processDefinitionKey;
  private final FlowNodeStates flowNodeStates;
  private final ProcessInstanceState processInstanceState;

  @JsonCreator
  public ProcessInstance(
      @Nonnull @JsonProperty("rootInstanceKey") ProcessInstanceKey rootInstanceKey,
      @Nonnull @JsonProperty("processInstanceKey") ProcessInstanceKey processInstanceKey,
      @Nonnull @JsonProperty("parentInstanceKey") ProcessInstanceKey parentInstanceKey,
      @Nonnull @JsonProperty("parentElementId") String parentElementId,
      @Nonnull @JsonProperty("processDefinitionKey") ProcessDefinitionKey processDefinitionKey,
      @Nonnull @JsonProperty("flowNodeStates") FlowNodeStates flowNodeStates,
      @Nonnull @JsonProperty("processInstanceState") ProcessInstanceState processInstanceState) {
    this.rootInstanceKey = rootInstanceKey;
    this.parentElementId = parentElementId;
    this.processInstanceKey = processInstanceKey;
    this.parentInstanceKey = parentInstanceKey;
    this.processDefinitionKey = processDefinitionKey;
    this.flowNodeStates = flowNodeStates;
    this.processInstanceState = processInstanceState;
  }
}
