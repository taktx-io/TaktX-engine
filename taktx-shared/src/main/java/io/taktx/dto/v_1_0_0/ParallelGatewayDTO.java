package io.taktx.dto.v_1_0_0;

import java.util.Set;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class ParallelGatewayDTO extends GatewayDTO {
  public ParallelGatewayDTO(
      String id, String parentId, Set<String> incoming, Set<String> outgoing) {
    super(id, parentId, incoming, outgoing, null);
  }
}
