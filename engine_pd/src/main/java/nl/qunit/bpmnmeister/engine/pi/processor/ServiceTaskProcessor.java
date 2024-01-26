package nl.qunit.bpmnmeister.engine.pi.processor;

import jakarta.enterprise.context.ApplicationScoped;
import nl.qunit.bpmnmeister.engine.pi.TriggerResult;
import nl.qunit.bpmnmeister.pd.model.ProcessDefinition;
import nl.qunit.bpmnmeister.pd.model.ServiceTask;
import nl.qunit.bpmnmeister.pi.ProcessInstanceTrigger;
import nl.qunit.bpmnmeister.pi.state.ServiceTaskState;
import nl.qunit.bpmnmeister.pi.state.StateEnum;

@ApplicationScoped
public class ServiceTaskProcessor extends StateProcessor<ServiceTask, ServiceTaskState> {
  @Override
  protected TriggerResult triggerWhenInit(
      ProcessInstanceTrigger trigger,
      ProcessDefinition processDefinition,
      ServiceTask element,
      ServiceTaskState oldState) {
    return new TriggerResult(
        ServiceTaskState.builder().cnt(oldState.getCnt() + 1).state(StateEnum.ACTIVE).build(),
        element.getOutgoing());
  }

  @Override
  protected TriggerResult triggerWhenActive(
      ProcessInstanceTrigger trigger,
      ProcessDefinition processDefinition,
      ServiceTask element,
      ServiceTaskState oldState) {
    return new TriggerResult(
        ServiceTaskState.builder().cnt(oldState.getCnt() + 1).state(StateEnum.FINISHED).build(),
        element.getOutgoing());
  }

  @Override
  public ServiceTaskState initialState() {
    return ServiceTaskState.builder().state(StateEnum.INIT).build();
  }
}
