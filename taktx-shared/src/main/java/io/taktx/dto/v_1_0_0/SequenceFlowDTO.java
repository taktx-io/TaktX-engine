package io.taktx.dto.v_1_0_0;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class SequenceFlowDTO extends FlowElementDTO {

  @JsonProperty("s")
  private String source;

  @JsonProperty("t")
  private String target;

  @JsonProperty("c")
  private FlowConditionDTO condition;

  public SequenceFlowDTO(
      String id, String parentId, String source, String target, FlowConditionDTO condition) {
    super(id, parentId);
    this.source = source;
    this.target = target;
    this.condition = condition;
  }
}
