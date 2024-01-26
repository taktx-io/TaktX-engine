package nl.qunit.bpmnmeister.engine.pi.processor;

import jakarta.enterprise.context.ApplicationScoped;
import java.util.HashSet;
import java.util.Set;
import nl.qunit.bpmnmeister.engine.pi.TriggerResult;
import nl.qunit.bpmnmeister.pd.model.ParallelGateway;
import nl.qunit.bpmnmeister.pd.model.ProcessDefinition;
import nl.qunit.bpmnmeister.pi.ProcessInstanceTrigger;
import nl.qunit.bpmnmeister.pi.state.ParallelGatewayState;
import nl.qunit.bpmnmeister.pi.state.StateEnum;
import org.jboss.logging.Logger;

@ApplicationScoped
public class ParallelGatewayProcessor
    extends StateProcessor<ParallelGateway, ParallelGatewayState> {
  private static final Logger LOG = Logger.getLogger(ParallelGatewayProcessor.class);

  @Override
  protected TriggerResult triggerWhenActive(
      ProcessInstanceTrigger trigger,
      ProcessDefinition processDefinition,
      ParallelGateway element,
      ParallelGatewayState oldState) {
    LOG.info(
        "Triggering ParallelGateway in Active state "
            + element.getId()
            + " for process definition "
            + processDefinition
            + " in process instance"
            + trigger.getProcessInstanceKey());
    return getTriggerResult(trigger, element, oldState);
  }

  @Override
  protected TriggerResult triggerWhenInit(
      ProcessInstanceTrigger trigger,
      ProcessDefinition processDefinition,
      ParallelGateway element,
      ParallelGatewayState oldState) {
    LOG.info(
        "Triggering ParallelGateway in Init state "
            + element.getId()
            + " for process definition "
            + processDefinition
            + " in process instance"
            + trigger.getProcessInstanceKey());
    return getTriggerResult(trigger, element, oldState);
  }

  private static TriggerResult getTriggerResult(
      ProcessInstanceTrigger trigger, ParallelGateway element, ParallelGatewayState oldState) {
    Set<String> newTriggeredFlows = new HashSet<>(oldState.getTriggeredFlows());
    newTriggeredFlows.add(trigger.getInputFlowId());
    final Set<String> outputFlows = new HashSet<>();
    StateEnum newState = StateEnum.ACTIVE;
    if (element.getOutgoing().equals(newTriggeredFlows)) {
      newState = StateEnum.INIT;
      newTriggeredFlows.clear();
      outputFlows.addAll(element.getOutgoing());
    }
    return new TriggerResult(
        ParallelGatewayState.builder().triggeredFlows(newTriggeredFlows).state(newState).build(),
        outputFlows);
  }

  @Override
  public ParallelGatewayState initialState() {
    return ParallelGatewayState.builder()
        .state(StateEnum.INIT)
        .triggeredFlows(new HashSet<>())
        .build();
  }
}
