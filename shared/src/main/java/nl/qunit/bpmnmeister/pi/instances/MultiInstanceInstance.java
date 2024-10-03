package nl.qunit.bpmnmeister.pi.instances;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import nl.qunit.bpmnmeister.pd.model.Activity;
import nl.qunit.bpmnmeister.pi.FlowNodeInstances;

@NoArgsConstructor
@Getter
@Setter
public class MultiInstanceInstance extends ActivityInstance<Activity>
    implements WithFlowNodeInstances {
  private FlowNodeInstances flowNodeInstances;

  public MultiInstanceInstance(Activity activity, FLowNodeInstance<?> parentInstance) {
    super(parentInstance, activity);
    this.flowNodeInstances = new FlowNodeInstances();
  }
}
