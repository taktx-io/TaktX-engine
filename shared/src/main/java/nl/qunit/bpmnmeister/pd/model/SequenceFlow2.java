package nl.qunit.bpmnmeister.pd.model;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Getter
@SuperBuilder
public class SequenceFlow2 extends FlowElement2 {
  private String source;
  private String target;
  private FlowCondition condition;

  @Setter private FlowNode2 sourceNode;
  @Setter private FlowNode2 targetNode;
}
