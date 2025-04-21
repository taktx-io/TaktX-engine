package io.taktx.dto;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class SequenceFlowDTO extends FlowElementDTO {

  private String source;

  private String target;

  private FlowConditionDTO condition;

  public SequenceFlowDTO(
      String id, String parentId, String source, String target, FlowConditionDTO condition) {
    super(id, parentId);
    this.source = source;
    this.target = target;
    this.condition = condition;
  }
}
