package nl.qunit.bpmnmeister.pi;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.annotation.Nonnull;
import java.util.UUID;
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
  private final ProcessInstanceState processInstanceState;
  private final UUID rootInstanceKey;
  private final String parentElementId;
  private final UUID processInstanceKey;
  private final UUID parentInstanceKey;
  private final UUID parentElementInstanceId1;
  private final ProcessDefinitionKey processDefinitionKey;
  private final FlowNodeStates flowNodeStates;

  @JsonCreator
  public ProcessInstance(
      @Nonnull @JsonProperty("rootInstanceKey") UUID rootInstanceKey,
      @Nonnull @JsonProperty("processInstanceKey") UUID processInstanceKey,
      @Nonnull @JsonProperty("parentInstanceKey") UUID parentInstanceKey,
      @Nonnull @JsonProperty("parentElementId") String parentElementId,
      @Nonnull @JsonProperty("parentElementInstanceId") UUID parentElementInstanceId,
      @Nonnull @JsonProperty("processDefinitionKey") ProcessDefinitionKey processDefinitionKey,
      @Nonnull @JsonProperty("flowNodeStates") FlowNodeStates flowNodeStates,
      @Nonnull @JsonProperty("processInstanceState") ProcessInstanceState processInstanceState) {
    this.rootInstanceKey = rootInstanceKey;
    this.parentElementId = parentElementId;
    this.processInstanceKey = processInstanceKey;
    this.parentInstanceKey = parentInstanceKey;
    this.parentElementInstanceId1 = parentElementInstanceId;
    this.processDefinitionKey = processDefinitionKey;
    this.flowNodeStates = flowNodeStates;
    this.processInstanceState = processInstanceState;
  }
}
