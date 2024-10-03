package nl.qunit.bpmnmeister.pi.state;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;
import nl.qunit.bpmnmeister.scheduler.ScheduledKey;

@Getter
@Setter
@ToString(callSuper = true)
@SuperBuilder(toBuilder = true)
public abstract class CatchEventState extends EventState {
  private CatchEventStateEnum state;
  private Set<ScheduledKey> scheduledKeys;
  private Map<MessageEventKey, Set<String>> messageEventKeys;

  protected CatchEventState(
      UUID elementInstanceId,
      String elementId,
      int passedCnt,
      CatchEventStateEnum state,
      Set<ScheduledKey> scheduledKeys,
      Map<MessageEventKey, Set<String>> messageEventKeys) {
    super(elementInstanceId, elementId, passedCnt);
    this.state = state;
    this.scheduledKeys = scheduledKeys;
    this.messageEventKeys = messageEventKeys;
  }
}
