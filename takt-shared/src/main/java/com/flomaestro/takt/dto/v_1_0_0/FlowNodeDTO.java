package com.flomaestro.takt.dto.v_1_0_0;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Set;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public abstract class FlowNodeDTO extends FlowElementDTO {
  @JsonProperty("inc")
  private Set<String> incoming;

  @JsonProperty("out")
  private Set<String> outgoing;

  protected FlowNodeDTO(String id, String parentId, Set<String> incoming, Set<String> outgoing) {
    super(id, parentId);
    this.incoming = incoming;
    this.outgoing = outgoing;
  }
}
