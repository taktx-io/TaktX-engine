package nl.qunit.bpmnmeister.engine.pi.processor;

import jakarta.enterprise.context.ApplicationScoped;
import nl.qunit.bpmnmeister.engine.pi.TriggerResult;
import nl.qunit.bpmnmeister.pd.model.EndEvent;
import nl.qunit.bpmnmeister.pd.model.ProcessDefinition;
import nl.qunit.bpmnmeister.pi.ProcessInstanceTrigger;
import nl.qunit.bpmnmeister.pi.state.EndEventState;
import nl.qunit.bpmnmeister.pi.state.StateEnum;
import org.jboss.logging.Logger;

@ApplicationScoped
public class EndEventProcessor extends StateProcessor<EndEvent, EndEventState> {
  private static final Logger LOG = Logger.getLogger(EndEventProcessor.class);

  @Override
  protected TriggerResult triggerWhenInit(
      ProcessInstanceTrigger trigger,
      ProcessDefinition processDefinition,
      EndEvent element,
      EndEventState oldState) {
    LOG.info(
        "Triggering EndEvent in init state "
            + element.getId()
            + " for process definition "
            + processDefinition
            + " in process instance"
            + trigger.getProcessInstanceKey());
    return TriggerResult.builder()
        .newElementState(EndEventState.builder().state(StateEnum.INIT).build())
        .build();
  }

  @Override
  protected TriggerResult triggerWhenFinished(
      ProcessInstanceTrigger trigger,
      ProcessDefinition processDefinition,
      EndEvent element,
      EndEventState oldState) {
    throw new IllegalStateException("EndEvent cannot be in finished state");
  }

  @Override
  protected TriggerResult triggerWhenWaiting(
      ProcessInstanceTrigger trigger,
      ProcessDefinition processDefinition,
      EndEvent element,
      EndEventState oldState) {
    throw new IllegalStateException("EndEvent cannot be in waiting state");
  }

  @Override
  protected TriggerResult triggerWhenActive(
      ProcessInstanceTrigger trigger,
      ProcessDefinition processDefinition,
      EndEvent element,
      EndEventState oldState) {
    throw new IllegalStateException("EndEvent cannot be in active state");
  }

  @Override
  public EndEventState initialState() {
    return EndEventState.builder().state(StateEnum.INIT).build();
  }
}
