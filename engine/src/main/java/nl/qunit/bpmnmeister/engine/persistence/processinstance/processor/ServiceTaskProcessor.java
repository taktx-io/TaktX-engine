package nl.qunit.bpmnmeister.engine.persistence.processinstance.processor;

import jakarta.enterprise.context.ApplicationScoped;
import nl.qunit.bpmnmeister.engine.persistence.processdefinition.Definitions;
import nl.qunit.bpmnmeister.engine.persistence.processdefinition.ServiceTask;
import nl.qunit.bpmnmeister.engine.persistence.processinstance.TriggerResult;
import nl.qunit.bpmnmeister.engine.persistence.processinstance.state.ServiceTaskState;
import nl.qunit.bpmnmeister.engine.persistence.processinstance.state.StateEnum;
import nl.qunit.bpmnmeister.model.processinstance.Trigger;

@ApplicationScoped
public class ServiceTaskProcessor extends StateProcessor<ServiceTask, ServiceTaskState> {
  @Override
  protected TriggerResult triggerWhenInit(Trigger trigger, Definitions processDefinition, ServiceTask element, ServiceTaskState oldState) {
    return new TriggerResult(
            ServiceTaskState.builder().cnt(oldState.getCnt() + 1).build(), element.getOutgoing());
  }
  @Override
  protected TriggerResult triggerWhenActive(Trigger trigger, Definitions processDefinition, ServiceTask element, ServiceTaskState oldState) {
    return new TriggerResult(
            ServiceTaskState.builder().cnt(oldState.getCnt() + 1).build(), element.getOutgoing());
  }


  @Override
  public ServiceTaskState initialState() {
    return ServiceTaskState.builder().state(StateEnum.INIT).build();
  }
}
