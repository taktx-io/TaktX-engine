package nl.qunit.bpmnmeister.model.processinstance;

import java.util.Set;
import nl.qunit.bpmnmeister.model.processdefinition.BpmnElement;

public record TaskState() implements BpmnElementState {
  @Override
  public TriggerResult trigger(Trigger trigger, BpmnElement bpmnElement) {
    return new TriggerResult(new TaskState(), bpmnElement.outputFlows(), Set.of());
  }
}
