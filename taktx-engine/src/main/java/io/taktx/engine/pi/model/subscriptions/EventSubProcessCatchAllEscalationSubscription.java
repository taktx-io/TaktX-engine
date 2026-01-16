package io.taktx.engine.pi.model.subscriptions;

import io.taktx.engine.pd.model.EventSignal;
import io.taktx.engine.pd.model.StartEvent;
import io.taktx.engine.pd.model.SubProcess;
import io.taktx.engine.pi.model.EscalationEventSignal;
import lombok.NoArgsConstructor;

@NoArgsConstructor
public class EventSubProcessCatchAllEscalationSubscription
    extends AbstractEventSubprocessSubscription {
  public EventSubProcessCatchAllEscalationSubscription(
      SubProcess subProcess, StartEvent startEvent) {
    super(30, subProcess, startEvent);
  }

  @Override
  public boolean matchesEvent(EventSignal event) {
    return event instanceof EscalationEventSignal
        && eventOriginatesFromSubProcess(event, getEventSubProcess());
  }
}
