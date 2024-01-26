package nl.qunit.bpmnmeister.pi.state;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

@Getter
@SuperBuilder
public class StartEventState extends BpmnElementState {
  @JsonCreator
  public StartEventState(@JsonProperty("state") StateEnum state) {
    super(state);
  }
}
