package nl.qunit.bpmnmeister.pi;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.annotation.Nonnull;
import java.util.function.Supplier;
import lombok.Getter;
import nl.qunit.bpmnmeister.pd.model.BaseElementId;
import nl.qunit.bpmnmeister.pd.model.ProcessDefinition;

@Getter
public class FlowElementNewProcessInstanceTrigger extends Trigger {
  public static final FlowElementNewProcessInstanceTrigger NONE =
      new FlowElementNewProcessInstanceTrigger(
          ProcessInstanceKey.NONE,
          ProcessInstanceKey.NONE,
          ProcessDefinition.NONE,
          BaseElementId.NONE,
          Variables.EMPTY);
  private final ProcessInstanceKey parentProcessInstanceKey;
  private final ProcessInstanceKey processInstanceKey;
  private final ProcessDefinition processDefinition;
  private final BaseElementId elementId;
  private final Variables variables;

  @JsonCreator
  public FlowElementNewProcessInstanceTrigger(
      @JsonProperty("processInstanceKey") @Nonnull ProcessInstanceKey processInstanceKey,
      @JsonProperty("parentProcessInstanceKey") @Nonnull
          ProcessInstanceKey parentProcessInstanceKey,
      @JsonProperty("processDefinition") @Nonnull ProcessDefinition processDefinition,
      @JsonProperty("elementId") @Nonnull BaseElementId elementId,
      @JsonProperty("variables") @Nonnull Variables variables) {
    this.processInstanceKey = processInstanceKey;
    this.parentProcessInstanceKey = parentProcessInstanceKey;
    this.processDefinition = processDefinition;
    this.elementId = elementId;
    this.variables = variables;
  }

  @Override
  public String toString() {
    return "ProcessInstanceTrigger{"
        + "parentProcessInstanceKey="
        + parentProcessInstanceKey
        + ", processInstanceKey="
        + processInstanceKey
        + ", processDefinition="
        + processDefinition
        + ", elementId='"
        + elementId
        + '\''
        + ", variables="
        + variables
        + '}';
  }

  @Override
  public ProcessInstance getProcessInstance(Supplier<ProcessInstance> processInstanceSupplier) {
    return new ProcessInstance(
        getParentProcessInstanceKey(),
        getProcessInstanceKey(),
        getProcessDefinition(),
        ElementStates.EMPTY,
        getVariables());
  }
}
