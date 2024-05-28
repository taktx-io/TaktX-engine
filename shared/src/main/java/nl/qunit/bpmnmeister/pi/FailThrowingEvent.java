package nl.qunit.bpmnmeister.pi;

import lombok.Getter;

@Getter
public class FailThrowingEvent extends ThrowingEvent {

  @Override
  public ProcessInstanceState process(ProcessInstance processInstance,
      FlowNodeStates newFlowNodeStates) {
    return ProcessInstanceState.FAILED;
  }
}
