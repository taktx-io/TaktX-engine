package com.flomaestro.takt.dto.v_1_0_0;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Set;
import java.util.UUID;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@EqualsAndHashCode(callSuper = true)
@Data
@NoArgsConstructor
public abstract class ActivityInstanceDTO extends FlowNodeInstanceDTO {
  @JsonProperty("st")
  private ActtivityStateEnum state;

  @JsonProperty("lc")
  private int loopCnt;

  @JsonProperty("be")
  private Set<UUID> boundaryEventIds;

  protected ActivityInstanceDTO(
      ActtivityStateEnum state,
      String elementId,
      UUID elementInstanceId,
      int passedCnt,
      int loopCnt,
      Set<UUID> boundaryEventIds) {
    super(elementInstanceId, elementId, passedCnt);
    this.state = state;
    this.loopCnt = loopCnt;
    this.boundaryEventIds = boundaryEventIds;
  }

  @Override
  public boolean isTerminated() {
    return state == ActtivityStateEnum.TERMINATED;
  }

  @Override
  public boolean isFailed() {
    return state == ActtivityStateEnum.FAILED;
  }
}
