package nl.qunit.bpmnmeister.pi.instances;

import lombok.NoArgsConstructor;
import nl.qunit.bpmnmeister.pd.model.ThrowEvent2;

@NoArgsConstructor
public abstract class ThrowEventInstance<N extends ThrowEvent2> extends EventInstance<N> {

  protected ThrowEventInstance(FLowNodeInstance parentInstance, N flowNode) {
    super(parentInstance, flowNode);
  }
}
