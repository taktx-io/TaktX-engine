package nl.qunit.bpmnmeister.pi;

import lombok.Getter;

@Getter
public class EndThrowingEvent extends ThrowingEvent {

  @Override
  public ProcessInstanceState process(
      ProcessInstance processInstance, FlowNodeStates newFlowNodeStates) {
    return ProcessInstanceState.COMPLETED;
  }
}
