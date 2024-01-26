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
    LOG.info(
        "Triggering start event in Init state "
            + element.getId()
            + " for process definition "
            + processDefinition
            + " in process instance"
            + trigger.getProcessInstanceKey());
    return trigger(trigger, element);
  }

  @Override
  protected TriggerResult triggerWhenActive(
      ProcessInstanceTrigger trigger,
      ProcessDefinition processDefinition,
      StartEvent element,
      StartEventState oldState) {
    LOG.info(
        "Triggering start event in Active state "
            + element.getId()
            + " for process definition "
            + processDefinition
            + " in process instance"
            + trigger.getProcessInstanceKey());

    return trigger(trigger, element);
  }

  private static TriggerResult trigger(ProcessInstanceTrigger trigger, StartEvent element) {
    return new TriggerResult(
        StartEventState.builder().state(StateEnum.ACTIVE).build(), element.getOutgoing());
  }

  @Override
  public StartEventState initialState() {
    return StartEventState.builder().state(StateEnum.INIT).build();
  }
}
