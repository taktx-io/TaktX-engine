package nl.qunit.bpmnmeister.pi;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.annotation.Nonnull;
import lombok.Getter;
import nl.qunit.bpmnmeister.pd.model.Constants;
import nl.qunit.bpmnmeister.pd.model.ProcessDefinition;

@Getter
public class FlowElementTrigger extends Trigger {
  public static final FlowElementTrigger NONE =
      new FlowElementTrigger(
          ProcessInstanceKey.NONE,
          ProcessInstanceKey.NONE,
          Constants.NONE,
          ProcessDefinition.NONE,
          Constants.NONE,
          Constants.NONE,
          Variables.EMPTY);

  private final String inputFlowId;

  @JsonCreator
  public FlowElementTrigger(
      @JsonProperty("processInstanceKey") @Nonnull ProcessInstanceKey processInstanceKey,
      @JsonProperty("parentProcessInstanceKey") @Nonnull ProcessInstanceKey parentProcessInstanceKey,
      @JsonProperty("parentElementId") @Nonnull String parentElementId,
      @JsonProperty("processDefinition") @Nonnull ProcessDefinition processDefinition,
      @JsonProperty("elementId") @Nonnull String elementId,
      @JsonProperty("inputFlowId") @Nonnull String inputFlowId,
      @JsonProperty("variables") @Nonnull Variables variables) {
    super(processInstanceKey, parentProcessInstanceKey, parentElementId, processDefinition, elementId, variables);
    this.inputFlowId = inputFlowId;
  }
}
