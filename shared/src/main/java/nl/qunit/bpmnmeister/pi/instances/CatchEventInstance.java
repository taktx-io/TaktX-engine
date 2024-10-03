package nl.qunit.bpmnmeister.pi.instances;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import nl.qunit.bpmnmeister.pd.model.Event2;
import nl.qunit.bpmnmeister.pi.state.CatchEventStateEnum;
import nl.qunit.bpmnmeister.pi.state.MessageEventKey;
import nl.qunit.bpmnmeister.scheduler.ScheduledKey;

@Getter
@Setter
@NoArgsConstructor
public abstract class CatchEventInstance<N extends Event2> extends EventInstance<N>
    implements ReceivingMessageInstance {
  private CatchEventStateEnum state;

  private Set<ScheduledKey> scheduledKeys;
  private Map<MessageEventKey, Set<String>> messageEventKeys;

  protected CatchEventInstance(FLowNodeInstance parentInstance, N flowNode) {
    super(parentInstance, flowNode);
    state = CatchEventStateEnum.READY;
    scheduledKeys = new HashSet<>();
    messageEventKeys = new HashMap<>();
  }

  @Override
  public boolean stateAllowsStart() {
    return state == CatchEventStateEnum.READY;
  }

  @Override
  public boolean stateAllowsContinue() {
    return state == CatchEventStateEnum.WAITING;
  }

  @Override
  public boolean isNotAwaiting() {
    return state != CatchEventStateEnum.WAITING;
  }

  @Override
  public boolean isCompleted() {
    return state == CatchEventStateEnum.FINISHED || state == CatchEventStateEnum.TERMINATED;
  }

  @Override
  public boolean stateAllowsTerminate() {
    return state == CatchEventStateEnum.WAITING || state == CatchEventStateEnum.READY;
  }

  @Override
  public void terminate() {
    // Do nothing
    if (stateAllowsTerminate()) {
      state = CatchEventStateEnum.TERMINATED;
    }
  }

  public void addScheduledKey(ScheduledKey scheduledKey) {
    this.scheduledKeys.add(scheduledKey);
  }

  public void addMessageSubscriptionWithCorrelationKey(
      MessageEventKey messageEventKey, String correlationKey) {
    this.messageEventKeys
        .computeIfAbsent(messageEventKey, k -> new HashSet<>())
        .add(correlationKey);
  }
}
