package nl.qunit.bpmnmeister.model.processinstance;

import java.util.HashSet;
import java.util.Set;
import nl.qunit.bpmnmeister.model.processdefinition.BpmnElement;
import nl.qunit.bpmnmeister.model.processdefinition.ParallelGateway;

public record ParallelGatewayState(Set<String> triggeredFlows) implements BpmnElementState {
  @Override
  public TriggerResult trigger(Trigger trigger, BpmnElement bpmnElement) {
    Set<String> newTriggeredFlows = new HashSet<>(triggeredFlows);
    newTriggeredFlows.add(trigger.inputFlowId());
    final Set<String> outputFlows = new HashSet<>();
    if (bpmnElement instanceof ParallelGateway parallelGateway
        && (parallelGateway.inputFlows().equals(newTriggeredFlows))) {
      newTriggeredFlows.clear();
      outputFlows.addAll(parallelGateway.outputFlows());
    }
    return new TriggerResult(new ParallelGatewayState(newTriggeredFlows), outputFlows, Set.of());
  }
}
