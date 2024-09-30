package nl.qunit.bpmnmeister.pi.instances;

import lombok.NoArgsConstructor;
import nl.qunit.bpmnmeister.pd.model.Event2;

@NoArgsConstructor
public abstract class EventInstance<N extends Event2> extends FLowNodeInstance<N> {

  protected EventInstance(FLowNodeInstance parentInstance, N flowNode) {
    super(parentInstance, flowNode);
  }
}
