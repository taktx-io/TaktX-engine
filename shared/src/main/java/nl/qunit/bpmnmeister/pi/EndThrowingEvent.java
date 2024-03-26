package nl.qunit.bpmnmeister.pi;

import com.fasterxml.jackson.annotation.JsonCreator;
import lombok.Getter;

@Getter
public class EndThrowingEvent extends ThrowingEvent {

  @JsonCreator
  public EndThrowingEvent() {}

  @Override
  public ProcessInstanceState process(ProcessInstance processInstance) {
    return ProcessInstanceState.COMPLETED;
  }
}
