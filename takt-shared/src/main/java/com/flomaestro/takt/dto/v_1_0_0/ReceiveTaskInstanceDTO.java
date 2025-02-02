package com.flomaestro.takt.dto.v_1_0_0;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;
import java.util.Set;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

@ToString(callSuper = true)
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class ReceiveTaskInstanceDTO extends TaskInstanceDTO {

  @JsonProperty("c")
  private String correlationKey;

  @JsonProperty("m")
  private Map<MessageEventKeyDTO, Set<String>> messageEventKeys;

  public ReceiveTaskInstanceDTO(
      ActtivityStateEnum state,
      long elementInstanceId,
      String elementId,
      int passedCnt,
      String correlationKey,
      Set<Long> boundaryEventIds,
      Map<MessageEventKeyDTO, Set<String>> messageEventKeys) {
    super(state, elementInstanceId, elementId, passedCnt, boundaryEventIds);
    this.correlationKey = correlationKey;
    this.messageEventKeys = messageEventKeys;
  }
}
