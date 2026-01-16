package io.taktx.engine.pi.model.subscriptions;

import io.taktx.engine.pd.model.BoundaryEvent;
import io.taktx.engine.pd.model.EventSignal;
import io.taktx.engine.pi.model.EscalationEventSignal;
import lombok.NoArgsConstructor;

@NoArgsConstructor
public class BoundaryEventCatchAllEscalationSubscription extends AbstractBoundaryEventSubscription {
  public BoundaryEventCatchAllEscalationSubscription(BoundaryEvent boundaryEvent) {
    super(10, boundaryEvent);
  }

  @Override
  public boolean matchesEvent(EventSignal event) {
    return event instanceof EscalationEventSignal;
  }
}
