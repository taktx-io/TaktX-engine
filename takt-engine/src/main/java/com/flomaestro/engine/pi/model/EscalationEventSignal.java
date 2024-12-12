package com.flomaestro.engine.pi.model;

import com.flomaestro.engine.pd.model.EventSignal;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

@Getter
@SuperBuilder
public class EscalationEventSignal extends EventSignal {

  private final String message;
  private final String code;

  public EscalationEventSignal(
      FlowNodeInstance<?> fLowNodeInstance, String name, String code, String message) {
    super(fLowNodeInstance, name);
    this.message = message;
    this.code = code;
  }

  @Override
  public boolean bubbleUp() {
    return true;
  }
}
