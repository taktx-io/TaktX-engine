package com.flomaestro.takt.dto.v_1_0_0;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.HashSet;
import java.util.UUID;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class MultiInstanceInstanceDTO extends ActivityInstanceDTO
    implements WithFlowNodeInstancesDTO {
  @JsonProperty("fni")
  private FlowNodeInstancesDTO flowNodeInstances;

  public MultiInstanceInstanceDTO(
      UUID elementInstanceId,
      String elementId,
      int passedCnt,
      ActtivityStateEnum state,
      FlowNodeInstancesDTO flowNodeInstances) {
    super(state, elementId, elementInstanceId, passedCnt, new HashSet<>());
    this.flowNodeInstances = flowNodeInstances;
  }
}
