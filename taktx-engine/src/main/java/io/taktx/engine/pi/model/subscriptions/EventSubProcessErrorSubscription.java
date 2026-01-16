package io.taktx.engine.pi.model.subscriptions;

import io.taktx.engine.pd.model.EventSignal;
import io.taktx.engine.pd.model.StartEvent;
import io.taktx.engine.pd.model.SubProcess;
import io.taktx.engine.pi.model.ErrorEventSignal;
import io.taktx.engine.pi.model.FlowNodeInstance;
import io.taktx.engine.pi.model.Scope;
import io.taktx.engine.pi.model.VariableScope;
import lombok.NoArgsConstructor;

@NoArgsConstructor
public class EventSubProcessErrorSubscription extends AbstractEventSubprocessSubscription {

  private String code;

  public EventSubProcessErrorSubscription(
      SubProcess subProcess, StartEvent startEvent, String code) {
    super(25, subProcess, startEvent);
    this.code = code;
  }

  @Override
  public void process(
      Scope scope,
      VariableScope variableScope,
      EventSignal event,
      FlowNodeInstance<?> parentInstance) {}

  @Override
  public boolean matchesEvent(EventSignal event) {
    return event instanceof ErrorEventSignal errorEventSignal
        && code.equals(errorEventSignal.getCode())
        && eventOriginatesFromSubProcess(event, getEventSubProcess());
  }
}
