package nl.qunit.bpmnmeister.engine.pd.model;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import nl.qunit.bpmnmeister.pd.model.v_1_0_0.FlowConditionDTO;

@Getter
@SuperBuilder
public class SequenceFlow extends FlowElement {
  private String source;
  private String target;
  private FlowConditionDTO condition;

  @Setter private FlowNode sourceNode;
  @Setter private FlowNode targetNode;
}
