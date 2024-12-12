package com.flomaestro.engine.pi.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class EscalationSubscription {
  private String name;
  private String code;

  public boolean matchesEvent(EscalationEventSignal event) {
    return name.equals(event.getName()) && code.equals(event.getCode());
  }
}
