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
public class ProcessInstanceDTO {
  private final UUID processInstanceKey;
  private final ProcessDefinitionKey processDefinitionKey;
  private final FlowNodeStatesDTO flowNodeStates;

  @JsonCreator
  public ProcessInstanceDTO(
      @Nonnull @JsonProperty("processInstanceKey") UUID processInstanceKey,
      @Nonnull @JsonProperty("processDefinitionKey") ProcessDefinitionKey processDefinitionKey,
      @Nonnull @JsonProperty("flowNodeStates") FlowNodeStatesDTO flowNodeStates) {
    this.processInstanceKey = processInstanceKey;
    this.processDefinitionKey = processDefinitionKey;
    this.flowNodeStates = flowNodeStates;
  }
}
