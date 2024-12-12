package com.flomaestro.engine.pi.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class ErrorSubscription {
  private String name;
  private String code;

  public ErrorSubscription(String name, String code) {
    this.name = name;
    this.code = code;
  }

  public boolean matchesEvent(ErrorEventSignal event) {
    return name.equals(event.getName()) && code.equals(event.getCode());
  }
}
