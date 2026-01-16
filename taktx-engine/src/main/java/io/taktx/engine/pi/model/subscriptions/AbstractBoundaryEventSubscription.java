package io.taktx.engine.pi.model.subscriptions;

import io.taktx.engine.pd.model.BoundaryEvent;
import io.taktx.engine.pd.model.EventSignal;
import io.taktx.engine.pi.model.BoundaryEventInstance;
import io.taktx.engine.pi.model.FlowNodeInstance;
import io.taktx.engine.pi.model.IFlowNodeInstance;
import io.taktx.engine.pi.model.Scope;
import io.taktx.engine.pi.model.StartFlowNodeInstanceInfo;
import io.taktx.engine.pi.model.VariableScope;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public abstract class AbstractBoundaryEventSubscription extends Subscription {
  private int order;
  private BoundaryEvent boundaryEvent;

  @Override
  public void process(
      Scope scope,
      VariableScope variableScope,
      EventSignal event,
      FlowNodeInstance<?> parentInstance) {
    BoundaryEventInstance boundaryEventInstance =
        boundaryEvent.newInstance(scope.getParentFlowNodeInstance(), scope);

    VariableScope childVariableScope = variableScope.selectChildScope(boundaryEventInstance);
    StartFlowNodeInstanceInfo startFlowNodeInstanceInfo =
        new StartFlowNodeInstanceInfo(boundaryEventInstance, null, childVariableScope);
    scope.getDirectInstanceResult().addNewFlowNodeInstance(startFlowNodeInstanceInfo);

    if (boundaryEvent.isCancelActivity()) {
      // Aborting the parent instance will also cancel the subscriptions for the other boundary
      // events
      scope.getDirectInstanceResult().addAbortInstance(parentInstance);
    }
  }

  @Override
  public int order() {
    return order;
  }

  @Override
  public void cancel(Scope scope, IFlowNodeInstance fni) {
    // No-op for escalation events
  }
}
