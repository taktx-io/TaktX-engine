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
import nl.qunit.bpmnmeister.pi.state.ActivityStateEnum;
import nl.qunit.bpmnmeister.pi.state.ParallelGatewayState;
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
    if (element.getOutgoing().equals(newTriggeredFlows)) {
      newTriggeredFlows.clear();
      outputFlows.addAll(element.getOutgoing());
    }
    return new TriggerResult(
        new ParallelGatewayState(UUID.randomUUID(), newTriggeredFlows),
        outputFlows,
        Set.of(),
        Set.of(),
        Variables.EMPTY);
  }

  @Override
  public ParallelGatewayState initialState() {
    return new ParallelGatewayState(UUID.randomUUID(), new HashSet<>());
  }

  @Override
  public ParallelGatewayState terminate(ParallelGatewayState oldState) {
    return new ParallelGatewayState(oldState.getElementInstanceId(), oldState.getTriggeredFlows());
  }
}
