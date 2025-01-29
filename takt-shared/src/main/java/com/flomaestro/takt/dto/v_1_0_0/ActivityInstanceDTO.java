package com.flomaestro.takt.dto.v_1_0_0;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.Set;
import java.util.UUID;
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
  private Set<UUID> boundaryEventIds;

  @JsonProperty("t")
  private boolean iteration = false;

  @JsonProperty("n")
  private UUID nextIterationId;

  @JsonProperty("u")
  private JsonNode inputElement;

  @JsonProperty("o")
  private JsonNode outputElement;

  @JsonProperty("l")
  private int loopCnt;

  protected ActivityInstanceDTO(
      ActtivityStateEnum state,
      String elementId,
      UUID elementInstanceId,
      int passedCnt,
      Set<UUID> boundaryEventIds) {
    super(elementInstanceId, elementId, passedCnt);
    this.state = state;
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
