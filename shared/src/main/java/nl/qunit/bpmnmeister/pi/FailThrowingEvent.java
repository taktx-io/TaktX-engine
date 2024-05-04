package nl.qunit.bpmnmeister.pi;

import com.fasterxml.jackson.annotation.JsonCreator;
import lombok.Getter;

@Getter
public class FailThrowingEvent extends ThrowingEvent {

  @JsonCreator
  public FailThrowingEvent() {}

  @Override
  public ProcessInstanceState process(ProcessInstance processInstance) {
    return ProcessInstanceState.FAILED;
  }
}
