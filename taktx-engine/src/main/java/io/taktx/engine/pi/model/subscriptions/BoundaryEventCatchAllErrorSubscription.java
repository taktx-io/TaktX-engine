package io.taktx.engine.pi.model.subscriptions;

import io.taktx.engine.pd.model.BoundaryEvent;
import io.taktx.engine.pd.model.EventSignal;
import io.taktx.engine.pi.model.ErrorEventSignal;
import lombok.NoArgsConstructor;

@NoArgsConstructor
public class BoundaryEventCatchAllErrorSubscription extends AbstractBoundaryEventSubscription {
  public BoundaryEventCatchAllErrorSubscription(BoundaryEvent boundaryEvent) {
    super(10, boundaryEvent);
  }

  @Override
  public boolean matchesEvent(EventSignal event) {
    return event instanceof ErrorEventSignal;
  }
}
