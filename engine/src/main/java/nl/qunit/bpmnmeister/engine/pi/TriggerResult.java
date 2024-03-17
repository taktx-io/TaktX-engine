package nl.qunit.bpmnmeister.engine.pi;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.annotation.Nonnull;
import java.util.Set;
import lombok.Getter;
import nl.qunit.bpmnmeister.pd.model.BaseElementId;
import nl.qunit.bpmnmeister.pi.Trigger;
import nl.qunit.bpmnmeister.pi.Variables;
import nl.qunit.bpmnmeister.pi.state.BpmnElementState;

@Getter
public class TriggerResult {

  private final BpmnElementState newElementState;
  private final Set<BaseElementId> newActiveFlows;
  private final Set<BaseElementId> externalTasks;
  private final Set<Trigger> newProcessInstanceTriggers;
  private final Variables variables;

  @JsonCreator
  public TriggerResult(
      @Nonnull @JsonProperty("newElementState") BpmnElementState newElementState,
      @Nonnull @JsonProperty("newActiveFlows") Set<BaseElementId> newActiveFlows,
      @Nonnull @JsonProperty("externalTasks") Set<BaseElementId> externalTasks,
      @Nonnull @JsonProperty("newProcessInstanceTriggers") Set<Trigger> newProcessInstanceTriggers,
      @Nonnull @JsonProperty("variables") Variables variables) {
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
