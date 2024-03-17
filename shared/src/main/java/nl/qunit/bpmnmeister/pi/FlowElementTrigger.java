package nl.qunit.bpmnmeister.pi;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.annotation.Nonnull;
import java.util.function.Supplier;
import lombok.Getter;
import nl.qunit.bpmnmeister.pd.model.BaseElementId;

@Getter
public class FlowElementTrigger extends Trigger {
  public static final FlowElementTrigger NONE =
      new FlowElementTrigger(
          ProcessInstanceKey.NONE,
          ProcessInstanceKey.NONE,
          BaseElementId.NONE,
          BaseElementId.NONE,
          Variables.EMPTY);
  private final ProcessInstanceKey parentProcessInstanceKey;
  private final ProcessInstanceKey processInstanceKey;
  private final BaseElementId elementId;
  private final BaseElementId inputFlowId;
  private final Variables variables;

  @JsonCreator
  public FlowElementTrigger(
      @JsonProperty("processInstanceKey") @Nonnull ProcessInstanceKey processInstanceKey,
      @JsonProperty("parentProcessInstanceKey") @Nonnull
          ProcessInstanceKey parentProcessInstanceKey,
      @JsonProperty("elementId") @Nonnull BaseElementId elementId,
      @JsonProperty("inputFlowId") @Nonnull BaseElementId inputFlowId,
      @JsonProperty("variables") @Nonnull Variables variables) {
    this.processInstanceKey = processInstanceKey;
    this.parentProcessInstanceKey = parentProcessInstanceKey;
    this.elementId = elementId;
    this.inputFlowId = inputFlowId;
    this.variables = variables;
  }

  @Override
  public String toString() {
    return "ProcessInstanceTrigger{"
        + "parentProcessInstanceKey="
        + parentProcessInstanceKey
        + ", processInstanceKey="
        + processInstanceKey
        + ", elementId='"
        + elementId
        + '\''
        + ", inputFlowId='"
        + inputFlowId
        + '\''
        + ", variables="
        + variables
        + '}';
  }

  @Override
  public ProcessInstance getProcessInstance(Supplier<ProcessInstance> processInstanceSupplier) {
    return processInstanceSupplier.get();
  }
}
