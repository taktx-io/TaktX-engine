package nl.qunit.bpmnmeister.pi.state;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.annotation.Nonnull;
import lombok.Getter;

@Getter
public class StartEventState extends EventState {
  @JsonCreator
  public StartEventState(
      @Nonnull @JsonProperty("state") StateEnum state,
      @Nonnull @JsonProperty("elementInstanceId") java.util.UUID elementInstanceId) {
    super(state, elementInstanceId);
  }
}
