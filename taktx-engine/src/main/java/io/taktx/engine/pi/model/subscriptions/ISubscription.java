package io.taktx.engine.pi.model.subscriptions;

import io.taktx.engine.pd.model.EventSignal;
import io.taktx.engine.pi.model.FlowNodeInstance;
import io.taktx.engine.pi.model.IFlowNodeInstance;
import io.taktx.engine.pi.model.Scope;
import io.taktx.engine.pi.model.VariableScope;

public interface ISubscription {

  void process(
      Scope scope,
      VariableScope variableScope,
      EventSignal event,
      FlowNodeInstance<?> parentInstance);

  boolean matchesEvent(EventSignal event);

  int order();

  void cancel(Scope scope, IFlowNodeInstance fni);
}
