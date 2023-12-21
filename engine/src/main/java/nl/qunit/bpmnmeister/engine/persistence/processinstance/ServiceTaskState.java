package nl.qunit.bpmnmeister.engine.persistence.processinstance;

import java.util.HashSet;
import java.util.Set;
import nl.qunit.bpmnmeister.engine.persistence.processdefinition.BpmnElement;

public record ServiceTaskState(StateEnum state, int cnt) implements BpmnElementState {

  @Override
  public TriggerResult trigger(Trigger trigger, BpmnElement bpmnElement) {
    StateEnum newState = state;
    Set<String> newActiveFlows = new HashSet<>();
    Set<String> externalTasks = new HashSet<>();

    if (state == StateEnum.INIT) {
      newState = StateEnum.WAITING;
      externalTasks.add(bpmnElement.getId());
    } else if (state == StateEnum.WAITING) {
      newActiveFlows.addAll(bpmnElement.getOutputFlows());
      newState = StateEnum.FINISHED;
    }

    return new TriggerResult(
        new ServiceTaskState(newState, cnt + 1), newActiveFlows, externalTasks);
  }
}
