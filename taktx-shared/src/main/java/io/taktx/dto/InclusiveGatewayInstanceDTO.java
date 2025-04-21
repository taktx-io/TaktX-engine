package io.taktx.dto;

import java.util.Set;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class InclusiveGatewayInstanceDTO extends GatewayInstanceDTO {
  private Set<String> triggeredInputFlows;
}
