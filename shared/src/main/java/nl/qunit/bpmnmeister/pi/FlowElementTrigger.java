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
  private final ProcessInstanceKey parentProcessInstanceKey;
  private final ProcessInstanceKey processInstanceKey;
  private final ProcessDefinition processDefinition;
  private final BaseElementId elementId;
  private final BaseElementId inputFlowId;
  private final Variables variables;

  @JsonCreator
  public FlowElementTrigger(
      @JsonProperty("processInstanceKey") @Nonnull ProcessInstanceKey processInstanceKey,
      @JsonProperty("parentProcessInstanceKey") @Nonnull
          ProcessInstanceKey parentProcessInstanceKey,
      @JsonProperty("processDefinition") @Nonnull ProcessDefinition processDefinition,
      @JsonProperty("elementId") @Nonnull BaseElementId elementId,
      @JsonProperty("inputFlowId") @Nonnull BaseElementId inputFlowId,
      @JsonProperty("variables") @Nonnull Variables variables) {
    this.processInstanceKey = processInstanceKey;
    this.parentProcessInstanceKey = parentProcessInstanceKey;
    this.processDefinition = processDefinition;
    this.elementId = elementId;
    this.inputFlowId = inputFlowId;
    this.variables = variables;
  }
}
