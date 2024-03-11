package nl.qunit.bpmnmeister.pi.state;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.UUID;
import lombok.Getter;

@Getter
public class EndEventState extends EventState {
  @JsonCreator
  public EndEventState(
      @JsonProperty("state") StateEnum state,
      @JsonProperty("elementInstanceId") UUID elementInstanceId) {
    super(state, elementInstanceId);
  }
}
