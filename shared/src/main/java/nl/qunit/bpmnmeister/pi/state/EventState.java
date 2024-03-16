package nl.qunit.bpmnmeister.pi.state;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.UUID;
import lombok.Getter;

@Getter
public abstract class EventState extends BpmnElementState {
  @JsonCreator
  protected EventState(@JsonProperty("elementInstanceId") UUID elementInstanceId) {
    super(elementInstanceId);
  }
}
