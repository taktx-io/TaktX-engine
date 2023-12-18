package nl.qunit.bpmnmeister.model.processinstance;

import java.util.Set;
import nl.qunit.bpmnmeister.model.processdefinition.BpmnElement;

public record StartEventState(StateEnum state) implements BpmnElementState {
  @Override
  public TriggerResult trigger(Trigger trigger, BpmnElement bpmnElement) {
    return new TriggerResult(
        new StartEventState(StateEnum.FINISHED), bpmnElement.outputFlows(), Set.of());
  }
}
