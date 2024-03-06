package nl.qunit.bpmnmeister.engine.pi.processor;

import jakarta.enterprise.context.ApplicationScoped;
import nl.qunit.bpmnmeister.engine.pi.TriggerResult;
import nl.qunit.bpmnmeister.pd.model.ProcessDefinition;
import nl.qunit.bpmnmeister.pd.model.StartEvent;
import nl.qunit.bpmnmeister.pi.ProcessInstanceTrigger;
import nl.qunit.bpmnmeister.pi.state.StartEventState;
import nl.qunit.bpmnmeister.pi.state.StateEnum;
import org.jboss.logging.Logger;

@ApplicationScoped
public class StartEventProcessor extends StateProcessor<StartEvent, StartEventState> {
  private static final Logger LOG = Logger.getLogger(StartEventProcessor.class);

  @Override
  protected TriggerResult triggerWhenInit(
      ProcessInstanceTrigger trigger,
      ProcessDefinition processDefinition,
      StartEvent element,
      StartEventState oldState) {
    return trigger(trigger, element);
  }

  @Override
  protected TriggerResult triggerWhenFinished(
      ProcessInstanceTrigger trigger,
      ProcessDefinition processDefinition,
      StartEvent element,
      StartEventState oldState) {
    throw new IllegalStateException("StartEvent cannot be in active state");
  }

  @Override
  protected TriggerResult triggerWhenWaiting(
      ProcessInstanceTrigger trigger,
      ProcessDefinition processDefinition,
      StartEvent element,
      StartEventState oldState) {
    throw new IllegalStateException("StartEvent cannot be in active state");
  }

  @Override
  protected TriggerResult triggerWhenActive(
      ProcessInstanceTrigger trigger,
      ProcessDefinition processDefinition,
      StartEvent element,
      StartEventState oldState) {
    throw new IllegalStateException("StartEvent cannot be in active state");
  }

  private static TriggerResult trigger(ProcessInstanceTrigger trigger, StartEvent element) {
    return TriggerResult.builder()
        .newElementState(StartEventState.builder().state(StateEnum.FINISHED).build())
        .newActiveFlows(element.getOutgoing())
        .build();
  }

  @Override
  public StartEventState initialState() {
    return StartEventState.builder().state(StateEnum.INIT).build();
  }
}
