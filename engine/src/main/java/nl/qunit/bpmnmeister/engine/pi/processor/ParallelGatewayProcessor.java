package nl.qunit.bpmnmeister.engine.pi.processor;

import jakarta.enterprise.context.ApplicationScoped;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import nl.qunit.bpmnmeister.engine.pi.TriggerResult;
import nl.qunit.bpmnmeister.pd.model.BaseElementId;
import nl.qunit.bpmnmeister.pd.model.ParallelGateway;
import nl.qunit.bpmnmeister.pi.ProcessInstance;
import nl.qunit.bpmnmeister.pi.ProcessInstanceTrigger;
import nl.qunit.bpmnmeister.pi.Variables;
import nl.qunit.bpmnmeister.pi.state.ExclusiveGatewayState;
import nl.qunit.bpmnmeister.pi.state.ParallelGatewayState;
import nl.qunit.bpmnmeister.pi.state.StateEnum;
import org.jboss.logging.Logger;

@ApplicationScoped
public class ParallelGatewayProcessor
    extends GatewayProcessor<ParallelGateway, ParallelGatewayState> {
  private static final Logger LOG = Logger.getLogger(ParallelGatewayProcessor.class);

  @Override
  protected TriggerResult triggerDecision(
      ProcessInstanceTrigger trigger,
      ProcessInstance processInstance,
      ParallelGateway element,
      ParallelGatewayState oldState) {
    Set<BaseElementId> newTriggeredFlows = new HashSet<>(oldState.getTriggeredFlows());
    newTriggeredFlows.add(trigger.getInputFlowId());
    final Set<BaseElementId> outputFlows = new HashSet<>();
    StateEnum newState = StateEnum.ACTIVE;
    if (element.getOutgoing().equals(newTriggeredFlows)) {
      newState = StateEnum.INIT;
      newTriggeredFlows.clear();
      outputFlows.addAll(element.getOutgoing());
    }
    return new TriggerResult(
        new ParallelGatewayState(newState, UUID.randomUUID(), newTriggeredFlows),
        outputFlows,
        Set.of(),
        Set.of(),
        Variables.EMPTY);
  }

  @Override
  public ParallelGatewayState initialState() {
    return new ParallelGatewayState(StateEnum.INIT, UUID.randomUUID(), new HashSet<>());
  }

  @Override
  public ParallelGatewayState terminate(ParallelGatewayState oldState) {
    return new ParallelGatewayState(
        StateEnum.TERMINATED, oldState.getElementInstanceId(), oldState.getTriggeredFlows());
  }
}
