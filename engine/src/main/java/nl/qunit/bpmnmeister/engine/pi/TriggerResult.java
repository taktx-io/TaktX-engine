package nl.qunit.bpmnmeister.engine.pi;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.annotation.Nonnull;
import java.util.Set;
import lombok.Getter;
import nl.qunit.bpmnmeister.pi.ProcessInstanceTrigger;
import nl.qunit.bpmnmeister.pi.StartCommand;
import nl.qunit.bpmnmeister.pi.ThrowingEvent;
import nl.qunit.bpmnmeister.pi.Variables;
import nl.qunit.bpmnmeister.pi.state.BpmnElementState;

@Getter
public class TriggerResult {

  private final BpmnElementState newElementState;
  private final Set<String> newActiveFlows;
  private final Set<String> externalTasks;
  private final Set<ProcessInstanceTrigger> newProcessInstanceTriggers;
  private final Set<StartCommand> newStartCommands;
  private final ThrowingEvent throwingEvent;
  private final Variables variables;

  @JsonCreator
  public TriggerResult(
      @Nonnull @JsonProperty("newElementState") BpmnElementState newElementState,
      @Nonnull @JsonProperty("newActiveFlows") Set<String> newActiveFlows,
      @Nonnull @JsonProperty("externalTasks") Set<String> externalTasks,
      @Nonnull @JsonProperty("newProcessInstanceTriggers")
          Set<ProcessInstanceTrigger> newProcessInstanceTriggers,
      @Nonnull @JsonProperty("newStartCommands") Set<StartCommand> newStartCommands,
      @Nonnull @JsonProperty("throwEvent") ThrowingEvent throwingEvent,
      @Nonnull @JsonProperty("variables") Variables variables) {
    this.newElementState = newElementState;
    this.newActiveFlows = newActiveFlows;
    this.externalTasks = externalTasks;
    this.newProcessInstanceTriggers = newProcessInstanceTriggers;
    this.newStartCommands = newStartCommands;
    this.throwingEvent = throwingEvent;
    this.variables = variables;
  }
}
