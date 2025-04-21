package io.taktx.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonFormat.Shape;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@EqualsAndHashCode
@JsonFormat(shape = Shape.ARRAY)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class FlowNodeInstancesDTO {
  private ProcessInstanceState state;

  private int activeCnt;

  private long elementInstanceCnt;

  public FlowNodeInstancesDTO(ProcessInstanceState state, int activeCnt, long elementInstanceCnt) {
    this.state = state;
    this.activeCnt = activeCnt;
    this.elementInstanceCnt = elementInstanceCnt;
  }
}
