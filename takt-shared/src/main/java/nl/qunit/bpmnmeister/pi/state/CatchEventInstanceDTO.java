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
public abstract class CatchEventInstanceDTO extends EventInstanceDTO {
  private CatchEventStateEnum state;
  private Set<ScheduledKey> scheduledKeys;
  private Map<MessageEventKey, Set<String>> messageEventKeys;
  private Set<EscalationSubscriptionDTO> escalationSubscriptions;
  private Set<ErrorSubscriptionDTO> errorSubscriptions;
  private boolean catchAllEscalations;
  private boolean catchAllErrors;

  protected CatchEventInstanceDTO(
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
