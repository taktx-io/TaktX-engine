package nl.qunit.bpmnmeister.pi;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.annotation.Nonnull;
import lombok.Getter;
import lombok.ToString;
import nl.qunit.bpmnmeister.pd.model.ProcessDefinition;

@Getter
@ToString(callSuper = true)
public class StartNewProcessInstanceTrigger extends FlowElementTrigger {

  @Nonnull private final ProcessInstanceKey parentProcessInstanceKey;
  private final ProcessDefinition processDefinition;
  private final String parentElementId;

  @JsonCreator
  public StartNewProcessInstanceTrigger(
      @JsonProperty("processInstanceKey") @Nonnull ProcessInstanceKey processInstanceKey,
      @JsonProperty("parentProcessInstanceKey") @Nonnull
          ProcessInstanceKey parentProcessInstanceKey,
      @JsonProperty("processDefinition") @Nonnull ProcessDefinition processDefinition,
      @JsonProperty("parentElementId") @Nonnull String parentElementId,
      @JsonProperty("elementId") @Nonnull String elementId,
      @JsonProperty("inputFlowId") @Nonnull String inputFlowId,
      @JsonProperty("variables") @Nonnull Variables variables) {
    super(processInstanceKey, elementId, inputFlowId, variables);
    this.parentProcessInstanceKey = parentProcessInstanceKey;
    this.processDefinition = processDefinition;
    this.parentElementId = parentElementId;
  }
}
