package io.taktx.engine.pi.model.subscriptions;

import io.taktx.engine.pd.model.EventSignal;
import io.taktx.engine.pd.model.StartEvent;
import io.taktx.engine.pd.model.SubProcess;
import io.taktx.engine.pi.model.EscalationEventSignal;
import lombok.NoArgsConstructor;

@NoArgsConstructor
public class EventSubProcessEscalationSubscription extends AbstractEventSubprocessSubscription {

  private String code;

  public EventSubProcessEscalationSubscription(
      SubProcess subProcess, StartEvent startEvent, String code) {
    super(25, subProcess, startEvent);
    this.code = code;
  }

  @Override
  public boolean matchesEvent(EventSignal event) {
    return event instanceof EscalationEventSignal escalationEventSignal
        && code.equals(escalationEventSignal.getCode());
  }
}
