package io.taktx.dto;

import java.util.Set;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public abstract class GatewayDTO extends FlowNodeDTO {

  private String defaultFlow;

  protected GatewayDTO(
      String id, String parentId, Set<String> incoming, Set<String> outgoing, String defaultFlow) {
    super(id, parentId, incoming, outgoing);
    this.defaultFlow = defaultFlow;
  }
}
