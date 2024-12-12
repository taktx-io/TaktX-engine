package com.flomaestro.takt.dto.v_1_0_0;

import java.util.Set;
import java.util.UUID;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class TaskInstanceDTO extends ActivityInstanceDTO {
  public TaskInstanceDTO(
      ActtivityStateEnum state,
      UUID elementInstanceId,
      String elementId,
      int passedCnt,
      int loopCnt,
      Set<UUID> boundaryEventIds) {
    super(state, elementId, elementInstanceId, passedCnt, loopCnt, boundaryEventIds);
  }
}
