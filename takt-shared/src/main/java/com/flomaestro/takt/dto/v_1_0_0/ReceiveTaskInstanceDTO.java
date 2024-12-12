package com.flomaestro.takt.dto.v_1_0_0;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

@ToString(callSuper = true)
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class ReceiveTaskInstanceDTO extends TaskInstanceDTO {

  @JsonProperty("ck")
  private String correlationKey;

  @JsonProperty("mek")
  private Map<MessageEventKeyDTO, Set<String>> messageEventKeys;

  public ReceiveTaskInstanceDTO(
      ActtivityStateEnum state,
      UUID elementInstanceId,
      String elementId,
      int passedCnt,
      int loopCnt,
      String correlationKey,
      Set<UUID> boundaryEventIds,
      Map<MessageEventKeyDTO, Set<String>> messageEventKeys) {
    super(state, elementInstanceId, elementId, passedCnt, loopCnt, boundaryEventIds);
    this.correlationKey = correlationKey;
    this.messageEventKeys = messageEventKeys;
  }
}
