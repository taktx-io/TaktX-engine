package com.flomaestro.takt.dto.v_1_0_0;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Set;
import java.util.UUID;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

@ToString
@EqualsAndHashCode(callSuper = true)
@Data
@NoArgsConstructor
public class CallActivityInstanceDTO extends ActivityInstanceDTO {

  @JsonProperty("cpi")
  private UUID childProcessInstanceId;

  public CallActivityInstanceDTO(
      ActtivityStateEnum state,
      UUID childProcessInstanceId,
      UUID elementInstanceId,
      String elementId,
      int passedCnt,
      int loopCnt,
      Set<UUID> boundaryEventIds) {
    super(state, elementId, elementInstanceId, passedCnt, loopCnt, boundaryEventIds);
    this.childProcessInstanceId = childProcessInstanceId;
  }
}
