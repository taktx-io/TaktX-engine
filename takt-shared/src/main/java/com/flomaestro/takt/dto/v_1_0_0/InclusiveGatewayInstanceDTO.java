package com.flomaestro.takt.dto.v_1_0_0;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Set;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class InclusiveGatewayInstanceDTO extends GatewayInstanceDTO {

  @JsonProperty("t")
  private Set<String> triggeredInputFlows;

  public InclusiveGatewayInstanceDTO(
      long elementInstanceId,
      String elementId,
      int passedCnt,
      Set<String> triggeredInputFlows,
      Set<String> selectedOutputFlows) {
    super(elementInstanceId, elementId, passedCnt, selectedOutputFlows);
    this.triggeredInputFlows = triggeredInputFlows;
  }
}
