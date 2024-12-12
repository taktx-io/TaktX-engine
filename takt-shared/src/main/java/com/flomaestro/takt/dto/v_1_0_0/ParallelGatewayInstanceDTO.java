package com.flomaestro.takt.dto.v_1_0_0;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Set;
import java.util.UUID;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class ParallelGatewayInstanceDTO extends GatewayInstanceDTO {
  @JsonProperty("tf")
  Set<String> triggeredFlows;

  public ParallelGatewayInstanceDTO(
      UUID elementInstanceId,
      String elementId,
      Set<String> triggeredFlows,
      int passedCnt,
      Set<String> selectedOutputFlows) {
    super(elementInstanceId, elementId, passedCnt, selectedOutputFlows);
    this.triggeredFlows = triggeredFlows;
  }
}
