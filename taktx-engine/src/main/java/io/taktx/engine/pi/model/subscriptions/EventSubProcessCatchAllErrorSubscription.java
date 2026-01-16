package io.taktx.engine.pi.model.subscriptions;

import io.taktx.engine.pd.model.EventSignal;
import io.taktx.engine.pd.model.StartEvent;
import io.taktx.engine.pd.model.SubProcess;
import io.taktx.engine.pi.model.ErrorEventSignal;
import lombok.NoArgsConstructor;

@NoArgsConstructor
public class EventSubProcessCatchAllErrorSubscription extends AbstractEventSubprocessSubscription {
  public EventSubProcessCatchAllErrorSubscription(
      SubProcess eventSubprocess, StartEvent startEvent) {
    super(30, eventSubprocess, startEvent);
  }

  @Override
  public boolean matchesEvent(EventSignal event) {
    return event instanceof ErrorEventSignal
        && eventOriginatesFromSubProcess(event, getEventSubProcess());
  }
}
