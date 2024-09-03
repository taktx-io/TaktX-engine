package nl.qunit.bpmnmeister.pi.instances;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import nl.qunit.bpmnmeister.pi.FlowNodeStates2;

@NoArgsConstructor
@Setter
@Getter
public class SubProcessInstance extends ActivityInstance {

  private FlowNodeStates2 flowNodeStates = new FlowNodeStates2();

  public SubProcessInstance(String flowNode, FLowNodeInstance parentInstance) {
    super(flowNode, parentInstance);
  }
}
