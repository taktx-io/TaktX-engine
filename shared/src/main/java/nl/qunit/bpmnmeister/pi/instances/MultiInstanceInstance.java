package nl.qunit.bpmnmeister.pi.instances;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import nl.qunit.bpmnmeister.pd.model.Activity2;
import nl.qunit.bpmnmeister.pi.FlowNodeStates2;

@NoArgsConstructor
@Getter
@Setter
public class MultiInstanceInstance extends ActivityInstance {
  private FlowNodeStates2 flowNodeStates;

  public MultiInstanceInstance(Activity2 activity2, FLowNodeInstance parentInstance) {
    super(activity2.getId(), parentInstance);
    this.flowNodeStates = new FlowNodeStates2();
  }
}
