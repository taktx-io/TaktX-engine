package nl.qunit.bpmnmeister.engine.pi.model;

import lombok.NoArgsConstructor;
import nl.qunit.bpmnmeister.engine.pd.model.Event;

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
