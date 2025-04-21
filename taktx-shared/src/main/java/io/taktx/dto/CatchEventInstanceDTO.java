package io.taktx.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.Map;
import java.util.Set;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Data
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public abstract class CatchEventInstanceDTO extends EventInstanceDTO {
  private CatchEventStateEnum state;

  private Set<ScheduleKeyDTO> scheduledKeys;

  private Map<MessageEventKeyDTO, Set<String>> messageEventKeys;

  private Set<EscalationSubscriptionDTO> escalationSubscriptions;

  private Set<ErrorSubscriptionDTO> errorSubscriptions;

  private boolean catchAllEscalations;

  private boolean catchAllErrors;

  @JsonIgnore
  @Override
  public boolean isWaiting() {
    return state == CatchEventStateEnum.WAITING;
  }
}
