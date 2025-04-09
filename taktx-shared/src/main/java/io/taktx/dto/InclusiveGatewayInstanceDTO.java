package io.taktx.dto;

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
}
