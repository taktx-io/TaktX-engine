package nl.qunit.bpmnmeister.engine.pi.processor;

import jakarta.enterprise.context.ApplicationScoped;
import nl.qunit.bpmnmeister.pd.model.ServiceTaskDTO;
import nl.qunit.bpmnmeister.pi.state.FlowNodeStateEnum;
import nl.qunit.bpmnmeister.pi.state.SendTaskState;
import nl.qunit.bpmnmeister.pi.state.ServiceTaskState;

@ApplicationScoped
public class ServiceTaskProcessor extends ExternalTaskProcessor<ServiceTaskDTO, ServiceTaskState> {

  @Override
  protected ServiceTaskState getNewAttempExternalTaskState(ServiceTaskState oldState) {
    return new ServiceTaskState(
        FlowNodeStateEnum.WAITING,
        oldState.getParentElementInstanceId(),
        oldState.getElementInstanceId(),
        oldState.getElementId(),
        oldState.getPassedCnt(),
        oldState.getLoopCnt(),
        oldState.getAttempt() + 1,
        oldState.getInputFlowId());
  }

  @Override
  protected SendTaskState getFinishedState(ServiceTaskState oldState) {
    return new SendTaskState(
        FlowNodeStateEnum.FINISHED,
        oldState.getParentElementInstanceId(),
        oldState.getElementInstanceId(),
        oldState.getElementId(),
        oldState.getPassedCnt() + 1,
        oldState.getLoopCnt(),
        oldState.getAttempt(),
        oldState.getInputFlowId());
  }

  @Override
  protected ServiceTaskState getTerminateElementState(ServiceTaskState elementState) {
    return new ServiceTaskState(
        FlowNodeStateEnum.TERMINATED,
        elementState.getParentElementInstanceId(),
        elementState.getElementInstanceId(),
        elementState.getElementId(),
        elementState.getPassedCnt(),
        elementState.getLoopCnt(),
        elementState.getAttempt(),
        elementState.getInputFlowId());
  }
}
