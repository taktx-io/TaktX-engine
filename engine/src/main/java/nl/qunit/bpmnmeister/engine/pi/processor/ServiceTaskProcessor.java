package nl.qunit.bpmnmeister.engine.pi.processor;

import jakarta.enterprise.context.ApplicationScoped;
import java.util.Set;
import java.util.UUID;
import nl.qunit.bpmnmeister.engine.pi.TriggerResult;
import nl.qunit.bpmnmeister.pd.model.ServiceTask;
import nl.qunit.bpmnmeister.pi.ExternalTaskResponseTrigger;
import nl.qunit.bpmnmeister.pi.FlowElementTrigger;
import nl.qunit.bpmnmeister.pi.ProcessInstance;
import nl.qunit.bpmnmeister.pi.ThrowingEvent;
import nl.qunit.bpmnmeister.pi.Variables;
import nl.qunit.bpmnmeister.pi.state.ActivityStateEnum;
import nl.qunit.bpmnmeister.pi.state.ServiceTaskState;

@ApplicationScoped
public class ServiceTaskProcessor extends ActivityProcessor<ServiceTask, ServiceTaskState> {

  @Override
  protected TriggerResult triggerFlowElement(
      FlowElementTrigger trigger,
      ProcessInstance processInstance,
      ServiceTask element,
      ServiceTaskState oldState,
      Variables variables) {
    if (oldState.getState() != ActivityStateEnum.READY) {
      return new TriggerResult(
          oldState, Set.of(), Set.of(), Set.of(), Set.of(), ThrowingEvent.NOOP, Variables.EMPTY);
    }
    return new TriggerResult(
        new ServiceTaskState(
            ActivityStateEnum.ACTIVE, oldState.getElementInstanceId(), oldState.getPassedCnt()),
        Set.of(),
        Set.of(element.getId()),
        Set.of(),
        Set.of(),
        ThrowingEvent.NOOP,
        Variables.EMPTY);
  }

  @Override
  protected TriggerResult triggerExternalTaskResponse(
      ExternalTaskResponseTrigger trigger,
      ProcessInstance processInstance,
      ServiceTask element,
      ServiceTaskState oldState,
      Variables variables) {
    if (oldState.getState() != ActivityStateEnum.ACTIVE) {
      return new TriggerResult(
          oldState, Set.of(), Set.of(), Set.of(), Set.of(), ThrowingEvent.NOOP, Variables.EMPTY);
    }

    return new TriggerResult(
        new ServiceTaskState(
            ActivityStateEnum.FINISHED,
            oldState.getElementInstanceId(),
            oldState.getPassedCnt() + 1),
        element.getOutgoing(),
        Set.of(),
        Set.of(),
        Set.of(),
        ThrowingEvent.NOOP,
        trigger.getVariables());
  }

  @Override
  public ServiceTaskState initialState() {
    return new ServiceTaskState(ActivityStateEnum.READY, UUID.randomUUID(), 0);
  }
}
