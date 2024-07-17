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
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
@SuperBuilder(toBuilder = true)
public class ProcessInstanceUpdate extends ProcessInstance {
  private final Variables variables;

  @JsonCreator
  public ProcessInstanceUpdate(
      @Nonnull @JsonProperty("rootInstanceKey") UUID rootInstanceKey,
      @Nonnull @JsonProperty("parentElementId") String parentElementId,
      @Nonnull @JsonProperty("parentElementInstanceId") UUID parentElementInstanceId,
      @Nonnull @JsonProperty("processInstanceKey") UUID processInstanceKey,
      @Nonnull @JsonProperty("parentInstanceKey") UUID parentInstanceKey,
      @Nonnull @JsonProperty("processDefinitionKey") ProcessDefinitionKey processDefinitionKey,
      @Nonnull @JsonProperty("flowNodeStates") FlowNodeStates flowNodeStates,
      @Nonnull @JsonProperty("processInstanceState") ProcessInstanceState processInstanceState,
      @Nonnull @JsonProperty("variables") Variables variables) {
    super(rootInstanceKey, processInstanceKey, parentInstanceKey, parentElementId, parentElementInstanceId, processDefinitionKey, flowNodeStates, processInstanceState);
    this.variables = variables;
  }

  public ProcessInstanceUpdate(ProcessInstance processInstance, Variables variables) {
    this(processInstance.getRootInstanceKey(), processInstance.getParentElementId(), processInstance.getParentElementInstanceId1(), processInstance.getProcessInstanceKey(), processInstance.getParentInstanceKey(), processInstance.getProcessDefinitionKey(), processInstance.getFlowNodeStates(), processInstance.getProcessInstanceState(), variables);
  }


}
