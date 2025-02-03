package com.flomaestro.takt.dto.v_1_0_0;

import com.fasterxml.jackson.annotation.JsonProperty;
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
  @JsonProperty("s")
  private ActtivityStateEnum state;

  @JsonProperty("b")
  private Set<Long> boundaryEventIds;

  @JsonProperty("t")
  private boolean iteration = false;

  @JsonProperty("n")
  private long nextIterationId;

  @JsonProperty("u")
  private JsonNode inputElement;

  @JsonProperty("o")
  private JsonNode outputElement;

  @JsonProperty("l")
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
