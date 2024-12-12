package com.flomaestro.engine.pd.model;

import com.flomaestro.takt.dto.v_1_0_0.FlowConditionDTO;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Getter
@SuperBuilder
public class SequenceFlow extends FlowElement {
  private String source;
  private String target;
  private FlowConditionDTO condition;

  @Setter private FlowNode sourceNode;
  @Setter private FlowNode targetNode;
}
