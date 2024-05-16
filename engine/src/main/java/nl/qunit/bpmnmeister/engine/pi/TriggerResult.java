package nl.qunit.bpmnmeister.engine.pi;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.annotation.Nonnull;
import java.util.Set;
import lombok.Builder;
import lombok.Getter;
import nl.qunit.bpmnmeister.pi.ProcessInstanceTrigger;
import nl.qunit.bpmnmeister.pi.StartCommand;
import nl.qunit.bpmnmeister.pi.ThrowingEvent;
import nl.qunit.bpmnmeister.pi.Variables;
import nl.qunit.bpmnmeister.pi.state.FlowNodeState;
import nl.qunit.bpmnmeister.scheduler.MessageScheduler;
import nl.qunit.bpmnmeister.scheduler.ScheduleKey;

@Getter
@Builder(toBuilder = true)
public class TriggerResult {
  public static final TriggerResult EMPTY = TriggerResult.builder().build();

  @Builder.Default private FlowNodeState newFlowNodeState = null;
  @Builder.Default private Set<String> newActiveFlows = Set.of();
  @Builder.Default private Set<String> externalTasks = Set.of();
  @Builder.Default private Set<ProcessInstanceTrigger> newProcessInstanceTriggers = Set.of();
  @Builder.Default private Set<StartCommand> newStartCommands = Set.of();
  @Builder.Default private ThrowingEvent throwingEvent = ThrowingEvent.NOOP;
  @Builder.Default private Set<MessageScheduler> messageSchedulers = Set.of();
  @Builder.Default private Set<ScheduleKey> cancelSchedules = Set.of();
  @Builder.Default private Variables variables = Variables.EMPTY;

  @JsonCreator
  public TriggerResult(
      @Nonnull @JsonProperty("newFlowNodeState") FlowNodeState newFlowNodeState,
      @Nonnull @JsonProperty("newActiveFlows") Set<String> newActiveFlows,
      @Nonnull @JsonProperty("externalTasks") Set<String> externalTasks,
      @Nonnull @JsonProperty("newProcessInstanceTriggers")
          Set<ProcessInstanceTrigger> newProcessInstanceTriggers,
      @Nonnull @JsonProperty("newStartCommands") Set<StartCommand> newStartCommands,
      @Nonnull @JsonProperty("throwEvent") ThrowingEvent throwingEvent,
      @Nonnull @JsonProperty("newSchedules") Set<MessageScheduler> newSchedules,
      @Nonnull @JsonProperty("cancelSchedules") Set<ScheduleKey> cancelSchedules,
      @Nonnull @JsonProperty("variables") Variables variables) {
    this.newFlowNodeState = newFlowNodeState;
    this.newActiveFlows = newActiveFlows;
    this.externalTasks = externalTasks;
    this.newProcessInstanceTriggers = newProcessInstanceTriggers;
    this.newStartCommands = newStartCommands;
    this.throwingEvent = throwingEvent;
    this.messageSchedulers = newSchedules;
    this.cancelSchedules = cancelSchedules;
    this.variables = variables;
  }
}
