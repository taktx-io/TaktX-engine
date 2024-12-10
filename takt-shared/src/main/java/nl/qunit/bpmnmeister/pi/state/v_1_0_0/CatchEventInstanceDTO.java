package nl.qunit.bpmnmeister.pi.state.v_1_0_0;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;
import nl.qunit.bpmnmeister.scheduler.v_1_0_0.ScheduledKeyDTO;

@Getter
@Setter
@ToString(callSuper = true)
@SuperBuilder(toBuilder = true)
public abstract class CatchEventInstanceDTO extends EventInstanceDTO {
  private CatchEventStateEnum state;
  private Set<ScheduledKeyDTO> scheduledKeys;
  private Map<MessageEventKeyDTO, Set<String>> messageEventKeys;
  private Set<EscalationSubscriptionDTO> escalationSubscriptions;
  private Set<ErrorSubscriptionDTO> errorSubscriptions;
  private boolean catchAllEscalations;
  private boolean catchAllErrors;

  protected CatchEventInstanceDTO(
      UUID elementInstanceId,
      String elementId,
      int passedCnt,
      CatchEventStateEnum state,
      Set<ScheduledKeyDTO> scheduledKeys,
      Map<MessageEventKeyDTO, Set<String>> messageEventKeys) {
    super(elementInstanceId, elementId, passedCnt);
    this.state = state;
    this.scheduledKeys = scheduledKeys;
    this.messageEventKeys = messageEventKeys;
  }
}
