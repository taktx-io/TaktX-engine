package nl.qunit.bpmnmeister.engine.pi.processor;

import jakarta.enterprise.context.ApplicationScoped;
import java.util.UUID;
import nl.qunit.bpmnmeister.engine.pi.TriggerResult;
import nl.qunit.bpmnmeister.pd.model.StartEvent;
import nl.qunit.bpmnmeister.pi.ProcessInstance;
import nl.qunit.bpmnmeister.pi.ProcessInstanceTrigger;
import nl.qunit.bpmnmeister.pi.state.StartEventState;
import nl.qunit.bpmnmeister.pi.state.StateEnum;
import org.jboss.logging.Logger;

@ApplicationScoped
public class StartEventProcessor extends EventProcessor<StartEvent, StartEventState> {
  private static final Logger LOG = Logger.getLogger(StartEventProcessor.class);

  @Override
  protected TriggerResult triggerEvent(
      ProcessInstanceTrigger trigger,
      ProcessInstance processInstance,
      StartEvent element,
      StartEventState oldState) {
    return TriggerResult.builder()
        .newElementState(new StartEventState(StateEnum.FINISHED, oldState.getElementInstanceId()))
        .newActiveFlows(element.getOutgoing())
        .build();
  }

  @Override
  public StartEventState initialState() {
    return new StartEventState(StateEnum.INIT, UUID.randomUUID());
  }

  @Override
  public StartEventState terminate(StartEventState oldState) {
    return new StartEventState(StateEnum.TERMINATED, oldState.getElementInstanceId());
  }
}
