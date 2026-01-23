package io.taktx.engine.pi.model.subscriptions;

import io.taktx.dto.subscriptions.SubScriptionType;
import io.taktx.engine.pd.model.EventSignal;
import io.taktx.engine.pi.ProcessInstanceProcessingContext;
import io.taktx.engine.pi.model.ErrorEventSignal;
import io.taktx.engine.pi.model.FlowNodeInstance;
import io.taktx.engine.pi.model.Scope;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ErrorSubscription extends Subscription {
  private String code;

  public ErrorSubscription() {
    setOrder(0);
  }

  public ErrorSubscription(String elementId, String code) {
    super(0, SubScriptionType.STARTING, elementId);
    this.code = code;
  }

  @Override
  public boolean matchesEvent(EventSignal event) {
    return event instanceof ErrorEventSignal errorEventSignal
        && code.equals(errorEventSignal.getCode());
  }

  @Override
  public void cancel(
      ProcessInstanceProcessingContext processInstanceProcessingContext,
      Scope scope,
      FlowNodeInstance<?> instance) {
    // No specific cancellation logic for error subscription
  }
}
