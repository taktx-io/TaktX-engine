package nl.qunit.bpmnmeister.pi.state;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

@Getter
@SuperBuilder
public class ServiceTaskState extends TaskState {

  @JsonCreator
  public ServiceTaskState(@JsonProperty("state") StateEnum state, @JsonProperty("cnt") int cnt) {
    super(state, cnt);
  }
}
