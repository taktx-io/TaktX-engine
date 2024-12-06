package nl.qunit.bpmnmeister.engine.pi.model;

import lombok.Getter;
import nl.qunit.bpmnmeister.engine.pd.model.EventSignal;

@Getter
public class EscalationEventSignal extends EventSignal {

  private final String message;
  private final String code;

  public EscalationEventSignal(
      FLowNodeInstance<?> fLowNodeInstance, String name, String code, String message) {
    super(fLowNodeInstance, name);
    this.message = message;
    this.code = code;
  }

  @Override
  public boolean bubbleUp() {
    return true;
  }
}
