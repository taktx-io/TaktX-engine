package nl.qunit.bpmnmeister.pd.model;

import nl.qunit.bpmnmeister.pi.instances.FLowNodeInstance;

public class ErrorSignal extends EventSignal {

  private final String message;

  public ErrorSignal(FLowNodeInstance<?> throwingInstance, String name, String message) {
    super(throwingInstance, name);
    this.message = message;
  }

  @Override
  public boolean bubbleUp() {
    return true;
  }
}
