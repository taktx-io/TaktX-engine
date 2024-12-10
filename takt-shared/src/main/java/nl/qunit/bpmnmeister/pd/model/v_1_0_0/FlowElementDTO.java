package nl.qunit.bpmnmeister.pd.model.v_1_0_0;

import lombok.EqualsAndHashCode;
import lombok.Getter;

@Getter
@EqualsAndHashCode(callSuper = true)
public abstract class FlowElementDTO extends BaseElementDTO {

  protected FlowElementDTO(String id, String parentId) {
    super(id, parentId);
  }
}
