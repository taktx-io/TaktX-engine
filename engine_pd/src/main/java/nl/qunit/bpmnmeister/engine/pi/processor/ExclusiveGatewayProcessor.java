package nl.qunit.bpmnmeister.engine.pi.processor;

import jakarta.enterprise.context.ApplicationScoped;
import nl.qunit.bpmnmeister.engine.pi.TriggerResult;
import nl.qunit.bpmnmeister.pd.model.ExclusiveGateway;
import nl.qunit.bpmnmeister.pd.model.ProcessDefinition;
import nl.qunit.bpmnmeister.pi.ProcessInstanceTrigger;
import nl.qunit.bpmnmeister.pi.state.ExclusiveGatewayState;
import nl.qunit.bpmnmeister.pi.state.StateEnum;
import org.jboss.logging.Logger;

@ApplicationScoped
public class ExclusiveGatewayProcessor
    extends StateProcessor<ExclusiveGateway, ExclusiveGatewayState> {
  private static final Logger LOG = Logger.getLogger(ExclusiveGatewayProcessor.class);

  @Override
  protected TriggerResult triggerWhenActive(
      ProcessInstanceTrigger trigger,
      ProcessDefinition processDefinition,
      ExclusiveGateway element,
      ExclusiveGatewayState oldState) {
    LOG.info(
        "Triggering ExclusiveGateway in Active state "
            + element.getId()
            + " for process definition "
            + processDefinition
            + " in process instance"
            + trigger.getProcessInstanceKey());
    return new TriggerResult(
        ExclusiveGatewayState.builder().state(StateEnum.FINISHED).build(), element.getOutgoing());
  }

  @Override
  protected TriggerResult triggerWhenInit(
      ProcessInstanceTrigger trigger,
      ProcessDefinition processDefinition,
      ExclusiveGateway element,
      ExclusiveGatewayState oldState) {
    LOG.info(
        "Triggering ExclusiveGateway in Init state "
            + element.getId()
            + " for process definition "
            + processDefinition
            + " in process instance"
            + trigger.getProcessInstanceKey());
    return new TriggerResult(
        ExclusiveGatewayState.builder().state(StateEnum.ACTIVE).build(), element.getOutgoing());
  }

  @Override
  public ExclusiveGatewayState initialState() {
    return ExclusiveGatewayState.builder().state(StateEnum.INIT).build();
  }
}
