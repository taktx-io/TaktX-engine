package com.flomaestro.takt.dto.v_1_0_0;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Set;
import java.util.UUID;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public abstract class GatewayInstanceDTO extends FlowNodeInstanceDTO {
  @JsonProperty("sof")
  private Set<String> selectedOutputFlows;

  protected GatewayInstanceDTO(
      UUID elementInstanceId, String elementId, int passedCnt, Set<String> selectedOutputFlows) {
    super(elementInstanceId, elementId, passedCnt);
    this.selectedOutputFlows = selectedOutputFlows;
  }
}
