package com.flomaestro.takt.dto.v_1_0_0;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Set;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@NoArgsConstructor
public abstract class ExternalTaskInstanceDTO extends TaskInstanceDTO {

  @JsonProperty("at")
  private int attempt;

  @JsonProperty("k")
  private List<ScheduleKeyDTO> scheduledKeys;

  public ExternalTaskInstanceDTO(
      ActtivityStateEnum state,
      long elementInstanceId,
      String elementId,
      int passedCnt,
      Set<Long> boundaryEventIds,
      int attempt,
      List<ScheduleKeyDTO> scheduledKeys) {
    super(state, elementInstanceId, elementId, passedCnt, boundaryEventIds);
    this.attempt = attempt;
    this.scheduledKeys = scheduledKeys;
  }
}
