package nl.qunit.bpmnmeister.pd.model;

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import nl.qunit.bpmnmeister.pi.FlowNodeInstances;
import nl.qunit.bpmnmeister.pi.instances.ActivityInstance;
import nl.qunit.bpmnmeister.pi.instances.FLowNodeInstance;
import nl.qunit.bpmnmeister.pi.instances.MultiInstanceInstance;

@Getter
@NoArgsConstructor
@SuperBuilder
public abstract class Activity extends FlowNode implements WithIoMapping {
  private LoopCharacteristics loopCharacteristics;
  private InputOutputMapping ioMapping;

  @Setter private List<BoundaryEvent> boundaryEvents;

  @Override
  public final ActivityInstance<?> newInstance(
      FLowNodeInstance<?> parentInstance, FlowNodeInstances flowNodeInstances) {
    if (loopCharacteristics != null && !loopCharacteristics.equals(LoopCharacteristics.NONE)) {
      return new MultiInstanceInstance(this, parentInstance);
    } else {
      return newActivityInstance(parentInstance);
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

  public abstract ActivityInstance<?> newActivityInstance(FLowNodeInstance<?> parentInstance);
}
