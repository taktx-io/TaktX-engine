package nl.qunit.bpmnmeister.engine.persistence.processinstance.processor;

import jakarta.enterprise.context.ApplicationScoped;
import java.util.HashSet;
import java.util.Set;
import nl.qunit.bpmnmeister.engine.persistence.processdefinition.Definitions;
import nl.qunit.bpmnmeister.engine.persistence.processdefinition.ServiceTask;
import nl.qunit.bpmnmeister.engine.persistence.processinstance.TriggerResult;
import nl.qunit.bpmnmeister.engine.persistence.processinstance.state.ServiceTaskState;
import nl.qunit.bpmnmeister.engine.persistence.processinstance.state.StateEnum;
import nl.qunit.bpmnmeister.model.processinstance.Trigger;

@ApplicationScoped
public class ServiceTaskProcessor extends StateProcessor<ServiceTask, ServiceTaskState> {
  @Override
  public TriggerResult doTrigger(
      Trigger trigger,
      Definitions processDefinition,
      ServiceTask element,
      ServiceTaskState oldState) {
    Set<String> newActiveFlows = new HashSet<>();
    if (oldState.getState() == StateEnum.INIT) {
    } else if (oldState.getState() == StateEnum.WAITING) {
      newActiveFlows.addAll(element.getOutgoing());
    }

    return new TriggerResult(
        ServiceTaskState.builder().cnt(oldState.getCnt() + 1).build(), newActiveFlows);
  }

  @Override
  public ServiceTaskState initialState() {
    return ServiceTaskState.builder().state(StateEnum.INIT).build();
  }
}
