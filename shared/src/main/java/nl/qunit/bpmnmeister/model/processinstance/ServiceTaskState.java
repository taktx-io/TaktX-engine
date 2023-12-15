package nl.qunit.bpmnmeister.model.processinstance;

import java.util.HashSet;
import java.util.Set;
import nl.qunit.bpmnmeister.model.processdefinition.BpmnElement;

public record ServiceTaskState(ServiceTaskStateEnum state) implements BpmnElementState {

  @Override
  public TriggerResult trigger(Trigger trigger, BpmnElement bpmnElement) {
    ServiceTaskStateEnum newState = state;
    Set<String> newActiveFlows = new HashSet<>();
    Set<String> externalTasks = new HashSet<>();

    if (state == ServiceTaskStateEnum.INIT) {
      newState = ServiceTaskStateEnum.WAITING;
      externalTasks.add(bpmnElement.id());
    } else if (state == ServiceTaskStateEnum.WAITING) {
      newActiveFlows.addAll(bpmnElement.outputFlows());
      newState = ServiceTaskStateEnum.FINISHED;
    }

    return new TriggerResult(new ServiceTaskState(newState), newActiveFlows, externalTasks);
  }
}
