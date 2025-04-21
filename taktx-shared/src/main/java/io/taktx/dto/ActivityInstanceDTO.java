package io.taktx.dto;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Set;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

@EqualsAndHashCode(callSuper = true)
@Data
@NoArgsConstructor
@ToString(callSuper = true)
public abstract class ActivityInstanceDTO extends FlowNodeInstanceDTO {
  private ActtivityStateEnum state;

  private Set<Long> boundaryEventIds;

  private boolean iteration = false;

  private long nextIterationId;

  private JsonNode inputElement;

  private JsonNode outputElement;

  private int loopCnt;

  @Override
  public boolean isTerminated() {
    return state == ActtivityStateEnum.TERMINATED;
  }

  @Override
  public boolean isFailed() {
    return state == ActtivityStateEnum.FAILED;
  }

  @Override
  public boolean isWaiting() {
    return state == ActtivityStateEnum.WAITING;
  }
}
