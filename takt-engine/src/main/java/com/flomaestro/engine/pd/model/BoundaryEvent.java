package com.flomaestro.engine.pd.model;

import com.flomaestro.engine.pi.model.BoundaryEventInstance;
import com.flomaestro.engine.pi.model.FlowNodeInstance;
import com.flomaestro.engine.pi.model.FlowNodeInstances;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Getter
@SuperBuilder
public class BoundaryEvent extends CatchEvent {
  private String attachedToRef;
  @Setter private FlowNode attachedActivity;

  private boolean cancelActivity;

  @Override
  public BoundaryEventInstance newInstance(
      FlowNodeInstance<?> parentInstance, FlowNodeInstances flowNodeInstances) {
    return new BoundaryEventInstance(parentInstance, this, flowNodeInstances.nextElementInstanceId());
  }
}
