package nl.qunit.bpmnmeister.model.processinstance;

import java.util.Set;
import nl.qunit.bpmnmeister.model.processdefinition.BpmnElement;

public record ExclusiveGatewayState() implements BpmnElementState {
  @Override
  public TriggerResult trigger(Trigger trigger, BpmnElement bpmnElement) {
    return new TriggerResult(new ExclusiveGatewayState(), bpmnElement.outputFlows(), Set.of());
  }
}
