package nl.qunit.bpmnmeister.engine.pi.processor;

import jakarta.enterprise.context.ApplicationScoped;
import nl.qunit.bpmnmeister.pd.model.SendTask;
import nl.qunit.bpmnmeister.pi.state.FlowNodeStateEnum;
import nl.qunit.bpmnmeister.pi.state.SendTaskState;

@ApplicationScoped
public class SendTaskProcessor extends ExternalTaskProcessor<SendTask, SendTaskState> {

  @Override
  protected SendTaskState getNewAttempExternalTaskState(SendTaskState oldState) {
    return new SendTaskState(
        FlowNodeStateEnum.ACTIVE,
        oldState.getParentElementInstanceId(),
        oldState.getElementInstanceId(),
        oldState.getElementId(),
        oldState.getPassedCnt(),
        oldState.getLoopCnt(),
        oldState.getAttempt() + 1,
        oldState.getInputFlowId());
  }

  @Override
  protected SendTaskState getFinishedState(SendTaskState oldState) {
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
  protected SendTaskState getTerminateElementState(SendTaskState elementState) {
    return new SendTaskState(
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
