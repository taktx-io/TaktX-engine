package nl.qunit.bpmnmeister.engine.pi.processor;

import jakarta.enterprise.context.ApplicationScoped;
import java.util.Set;
import java.util.UUID;
import nl.qunit.bpmnmeister.engine.pi.TriggerResult;
import nl.qunit.bpmnmeister.pd.model.StartEvent;
import nl.qunit.bpmnmeister.pi.FlowElementTrigger;
import nl.qunit.bpmnmeister.pi.ProcessInstance;
import nl.qunit.bpmnmeister.pi.StartThrowingEvent;
import nl.qunit.bpmnmeister.pi.Variables;
import nl.qunit.bpmnmeister.pi.state.StartEventState;
import org.jboss.logging.Logger;

@ApplicationScoped
public class StartEventProcessor extends EventProcessor<StartEvent, StartEventState> {
  private static final Logger LOG = Logger.getLogger(StartEventProcessor.class);

  @Override
  protected TriggerResult triggerEvent(
      FlowElementTrigger trigger,
      ProcessInstance processInstance,
      StartEvent element,
      StartEventState oldState) {
    return new TriggerResult(
        new StartEventState(oldState.getElementInstanceId(), oldState.getPassedCnt() + 1),
        element.getOutgoing(),
        Set.of(),
        Set.of(),
        new StartThrowingEvent(),
        Variables.EMPTY);
  }

  @Override
  public StartEventState initialState() {
    return new StartEventState(UUID.randomUUID(), 0);
  }
}
