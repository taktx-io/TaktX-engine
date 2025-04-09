package io.taktx.dto;

import java.util.Set;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class ExclusiveGatewayDTO extends GatewayDTO {

  public ExclusiveGatewayDTO(
      String id, String parentId, Set<String> incoming, Set<String> outgoing, String defaultFlow) {
    super(id, parentId, incoming, outgoing, defaultFlow);
  }
}
