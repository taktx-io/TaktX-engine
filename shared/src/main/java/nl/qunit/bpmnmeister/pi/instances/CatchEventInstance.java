package nl.qunit.bpmnmeister.pi.instances;

import lombok.NoArgsConstructor;
import nl.qunit.bpmnmeister.pd.model.Event2;

@NoArgsConstructor
public abstract class CatchEventInstance<N extends Event2> extends EventInstance<N> {

  protected CatchEventInstance(FLowNodeInstance parentInstance, N flowNode) {
    super(parentInstance, flowNode);
  }
}
