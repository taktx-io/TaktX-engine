package com.flomaestro.takt.dto.v_1_0_0;

import java.util.Set;
import java.util.UUID;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class TaskInstanceDTO extends ActivityInstanceDTO {
  public TaskInstanceDTO(
      ActtivityStateEnum state,
      UUID elementInstanceId,
      String elementId,
      int passedCnt,
      Set<UUID> boundaryEventIds) {
    super(state, elementId, elementInstanceId, passedCnt, boundaryEventIds);
  }
}
