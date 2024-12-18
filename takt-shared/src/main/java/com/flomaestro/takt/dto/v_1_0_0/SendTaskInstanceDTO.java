package com.flomaestro.takt.dto.v_1_0_0;

import java.util.List;
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
public class SendTaskInstanceDTO extends ExternalTaskInstanceDTO {

  public SendTaskInstanceDTO(
      ActtivityStateEnum state,
      UUID elementInstanceId,
      String elementId,
      int passedCnt,
      int loopCnt,
      int attempt,
      Set<UUID> boundaryEventIds,
      List<ScheduleKeyDTO> scheduledKeys) {
    super(
        state,
        elementInstanceId,
        elementId,
        passedCnt,
        loopCnt,
        boundaryEventIds,
        attempt,
        scheduledKeys);
  }
}
