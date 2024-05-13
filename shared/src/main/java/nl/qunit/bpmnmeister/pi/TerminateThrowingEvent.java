package nl.qunit.bpmnmeister.pi;

import com.fasterxml.jackson.annotation.JsonCreator;
import lombok.Getter;

@Getter
public class TerminateThrowingEvent extends ThrowingEvent {

  @JsonCreator
  public TerminateThrowingEvent() {}

  @Override
  public ProcessInstanceState process(ProcessInstance processInstance) {
    return ProcessInstanceState.TERMINATED;
  }
}
