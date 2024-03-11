package nl.qunit.bpmnmeister.pi.state;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

@Getter
public class StartEventState extends EventState {
  @JsonCreator
  public StartEventState(
      @JsonProperty("state") StateEnum state,
      @JsonProperty("elementInstanceId") java.util.UUID elementInstanceId) {
    super(state, elementInstanceId);
  }
}
