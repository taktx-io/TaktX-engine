package nl.qunit.bpmnmeister.pd.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import nl.qunit.bpmnmeister.pi.instances.ActivityInstance;
import nl.qunit.bpmnmeister.pi.instances.FLowNodeInstance;
import nl.qunit.bpmnmeister.pi.instances.MultiInstanceInstance;

@Getter
@NoArgsConstructor
@SuperBuilder
public abstract class Activity2 extends FlowNode2 implements WithIoMapping {
  private LoopCharacteristics2 loopCharacteristics;
  private InputOutputMapping2 ioMapping;

  @Override
  public final ActivityInstance newInstance(FLowNodeInstance parentInstance) {
    if (loopCharacteristics != null && !loopCharacteristics.equals(LoopCharacteristics2.NONE)) {
      return new MultiInstanceInstance(this, parentInstance);
    } else {
      return newActivityInstance(parentInstance);
    }
  }

  public abstract ActivityInstance newActivityInstance(FLowNodeInstance parentInstance);
}
