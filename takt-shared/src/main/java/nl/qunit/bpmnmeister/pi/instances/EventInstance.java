package nl.qunit.bpmnmeister.pi.instances;

import lombok.NoArgsConstructor;
import nl.qunit.bpmnmeister.pd.model.Event;

@NoArgsConstructor
public abstract class EventInstance<N extends Event> extends FLowNodeInstance<N> {

  protected EventInstance(FLowNodeInstance<?> parentInstance, N flowNode) {
    super(parentInstance, flowNode);
  }

  @Override
  public boolean canSelectNextNodeStart() {
    return isCompleted();
  }

  @Override
  public boolean canSelectNextNodeContinue() {
    return isCompleted();
  }
}
