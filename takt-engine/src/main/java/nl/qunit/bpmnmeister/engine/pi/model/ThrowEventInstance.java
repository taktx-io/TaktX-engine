package nl.qunit.bpmnmeister.engine.pi.model;

import lombok.NoArgsConstructor;
import nl.qunit.bpmnmeister.engine.pd.model.ThrowEvent;

@NoArgsConstructor
public abstract class ThrowEventInstance<N extends ThrowEvent> extends EventInstance<N> {

  protected ThrowEventInstance(FLowNodeInstance<?> parentInstance, N flowNode) {
    super(parentInstance, flowNode);
  }
}
