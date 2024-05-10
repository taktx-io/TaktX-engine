package nl.qunit.bpmnmeister.engine.pi.processor;

import jakarta.enterprise.context.ApplicationScoped;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import nl.qunit.bpmnmeister.engine.pi.TriggerResult;
import nl.qunit.bpmnmeister.pd.model.ParallelGateway;
import nl.qunit.bpmnmeister.pi.FlowElementTrigger;
import nl.qunit.bpmnmeister.pi.ProcessInstance;
import nl.qunit.bpmnmeister.pi.ThrowingEvent;
import nl.qunit.bpmnmeister.pi.Variables;
import nl.qunit.bpmnmeister.pi.state.ParallelGatewayState;

@ApplicationScoped
public class ParallelGatewayProcessor
    extends GatewayProcessor<ParallelGateway, ParallelGatewayState> {

  @Override
  protected TriggerResult triggerDecision(
      FlowElementTrigger trigger,
      ProcessInstance processInstance,
      ParallelGateway element,
      ParallelGatewayState oldState) {
    Set<String> newTriggeredFlows = new HashSet<>(oldState.getTriggeredFlows());
    newTriggeredFlows.add(trigger.getInputFlowId());
    final Set<String> outputFlows = new HashSet<>();
    if (element.getOutgoing().equals(newTriggeredFlows)) {
      newTriggeredFlows.clear();
      outputFlows.addAll(element.getOutgoing());
    }
    return new TriggerResult(
        new ParallelGatewayState(UUID.randomUUID(), newTriggeredFlows, oldState.getPassedCnt() + 1),
        outputFlows,
        Set.of(),
        Set.of(),
        Set.of(),
        ThrowingEvent.NOOP,
        Set.of(),
        Variables.EMPTY);
  }
}
