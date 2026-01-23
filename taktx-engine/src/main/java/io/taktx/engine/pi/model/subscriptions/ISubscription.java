package io.taktx.engine.pi.model.subscriptions;

import io.taktx.engine.pd.model.EventSignal;
import io.taktx.engine.pi.ProcessInstanceProcessingContext;
import io.taktx.engine.pi.model.FlowNodeInstance;
import io.taktx.engine.pi.model.Scope;

public interface ISubscription {

  boolean matchesEvent(EventSignal event);

  int order();

  void cancel(
      ProcessInstanceProcessingContext processInstanceProcessingContext,
      Scope scope,
      FlowNodeInstance<?> instance);
}
