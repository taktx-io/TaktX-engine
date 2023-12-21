package nl.qunit.bpmnmeister.engine.persistence.processinstance;

import java.util.Set;
import nl.qunit.bpmnmeister.engine.persistence.processdefinition.BpmnElement;

public record TaskState(StateEnum state, int cnt) implements BpmnElementState {
  @Override
  public TriggerResult trigger(Trigger trigger, BpmnElement bpmnElement) {
    return new TriggerResult(
        new TaskState(StateEnum.FINISHED, cnt + 1), bpmnElement.getOutputFlows(), Set.of());
  }
}
