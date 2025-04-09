package io.taktx.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Set;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class ParallelGatewayInstanceDTO extends GatewayInstanceDTO {
  @JsonProperty("t")
  Set<String> triggeredFlows;
}
