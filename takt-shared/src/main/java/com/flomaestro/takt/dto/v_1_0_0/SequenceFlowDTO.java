package com.flomaestro.takt.dto.v_1_0_0;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class SequenceFlowDTO extends FlowElementDTO {

  @JsonProperty("src")
  private String source;

  @JsonProperty("tgt")
  private String target;

  @JsonProperty("cnd")
  private FlowConditionDTO condition;

  public SequenceFlowDTO(
      String id, String parentId, String source, String target, FlowConditionDTO condition) {
    super(id, parentId);
    this.source = source;
    this.target = target;
    this.condition = condition;
  }
}
