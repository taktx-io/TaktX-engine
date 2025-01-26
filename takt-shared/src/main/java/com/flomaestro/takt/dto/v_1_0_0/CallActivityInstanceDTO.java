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
      Set<UUID> boundaryEventIds) {
    super(state, elementId, elementInstanceId, passedCnt, boundaryEventIds);
    this.childProcessInstanceId = childProcessInstanceId;
  }
}
