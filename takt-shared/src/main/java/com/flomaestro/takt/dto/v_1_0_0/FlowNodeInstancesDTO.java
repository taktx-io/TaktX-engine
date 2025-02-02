package com.flomaestro.takt.dto.v_1_0_0;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@EqualsAndHashCode
public class FlowNodeInstancesDTO {
  @JsonProperty("st")
  private ProcessInstanceState state;

  @JsonProperty("ac")
  private int activeCnt;

  @JsonProperty("ei")
  private long elementInstanceCnt;


  public FlowNodeInstancesDTO(ProcessInstanceState state, int activeCnt, long elementInstanceCnt) {
    this.state = state;
    this.activeCnt = activeCnt;
    this.elementInstanceCnt = elementInstanceCnt;
  }
}
