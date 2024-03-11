package nl.qunit.bpmnmeister.engine.pi.processor;

import jakarta.enterprise.context.ApplicationScoped;
import java.util.Set;
import java.util.UUID;
import nl.qunit.bpmnmeister.engine.pi.TriggerResult;
import nl.qunit.bpmnmeister.pd.model.ServiceTask;
import nl.qunit.bpmnmeister.pi.ProcessInstance;
import nl.qunit.bpmnmeister.pi.ProcessInstanceTrigger;
import nl.qunit.bpmnmeister.pi.Variables;
import nl.qunit.bpmnmeister.pi.state.ServiceTaskState;
import nl.qunit.bpmnmeister.pi.state.StateEnum;

@ApplicationScoped
public class ServiceTaskProcessor extends ActivityProcessor<ServiceTask, ServiceTaskState> {
  @Override
  protected TriggerResult triggerWhenInit(
      ProcessInstanceTrigger trigger,
      ProcessInstance processInstance,
      ServiceTask element,
      ServiceTaskState oldState,
      Variables variables) {
    return TriggerResult.builder()
        .newElementState(new ServiceTaskState(StateEnum.WAITING, oldState.getElementInstanceId()))
        .externalTasks(Set.of(element.getId()))
        .build();
  }

  @Override
  protected TriggerResult triggerWhenWaiting(
      ProcessInstanceTrigger trigger,
      ProcessInstance processInstance,
      ServiceTask element,
      ServiceTaskState oldState,
      Variables variables) {

    return TriggerResult.builder()
        .newElementState(new ServiceTaskState(StateEnum.FINISHED, oldState.getElementInstanceId()))
        .newActiveFlows(element.getOutgoing())
        .variables(trigger.getVariables())
        .build();
  }

  @Override
  public ServiceTaskState initialState() {
    return new ServiceTaskState(StateEnum.INIT, UUID.randomUUID());
  }

  @Override
  public ServiceTaskState terminate(ServiceTaskState oldState) {
    return new ServiceTaskState(StateEnum.TERMINATED, oldState.getElementInstanceId());
  }
}
