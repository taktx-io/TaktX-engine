package nl.qunit.bpmnmeister.pi.state;

import com.fasterxml.jackson.annotation.JsonCreator;
import java.util.UUID;
import lombok.Getter;

@Getter
public abstract class EventState extends BpmnElementState {
  @JsonCreator
  protected EventState(UUID elementInstanceId, int passedCnt) {
    super(elementInstanceId, passedCnt);
  }
}
