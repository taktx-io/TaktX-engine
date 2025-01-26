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
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class ServiceTaskInstanceDTO extends ExternalTaskInstanceDTO {

  public ServiceTaskInstanceDTO(
      ActtivityStateEnum state,
      UUID elementInstanceId,
      String elementId,
      int passedCnt,
      int attempt,
      Set<UUID> boundaryEventIds,
      List<ScheduleKeyDTO> scheduledKeys) {
    super(
        state,
        elementInstanceId,
        elementId,
        passedCnt,
        boundaryEventIds,
        attempt,
        scheduledKeys);
  }
}
