package nl.qunit.bpmnmeister.engine.pi.processor;

import jakarta.enterprise.context.ApplicationScoped;
import java.util.Set;
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
    return TriggerResult.builder()
        .newElementState(
            ServiceTaskState.builder().cnt(oldState.getCnt() + 1).state(StateEnum.WAITING).build())
        .externalTasks(Set.of(element.getId()))
        .build();
  }

  @Override
  protected TriggerResult triggerWhenFinished(
      ProcessInstanceTrigger trigger,
      ProcessDefinition processDefinition,
      ServiceTask element,
      ServiceTaskState oldState) {
    throw new IllegalStateException("ServiceTask cannot be in finished state");
  }

  @Override
  protected TriggerResult triggerWhenWaiting(
      ProcessInstanceTrigger trigger,
      ProcessDefinition processDefinition,
      ServiceTask element,
      ServiceTaskState oldState) {
    return TriggerResult.builder()
        .newElementState(
            ServiceTaskState.builder().cnt(oldState.getCnt() + 1).state(StateEnum.INIT).build())
        .newActiveFlows(element.getOutgoing())
        .build();
  }

  @Override
  protected TriggerResult triggerWhenActive(
      ProcessInstanceTrigger trigger,
      ProcessDefinition processDefinition,
      ServiceTask element,
      ServiceTaskState oldState) {
    throw new IllegalStateException("ServiceTask cannot be in active state");
  }

  @Override
  public ServiceTaskState initialState() {
    return ServiceTaskState.builder().state(StateEnum.INIT).build();
  }
}
