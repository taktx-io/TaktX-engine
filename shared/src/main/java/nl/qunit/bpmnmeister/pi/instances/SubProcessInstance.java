package nl.qunit.bpmnmeister.pi.instances;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import nl.qunit.bpmnmeister.pd.model.SubProcess2;
import nl.qunit.bpmnmeister.pi.FlowNodeStates2;

@NoArgsConstructor
@Setter
@Getter
public class SubProcessInstance extends ActivityInstance<SubProcess2> {

  private FlowNodeStates2 flowNodeStates = new FlowNodeStates2();

  public SubProcessInstance(FLowNodeInstance parentInstance, SubProcess2 flowNode) {
    super(parentInstance, flowNode);
  }
}
