package io.taktx.engine.pi.model.subscriptions;

import io.taktx.dto.subscriptions.SubScriptionType;
import io.taktx.engine.pd.model.EventSignal;
import io.taktx.engine.pi.ProcessInstanceProcessingContext;
import io.taktx.engine.pi.model.EscalationEventSignal;
import io.taktx.engine.pi.model.FlowNodeInstance;
import io.taktx.engine.pi.model.Scope;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class EscalationSubscription extends Subscription {
  private String code;

  public EscalationSubscription() {
    setOrder(10);
  }

  public EscalationSubscription(String elementId, String code) {
    super(0, SubScriptionType.STARTING, elementId);
    this.code = code;
  }

  @Override
  public boolean matchesEvent(EventSignal event) {
    return event instanceof EscalationEventSignal escalationEventSignal
        && code.equals(escalationEventSignal.getCode());
  }

  @Override
  public void cancel(
      ProcessInstanceProcessingContext processInstanceProcessingContext,
      Scope scope,
      FlowNodeInstance<?> instance) {
    // No specific cancellation logic for escalation subscription
  }
}
