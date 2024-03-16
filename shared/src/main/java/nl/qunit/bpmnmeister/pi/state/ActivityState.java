package nl.qunit.bpmnmeister.pi.state;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.UUID;
import lombok.Getter;

@Getter
public abstract class ActivityState extends BpmnElementState {

  private final ActivityStateEnum state;

  @JsonCreator
  protected ActivityState(
      @JsonProperty("state") ActivityStateEnum state,
      @JsonProperty("elementInstanceId") UUID elementInstanceId) {
    super(elementInstanceId);
    this.state = state;
  }
}
