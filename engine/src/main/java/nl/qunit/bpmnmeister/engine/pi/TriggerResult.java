package nl.qunit.bpmnmeister.engine.pi;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Set;
import lombok.Builder;
import lombok.Getter;
import nl.qunit.bpmnmeister.pd.model.BaseElementId;
import nl.qunit.bpmnmeister.pi.ProcessInstanceTrigger;
import nl.qunit.bpmnmeister.pi.Variables;
import nl.qunit.bpmnmeister.pi.state.BpmnElementState;

@Getter
@Builder
public class TriggerResult {
  private final BpmnElementState newElementState;
  @Builder.Default private Set<BaseElementId> newActiveFlows = Set.of();
  @Builder.Default private Set<BaseElementId> externalTasks = Set.of();
  @Builder.Default private Set<ProcessInstanceTrigger> newProcessInstanceTriggers = Set.of();
  @Builder.Default private Variables variables = Variables.EMPTY;

  @JsonCreator
  public TriggerResult(
      @JsonProperty("newElementState") BpmnElementState newElementState,
      @JsonProperty("newActiveFlows") Set<BaseElementId> newActiveFlows,
      @JsonProperty("externalTasks") Set<BaseElementId> externalTasks,
      @JsonProperty("newProcessInstanceTriggers")
          Set<ProcessInstanceTrigger> newProcessInstanceTriggers,
      @JsonProperty("variables") Variables variables) {
    this.newElementState = newElementState;
    this.newActiveFlows = newActiveFlows;
    this.externalTasks = externalTasks;
    this.newProcessInstanceTriggers = newProcessInstanceTriggers;
    this.variables = variables;
  }

  @Override
  public String toString() {
    return "TriggerResult{"
        + "newElementState="
        + newElementState
        + ", newActiveFlows="
        + newActiveFlows
        + ", externalTasks="
        + externalTasks
        + ", newProcessInstanceTriggers="
        + newProcessInstanceTriggers
        + ", variables="
        + variables
        + '}';
  }
}
