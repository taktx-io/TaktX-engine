package com.flomaestro.takt.dto.v_1_0_0;

import java.util.Set;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class ExclusiveGatewayInstanceDTO extends GatewayInstanceDTO {
  public ExclusiveGatewayInstanceDTO(
      long elementInstanceId,
      String elementId,
      int passedCnt,
      Set<String> selectedOutputFlows) {
    super(elementInstanceId, elementId, passedCnt, selectedOutputFlows);
  }
}
