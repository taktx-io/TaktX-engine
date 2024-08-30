package nl.qunit.bpmnmeister.pi;

import lombok.Getter;
import nl.qunit.bpmnmeister.pi.state.FlowNodeStateEnum;

@Getter
public class EndThrowingEvent extends ThrowingEvent {

  @Override
  public ProcessInstanceState process(
      ProcessInstance processInstance, FlowNodeStates newFlowNodeStates) {
    if (newFlowNodeStates.getWithState(FlowNodeStateEnum.WAITING).isEmpty()) {
      return ProcessInstanceState.COMPLETED;
    } else {
      return processInstance.getProcessInstanceState();
    }
  }
}
