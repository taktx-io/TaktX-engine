package com.flomaestro.engine.pd.model;

import com.flomaestro.engine.pi.model.ActivityInstance;
import com.flomaestro.engine.pi.model.FlowNodeInstance;
import com.flomaestro.engine.pi.model.FlowNodeInstances;
import com.flomaestro.engine.pi.model.MultiInstanceInstance;
import com.flomaestro.takt.dto.v_1_0_0.ActtivityStateEnum;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Getter
@NoArgsConstructor
@SuperBuilder
public abstract class Activity extends FlowNode implements WithIoMapping {
  private LoopCharacteristics loopCharacteristics;
  private InputOutputMapping ioMapping;

  @Setter private List<BoundaryEvent> boundaryEvents;

  @Override
  public final ActivityInstance<?> newInstance(
      FlowNodeInstance<?> parentInstance, FlowNodeInstances flowNodeInstances) {
    if (loopCharacteristics != null && !loopCharacteristics.equals(LoopCharacteristics.NONE)) {
      MultiInstanceInstance multiInstanceInstance =
          new MultiInstanceInstance(
              this, parentInstance, flowNodeInstances.nextElementInstanceId());
      return multiInstanceInstance;
    } else {
      ActivityInstance<?> activityInstance =
          newActivityInstance(parentInstance, flowNodeInstances.nextElementInstanceId());
      activityInstance.setState(ActtivityStateEnum.INITIAL);
      return activityInstance;
    }
  }

  public List<BoundaryEvent> getBoundaryEvents() {
    if (boundaryEvents == null) {
      boundaryEvents = new ArrayList<>();
    }
    return boundaryEvents;
  }

  public void addBoundaryEvent(BoundaryEvent boundaryEvent) {
    getBoundaryEvents().add(boundaryEvent);
  }

  public abstract ActivityInstance<?> newActivityInstance(
      FlowNodeInstance<?> parentInstance, long elementInstanceId);
}
