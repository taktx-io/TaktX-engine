package io.taktx.dto;

import java.util.Set;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class ParallelGatewayInstanceDTO extends GatewayInstanceDTO {
  Set<String> triggeredFlows;
}
