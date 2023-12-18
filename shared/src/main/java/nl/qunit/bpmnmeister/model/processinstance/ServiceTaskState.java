package nl.qunit.bpmnmeister.model.processinstance;

import java.util.HashSet;
import java.util.Set;
import nl.qunit.bpmnmeister.model.processdefinition.BpmnElement;

public record ServiceTaskState(StateEnum state) implements BpmnElementState {

  @Override
  public TriggerResult trigger(Trigger trigger, BpmnElement bpmnElement) {
    StateEnum newState = state;
    Set<String> newActiveFlows = new HashSet<>();
    Set<String> externalTasks = new HashSet<>();

    if (state == StateEnum.INIT) {
      newState = StateEnum.WAITING;
      externalTasks.add(bpmnElement.id());
    } else if (state == StateEnum.WAITING) {
      newActiveFlows.addAll(bpmnElement.outputFlows());
      newState = StateEnum.FINISHED;
    }

    return new TriggerResult(new ServiceTaskState(newState), newActiveFlows, externalTasks);
  }
}
