package com.flomaestro.takt.dto.v_1_0_0;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
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
  private Set<ScheduledKeyDTO> scheduledKeys;

  @JsonProperty("mek")
  private Map<MessageEventKeyDTO, Set<String>> messageEventKeys;

  @JsonProperty("ess")
  private Set<EscalationSubscriptionDTO> escalationSubscriptions;

  @JsonProperty("ers")
  private Set<ErrorSubscriptionDTO> errorSubscriptions;

  @JsonProperty("ces")
  private boolean catchAllEscalations;

  @JsonProperty("cer")
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
