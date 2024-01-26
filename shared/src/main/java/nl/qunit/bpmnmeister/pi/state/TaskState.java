package nl.qunit.bpmnmeister.pi.state;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

@Getter
@SuperBuilder
public class TaskState extends BpmnElementState {
  int cnt;

  @JsonCreator
  public TaskState(@JsonProperty("state") StateEnum state, @JsonProperty("cnt") int cnt) {
    super(state);
    this.cnt = cnt;
  }
}
