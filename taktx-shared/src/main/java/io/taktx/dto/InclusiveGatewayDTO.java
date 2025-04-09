package io.taktx.dto;

import java.util.Set;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class InclusiveGatewayDTO extends GatewayDTO {

  public InclusiveGatewayDTO(
      String id, String parentId, Set<String> incoming, Set<String> outgoing, String defaultFlow) {
    super(id, parentId, incoming, outgoing, defaultFlow);
  }
}
