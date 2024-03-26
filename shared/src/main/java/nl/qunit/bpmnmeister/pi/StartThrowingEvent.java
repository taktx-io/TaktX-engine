package nl.qunit.bpmnmeister.pi;

import com.fasterxml.jackson.annotation.JsonCreator;
import lombok.Getter;

@Getter
public class StartThrowingEvent extends ThrowingEvent {

  @JsonCreator
  public StartThrowingEvent() {}

  @Override
  public ProcessInstanceState process(ProcessInstance processInstance) {
    return ProcessInstanceState.ACTIVE;
  }
}
