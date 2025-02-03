package com.flomaestro.takt.dto.v_1_0_0;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class MultiInstanceInstanceDTO extends ActivityInstanceDTO
    implements WithFlowNodeInstancesDTO {
  @JsonProperty("f")
  private FlowNodeInstancesDTO flowNodeInstances;
}
