package nl.qunit.bpmnmeister.pi.state;

import com.fasterxml.jackson.annotation.JsonCreator;
import java.util.UUID;
import lombok.Getter;
import lombok.ToString;

@Getter
@ToString(callSuper = true)
public abstract class EventState extends BpmnElementState {
  @JsonCreator
  protected EventState(UUID elementInstanceId, int passedCnt) {
    super(elementInstanceId, passedCnt);
  }
}
