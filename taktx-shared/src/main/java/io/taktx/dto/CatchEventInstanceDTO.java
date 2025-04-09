package io.taktx.dto;

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

  @JsonIgnore
  @Override
  public boolean isWaiting() {
    return state == CatchEventStateEnum.WAITING;
  }
}
