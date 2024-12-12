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

  public FlowNodeInstancesDTO(ProcessInstanceState state, UUID flowNodeInstancesId) {
    this.state = state;
    this.flowNodeInstancesId = flowNodeInstancesId;
  }
}
