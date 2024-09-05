package nl.qunit.bpmnmeister.pd.model;

import java.util.Optional;
import lombok.Getter;
import lombok.experimental.SuperBuilder;
import nl.qunit.bpmnmeister.pi.instances.FLowNodeInstance;

@Getter
@SuperBuilder
public class SequenceFlow2 extends FlowElement2 {
  private String source;
  private String target;
  private FlowCondition condition;

  public Optional<FLowNodeInstance> trigger(
      FlowElements2 flowElements, FLowNodeInstance parentInstance) {
    return flowElements.getFlowNode(target).map(node -> node.newInstance(parentInstance));
  }
}
