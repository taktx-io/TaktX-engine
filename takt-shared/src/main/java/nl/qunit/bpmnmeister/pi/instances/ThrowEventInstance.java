package nl.qunit.bpmnmeister.pi.instances;

import lombok.NoArgsConstructor;
import nl.qunit.bpmnmeister.pd.model.ThrowEvent;

@NoArgsConstructor
public abstract class ThrowEventInstance<N extends ThrowEvent> extends EventInstance<N> {

  protected ThrowEventInstance(FLowNodeInstance<?> parentInstance, N flowNode) {
    super(parentInstance, flowNode);
  }
}
