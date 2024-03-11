package nl.qunit.bpmnmeister.pi.state;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.annotation.Nonnull;
import java.util.UUID;
import lombok.Getter;

@Getter
public class ServiceTaskState extends BpmnElementState {

  @JsonCreator
  public ServiceTaskState(
      @Nonnull @JsonProperty("state") StateEnum state,
      @Nonnull @JsonProperty("elementInstanceId") UUID elementInstanceId) {
    super(state, elementInstanceId);
  }
}
