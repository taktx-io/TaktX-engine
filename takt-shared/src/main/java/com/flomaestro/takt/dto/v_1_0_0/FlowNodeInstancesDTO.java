package com.flomaestro.takt.dto.v_1_0_0;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.UUID;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@EqualsAndHashCode
public class FlowNodeInstancesDTO {
  @JsonProperty("st")
  private ProcessInstanceState state;

  @JsonProperty("fi")
  private UUID flowNodeInstancesId;

  @JsonProperty("ac")
  private int activeCnt;

  public FlowNodeInstancesDTO(
      ProcessInstanceState state,
      UUID flowNodeInstancesId,
      int activeCnt) {
    this.state = state;
    this.flowNodeInstancesId = flowNodeInstancesId;
    this.activeCnt = activeCnt;
  }
}
