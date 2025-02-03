package com.flomaestro.engine.pi.model;

import com.flomaestro.engine.pd.model.Activity;
import com.flomaestro.takt.dto.v_1_0_0.ActtivityStateEnum;
import com.flomaestro.takt.dto.v_1_0_0.ProcessInstanceState;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@NoArgsConstructor
@Getter
@Setter
public class MultiInstanceInstance extends ActivityInstance<Activity>
    implements WithFlowNodeInstances {
  private FlowNodeInstances flowNodeInstances = new FlowNodeInstances();

  public MultiInstanceInstance(
      Activity activity, FlowNodeInstance<?> parentInstance, long elementInstanceId) {
    super(parentInstance, activity, elementInstanceId);
    setState(ActtivityStateEnum.INITIAL);
  }

  @Override
  public void setState(ActtivityStateEnum state) {
    super.setState(state);
    switch (state) {
      case INITIAL -> flowNodeInstances.setState(ProcessInstanceState.START);
      case STARTED -> flowNodeInstances.setState(ProcessInstanceState.ACTIVE);
      case WAITING -> flowNodeInstances.setState(ProcessInstanceState.ACTIVE);
      case TERMINATED -> flowNodeInstances.setState(ProcessInstanceState.TERMINATED);
      case FAILED -> flowNodeInstances.setState(ProcessInstanceState.FAILED);
      case FINISHED -> flowNodeInstances.setState(ProcessInstanceState.COMPLETED);
    }
  }
}
