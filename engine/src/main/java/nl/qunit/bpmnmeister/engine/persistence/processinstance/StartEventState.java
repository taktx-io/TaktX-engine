package nl.qunit.bpmnmeister.engine.persistence.processinstance;

import java.util.Set;
import nl.qunit.bpmnmeister.engine.persistence.processdefinition.BpmnElement;

public record StartEventState(StateEnum state) implements BpmnElementState {
  @Override
  public TriggerResult trigger(Trigger trigger, BpmnElement bpmnElement) {
    return new TriggerResult(
        new StartEventState(StateEnum.FINISHED), bpmnElement.outputFlows(), Set.of());
  }
}
