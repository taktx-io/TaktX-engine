package nl.qunit.bpmnmeister.engine.pi.processor;

import jakarta.enterprise.context.ApplicationScoped;
import java.util.Set;
import nl.qunit.bpmnmeister.engine.pi.TriggerResult;
import nl.qunit.bpmnmeister.pd.model.StartEvent;
import nl.qunit.bpmnmeister.pi.ProcessInstance;
import nl.qunit.bpmnmeister.pi.ProcessInstanceTrigger;
import nl.qunit.bpmnmeister.pi.StartThrowingEvent;
import nl.qunit.bpmnmeister.pi.Variables;
import nl.qunit.bpmnmeister.pi.state.StartEventState;

@ApplicationScoped
public class StartEventProcessor extends EventProcessor<StartEvent, StartEventState> {
  @Override
  protected TriggerResult triggerEvent(
      ProcessInstanceTrigger trigger,
      ProcessInstance processInstance,
      StartEvent element,
      StartEventState oldState) {
    return new TriggerResult(
        new StartEventState(oldState.getElementInstanceId(), oldState.getPassedCnt() + 1),
        element.getOutgoing(),
        Set.of(),
        Set.of(),
        Set.of(),
        new StartThrowingEvent(),
        Set.of(),
        Set.of(),
        Variables.EMPTY);
  }
}
