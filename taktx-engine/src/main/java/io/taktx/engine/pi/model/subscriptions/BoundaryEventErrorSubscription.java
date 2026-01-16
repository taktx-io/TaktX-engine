package io.taktx.engine.pi.model.subscriptions;

import io.taktx.engine.pd.model.BoundaryEvent;
import io.taktx.engine.pd.model.EventSignal;
import io.taktx.engine.pi.model.ErrorEventSignal;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@NoArgsConstructor
@Getter
@Setter
public class BoundaryEventErrorSubscription extends AbstractBoundaryEventSubscription {
  private String code;

  public BoundaryEventErrorSubscription(BoundaryEvent boundaryEvent, String code) {
    super(0, boundaryEvent);
    this.code = code;
  }

  @Override
  public boolean matchesEvent(EventSignal event) {
    return event instanceof ErrorEventSignal errorEventSignal
        && code.equals(errorEventSignal.getCode());
  }
}
