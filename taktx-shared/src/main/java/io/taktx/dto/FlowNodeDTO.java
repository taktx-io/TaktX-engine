package io.taktx.dto;

import java.util.Set;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public abstract class FlowNodeDTO extends FlowElementDTO {
  private Set<String> incoming;

  private Set<String> outgoing;

  protected FlowNodeDTO(String id, String parentId, Set<String> incoming, Set<String> outgoing) {
    super(id, parentId);
    this.incoming = incoming;
    this.outgoing = outgoing;
  }
}
