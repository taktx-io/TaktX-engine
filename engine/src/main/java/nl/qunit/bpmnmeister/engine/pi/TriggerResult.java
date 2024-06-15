package nl.qunit.bpmnmeister.engine.pi;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.annotation.Nonnull;
import java.util.List;
import java.util.Set;
import lombok.Builder;
import lombok.Getter;
import nl.qunit.bpmnmeister.pi.ProcessInstanceTrigger;
import nl.qunit.bpmnmeister.pi.StartCommand;
import nl.qunit.bpmnmeister.pi.ThrowingEvent;
import nl.qunit.bpmnmeister.pi.state.FlowNodeState;
import nl.qunit.bpmnmeister.pi.state.MessageEvent;
import nl.qunit.bpmnmeister.pi.state.MessageEventKey;
import nl.qunit.bpmnmeister.scheduler.MessageScheduler;
import nl.qunit.bpmnmeister.scheduler.ScheduleKey;

@Getter
@Builder(toBuilder = true)
public class TriggerResult {
  public static final TriggerResult EMPTY = TriggerResult.builder().build();

  @Builder.Default private FlowNodeState newFlowNodeState = null;
  @Builder.Default private Set<ExternalTaskInfo> externalTasks = Set.of();
  @Builder.Default private List<ProcessInstanceTrigger> processInstanceTriggers = List.of();
  @Builder.Default private Set<StartCommand> newStartCommands = Set.of();
  @Builder.Default private ThrowingEvent throwingEvent = ThrowingEvent.NOOP;
  @Builder.Default private Set<MessageScheduler> messageSchedulers = Set.of();
  @Builder.Default private Set<ScheduleKey> cancelSchedules = Set.of();
  @Builder.Default private Set<MessageEvent> newMessageSubscriptions = Set.of();
  private final Set<MessageEventKey> cancelMessageSubscriptions;

  @JsonCreator
  public TriggerResult(
      @Nonnull @JsonProperty("newFlowNodeState") FlowNodeState newFlowNodeState,
      @Nonnull @JsonProperty("externalTasks") Set<ExternalTaskInfo> externalTasks,
      @Nonnull @JsonProperty("processInstanceTriggers")
          List<ProcessInstanceTrigger> processInstanceTriggers,
      @Nonnull @JsonProperty("newStartCommands") Set<StartCommand> newStartCommands,
      @Nonnull @JsonProperty("throwEvent") ThrowingEvent throwingEvent,
      @Nonnull @JsonProperty("newSchedules") Set<MessageScheduler> newSchedules,
      @Nonnull @JsonProperty("cancelSchedules") Set<ScheduleKey> cancelSchedules,
      @Nonnull @JsonProperty("newMessageSubscriptions") Set<MessageEvent> newMessageSubscriptions,
      @Nonnull @JsonProperty("cancelMessageSubscriptions")
          Set<MessageEventKey> cancelMessageSubscriptions) {
    this.newFlowNodeState = newFlowNodeState;
    this.externalTasks = externalTasks;
    this.processInstanceTriggers = processInstanceTriggers;
    this.newStartCommands = newStartCommands;
    this.throwingEvent = throwingEvent;
    this.messageSchedulers = newSchedules;
    this.cancelSchedules = cancelSchedules;
    this.newMessageSubscriptions = newMessageSubscriptions;
    this.cancelMessageSubscriptions = cancelMessageSubscriptions;
  }
}
