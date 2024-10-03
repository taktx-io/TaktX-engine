package nl.qunit.bpmnmeister.pd.model;

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import nl.qunit.bpmnmeister.pi.FlowNodeStates2;
import nl.qunit.bpmnmeister.pi.instances.ActivityInstance;
import nl.qunit.bpmnmeister.pi.instances.FLowNodeInstance;
import nl.qunit.bpmnmeister.pi.instances.MultiInstanceInstance;

@Getter
@NoArgsConstructor
@SuperBuilder
public abstract class Activity2 extends FlowNode2 implements WithIoMapping {
  private LoopCharacteristics2 loopCharacteristics;
  private InputOutputMapping2 ioMapping;

  @Setter private List<BoundaryEvent2> boundaryEvents;

  @Override
  public final ActivityInstance newInstance(
      FLowNodeInstance parentInstance, FlowNodeStates2 flowNodeStates) {
    if (loopCharacteristics != null && !loopCharacteristics.equals(LoopCharacteristics2.NONE)) {
      return new MultiInstanceInstance(this, parentInstance);
    } else {
      return newActivityInstance(parentInstance);
    }
  }

  public List<BoundaryEvent2> getBoundaryEvents() {
    if (boundaryEvents == null) {
      boundaryEvents = new ArrayList<>();
    }
    return boundaryEvents;
  }

  public void addBoundaryEvent(BoundaryEvent2 boundaryEvent2) {
    getBoundaryEvents().add(boundaryEvent2);
  }

  public abstract ActivityInstance newActivityInstance(FLowNodeInstance parentInstance);
}
