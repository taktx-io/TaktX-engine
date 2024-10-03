package nl.qunit.bpmnmeister.pi.instances;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import nl.qunit.bpmnmeister.pd.model.Activity2;
import nl.qunit.bpmnmeister.pi.FlowNodeStates2;

@NoArgsConstructor
@Getter
@Setter
public class MultiInstanceInstance extends ActivityInstance<Activity2>
    implements WithFlowNodeStates {
  private FlowNodeStates2 flowNodeStates;

  public MultiInstanceInstance(Activity2 activity2, FLowNodeInstance<?> parentInstance) {
    super(parentInstance, activity2);
    this.flowNodeStates = new FlowNodeStates2();
  }
}
