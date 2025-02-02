package com.flomaestro.takt.dto.v_1_0_0;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
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
  @JsonProperty("st")
  private CatchEventStateEnum state;

  @JsonProperty("sk")
  private Set<ScheduleKeyDTO> scheduledKeys;

  @JsonProperty("mk")
  private Map<MessageEventKeyDTO, Set<String>> messageEventKeys;

  @JsonProperty("es")
  private Set<EscalationSubscriptionDTO> escalationSubscriptions;

  @JsonProperty("er")
  private Set<ErrorSubscriptionDTO> errorSubscriptions;

  @JsonProperty("cs")
  private boolean catchAllEscalations;

  @JsonProperty("cr")
  private boolean catchAllErrors;

  protected CatchEventInstanceDTO(
      long elementInstanceId,
      String elementId,
      int passedCnt,
      CatchEventStateEnum state,
      Set<ScheduleKeyDTO> scheduledKeys,
      Map<MessageEventKeyDTO, Set<String>> messageEventKeys) {
    super(elementInstanceId, elementId, passedCnt);
    this.state = state;
    this.scheduledKeys = scheduledKeys;
    this.messageEventKeys = messageEventKeys;
  }

  @JsonIgnore
  @Override
  public boolean isWaiting() {
    return state == CatchEventStateEnum.WAITING;
  }
}
