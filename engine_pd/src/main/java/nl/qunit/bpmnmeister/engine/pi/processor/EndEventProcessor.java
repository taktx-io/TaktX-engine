package nl.qunit.bpmnmeister.engine.pi.processor;

import jakarta.enterprise.context.ApplicationScoped;
import java.util.Set;
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
    return new TriggerResult(EndEventState.builder().state(StateEnum.FINISHED).build(), Set.of());
  }

  @Override
  protected TriggerResult triggerWhenActive(
      ProcessInstanceTrigger trigger,
      ProcessDefinition processDefinition,
      EndEvent element,
      EndEventState oldState) {
    LOG.info(
        "Triggering EndEvent in active state "
            + element.getId()
            + " for process definition "
            + processDefinition
            + " in process instance"
            + trigger.getProcessInstanceKey());
    return new TriggerResult(EndEventState.builder().state(StateEnum.FINISHED).build(), Set.of());
  }

  @Override
  public EndEventState initialState() {
    return EndEventState.builder().state(StateEnum.INIT).build();
  }
}
