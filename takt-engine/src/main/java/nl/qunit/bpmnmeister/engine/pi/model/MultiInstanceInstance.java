package nl.qunit.bpmnmeister.engine.pi.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import nl.qunit.bpmnmeister.engine.pd.model.Activity;
import nl.qunit.bpmnmeister.pi.state.v_1_0_0.ActtivityStateEnum;
import nl.qunit.bpmnmeister.pi.trigger.v_1_0_0.ProcessInstanceState;

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
