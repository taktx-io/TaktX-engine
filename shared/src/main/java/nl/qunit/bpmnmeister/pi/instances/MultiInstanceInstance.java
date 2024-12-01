package nl.qunit.bpmnmeister.pi.instances;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import nl.qunit.bpmnmeister.pd.model.Activity;
import nl.qunit.bpmnmeister.pi.FlowNodeInstances;
import nl.qunit.bpmnmeister.pi.ProcessInstanceState;
import nl.qunit.bpmnmeister.pi.state.ActtivityStateEnum;

@NoArgsConstructor
@Getter
@Setter
public class MultiInstanceInstance extends ActivityInstance<Activity>
    implements WithFlowNodeInstances {
  private FlowNodeInstances flowNodeInstances = new FlowNodeInstances();

  public MultiInstanceInstance(Activity activity, FLowNodeInstance<?> parentInstance) {
    super(parentInstance, activity);
  }

  @Override
  public void setState(ActtivityStateEnum state) {
    super.setState(state);
    switch (state) {
      case READY -> flowNodeInstances.setState(ProcessInstanceState.START);
      case WAITING -> flowNodeInstances.setState(ProcessInstanceState.ACTIVE);
      case TERMINATED -> flowNodeInstances.setState(ProcessInstanceState.TERMINATED);
      case FAILED -> flowNodeInstances.setState(ProcessInstanceState.FAILED);
      case FINISHED -> flowNodeInstances.setState(ProcessInstanceState.COMPLETED);
    }
  }
}
