package nl.qunit.bpmnmeister.pi.state.v_1_0_0;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.annotation.Nonnull;
import java.util.List;
import java.util.UUID;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;
import nl.qunit.bpmnmeister.pd.model.v_1_0_0.ProcessDefinitionKey;

@Getter
@ToString
@EqualsAndHashCode
@SuperBuilder(toBuilder = true)
public class ProcessInstanceDTO {
  private final UUID processInstanceKey;
  private final UUID parentProcessInstanceKey;
  private final List<String> parentElementIdPath;
  private final List<UUID> parentElementInstancePath;
  private final ProcessDefinitionKey processDefinitionKey;
  private final FlowNodeInstancesDTO flowNodeInstances;

  @JsonCreator
  public ProcessInstanceDTO(
      @Nonnull @JsonProperty("processInstanceKey") UUID processInstanceKey,
      @Nonnull @JsonProperty("parentProcessInstanceKey") UUID parentProcessInstanceKey,
      @Nonnull @JsonProperty("parentElementIdPath") List<String> parentElementIdPath,
      @Nonnull @JsonProperty("parentElementInstancePath") List<UUID> parentElementInstancePath,
      @Nonnull @JsonProperty("processDefinitionKey") ProcessDefinitionKey processDefinitionKey,
      @Nonnull @JsonProperty("flowNodeInstances") FlowNodeInstancesDTO flowNodeInstances) {
    this.processInstanceKey = processInstanceKey;
    this.parentProcessInstanceKey = parentProcessInstanceKey;
    this.parentElementIdPath = parentElementIdPath;
    this.parentElementInstancePath = parentElementInstancePath;
    this.processDefinitionKey = processDefinitionKey;
    this.flowNodeInstances = flowNodeInstances;
  }
}
