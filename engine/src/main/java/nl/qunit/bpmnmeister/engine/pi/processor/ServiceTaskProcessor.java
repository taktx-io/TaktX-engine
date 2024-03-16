package nl.qunit.bpmnmeister.engine.pi.processor;

import jakarta.enterprise.context.ApplicationScoped;
import java.util.Set;
import java.util.UUID;
import nl.qunit.bpmnmeister.engine.pi.TriggerResult;
import nl.qunit.bpmnmeister.pd.model.ServiceTask;
import nl.qunit.bpmnmeister.pi.ProcessInstance;
import nl.qunit.bpmnmeister.pi.ProcessInstanceTrigger;
import nl.qunit.bpmnmeister.pi.Variables;
import nl.qunit.bpmnmeister.pi.state.ActivityStateEnum;
import nl.qunit.bpmnmeister.pi.state.ServiceTaskState;

@ApplicationScoped
public class ServiceTaskProcessor extends ActivityProcessor<ServiceTask, ServiceTaskState> {
  @Override
  protected TriggerResult triggerWhenInit(
      ProcessInstanceTrigger trigger,
      ProcessInstance processInstance,
      ServiceTask element,
      ServiceTaskState oldState,
      Variables variables) {
    return new TriggerResult(
        new ServiceTaskState(ActivityStateEnum.ACTIVE, oldState.getElementInstanceId()),
        Set.of(),
        Set.of(element.getId()),
        Set.of(),
        Variables.EMPTY);
  }

  @Override
  protected TriggerResult triggerWhenWaiting(
      ProcessInstanceTrigger trigger,
      ProcessInstance processInstance,
      ServiceTask element,
      ServiceTaskState oldState,
      Variables variables) {
    return new TriggerResult(
        new ServiceTaskState(ActivityStateEnum.FINISHED, oldState.getElementInstanceId()),
        element.getOutgoing(),
        Set.of(),
        Set.of(),
        trigger.getVariables());
  }

  @Override
  public ServiceTaskState initialState() {
    return new ServiceTaskState(ActivityStateEnum.READY, UUID.randomUUID());
  }

  @Override
  public ServiceTaskState terminate(ServiceTaskState oldState) {
    return new ServiceTaskState(ActivityStateEnum.TERMINATED, oldState.getElementInstanceId());
  }
}
