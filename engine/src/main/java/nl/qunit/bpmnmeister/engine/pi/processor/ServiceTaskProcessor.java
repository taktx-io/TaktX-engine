package nl.qunit.bpmnmeister.engine.pi.processor;

import jakarta.enterprise.context.ApplicationScoped;
import nl.qunit.bpmnmeister.pd.model.ServiceTask;
import nl.qunit.bpmnmeister.pi.state.FlowNodeStateEnum;
import nl.qunit.bpmnmeister.pi.state.ServiceTaskState;

@ApplicationScoped
public class ServiceTaskProcessor extends ExternalTaskProcessor<ServiceTask, ServiceTaskState> {

  @Override
  protected ServiceTaskState getNewAttempExternalTaskState(ServiceTaskState oldState) {
    return new ServiceTaskState(
        FlowNodeStateEnum.ACTIVE,
        oldState.getElementInstanceId(),
        oldState.getPassedCnt(),
        oldState.getLoopCnt(),
        oldState.getAttempt() + 1,
        oldState.getInputFlowId());
  }

  @Override
  protected ServiceTaskState getTerminateElementState(ServiceTaskState elementState) {
    return new ServiceTaskState(
        FlowNodeStateEnum.TERMINATED,
        elementState.getElementInstanceId(),
        elementState.getPassedCnt(),
        elementState.getLoopCnt(),
        elementState.getAttempt(),
        elementState.getInputFlowId());
  }
}
