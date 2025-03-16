package com.flomaestro.engine.pi.model;

import com.flomaestro.engine.pd.model.EventSignal;
import lombok.Getter;

@Getter
public class ErrorEventSignal extends EventSignal {

  private final String message;
  private final String code;

  public ErrorEventSignal(
      FlowNodeInstance<?> fLowNodeInstance, String name, String code, String message) {
    super(fLowNodeInstance, name);
    this.message = message;
    this.code = code;
  }
}
