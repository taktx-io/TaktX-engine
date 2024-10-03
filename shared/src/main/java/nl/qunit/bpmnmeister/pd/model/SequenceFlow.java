package nl.qunit.bpmnmeister.pd.model;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Getter
@SuperBuilder
public class SequenceFlow extends FlowElement {
  private String source;
  private String target;
  private FlowCondition condition;

  @Setter private FlowNode sourceNode;
  @Setter private FlowNode targetNode;
}
