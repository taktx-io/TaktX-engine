package io.taktx.dto;

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
}
