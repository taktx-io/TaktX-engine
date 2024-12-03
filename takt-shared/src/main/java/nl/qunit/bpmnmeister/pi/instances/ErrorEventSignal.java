package nl.qunit.bpmnmeister.pi.instances;

import lombok.Getter;
import nl.qunit.bpmnmeister.pd.model.EventSignal;

@Getter
public class ErrorEventSignal extends EventSignal {

  private final String message;
  private final String code;

  public ErrorEventSignal(
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
