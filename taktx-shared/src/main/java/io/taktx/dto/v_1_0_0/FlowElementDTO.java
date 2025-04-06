package io.taktx.dto.v_1_0_0;

import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public abstract class FlowElementDTO extends BaseElementDTO {

  protected FlowElementDTO(String id, String parentId) {
    super(id, parentId);
  }
}
