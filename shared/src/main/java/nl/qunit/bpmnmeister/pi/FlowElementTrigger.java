package nl.qunit.bpmnmeister.pi;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.annotation.Nonnull;
import lombok.Getter;
import nl.qunit.bpmnmeister.pd.model.BaseElementId;
import nl.qunit.bpmnmeister.pd.model.ProcessDefinition;

@Getter
public class FlowElementTrigger extends Trigger {
  public static final FlowElementTrigger NONE =
      new FlowElementTrigger(
          ProcessInstanceKey.NONE,
          ProcessInstanceKey.NONE,
          ProcessDefinition.NONE,
          BaseElementId.NONE,
          BaseElementId.NONE,
          Variables.EMPTY);

  private final BaseElementId inputFlowId;

  @JsonCreator
  public FlowElementTrigger(
      @JsonProperty("processInstanceKey") @Nonnull ProcessInstanceKey processInstanceKey,
      @JsonProperty("parentProcessInstanceKey") @Nonnull
          ProcessInstanceKey parentProcessInstanceKey,
      @JsonProperty("processDefinition") @Nonnull ProcessDefinition processDefinition,
      @JsonProperty("elementId") @Nonnull BaseElementId elementId,
      @JsonProperty("inputFlowId") @Nonnull BaseElementId inputFlowId,
      @JsonProperty("variables") @Nonnull Variables variables) {
    super(processInstanceKey, parentProcessInstanceKey, processDefinition, elementId, variables);
    this.inputFlowId = inputFlowId;
  }
}
