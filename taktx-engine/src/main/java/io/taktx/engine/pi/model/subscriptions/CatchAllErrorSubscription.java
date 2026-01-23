package io.taktx.engine.pi.model.subscriptions;

import io.taktx.dto.subscriptions.SubScriptionType;
import io.taktx.engine.pd.model.EventSignal;
import io.taktx.engine.pi.ProcessInstanceProcessingContext;
import io.taktx.engine.pi.model.ErrorEventSignal;
import io.taktx.engine.pi.model.FlowNodeInstance;
import io.taktx.engine.pi.model.Scope;

public class CatchAllErrorSubscription extends Subscription {
  public CatchAllErrorSubscription() {
    setOrder(30);
  }

  public CatchAllErrorSubscription(String elementId) {
    super(30, SubScriptionType.STARTING, elementId);
  }

  @Override
  public boolean matchesEvent(EventSignal event) {
    return event instanceof ErrorEventSignal;
  }

  @Override
  public void cancel(
      ProcessInstanceProcessingContext processInstanceProcessingContext,
      Scope scope,
      FlowNodeInstance<?> instance) {
    // No specific cancellation logic for catch-all error subscription
  }
}
