package com.flomaestro.takt.dto.v_1_0_0;

import com.fasterxml.jackson.annotation.JsonProperty;
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
public class SubProcessInstanceDTO extends ActivityInstanceDTO implements WithFlowNodeInstancesDTO {

  @JsonProperty("fni")
  private FlowNodeInstancesDTO flowNodeInstances;

  public SubProcessInstanceDTO(
      FlowNodeInstancesDTO flowNodeInstances,
      String elementId,
      UUID elementInstanceId,
      ActtivityStateEnum state,
      int passedCnt,
      Set<UUID> boundaryEventIds) {
    super(state, elementId, elementInstanceId, passedCnt, boundaryEventIds);
    this.flowNodeInstances = flowNodeInstances;
  }
}
