package io.taktx.engine.pi.model.subscriptions;

import io.taktx.dto.subscriptions.SubScriptionType;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@NoArgsConstructor
@Setter
@Getter
public abstract class Subscription implements ISubscription {
  private int order;

  private SubScriptionType subScriptionType;

  private String elementId; // In case of starting

  protected Subscription(int order, SubScriptionType subScriptionType, String elementId) {
    this.order = order;
    this.subScriptionType = subScriptionType;
    this.elementId = elementId;
  }

  @Override
  public int order() {
    return order;
  }
}
