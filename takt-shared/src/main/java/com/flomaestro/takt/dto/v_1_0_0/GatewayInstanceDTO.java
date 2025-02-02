package com.flomaestro.takt.dto.v_1_0_0;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Set;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public abstract class GatewayInstanceDTO extends FlowNodeInstanceDTO {
  @JsonProperty("f")
  private Set<String> selectedOutputFlows;

  protected GatewayInstanceDTO(
      long elementInstanceId, String elementId, int passedCnt, Set<String> selectedOutputFlows) {
    super(elementInstanceId, elementId, passedCnt);
    this.selectedOutputFlows = selectedOutputFlows;
  }
}
