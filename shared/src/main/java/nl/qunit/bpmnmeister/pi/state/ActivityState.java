package nl.qunit.bpmnmeister.pi.state;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.UUID;
import lombok.Getter;

@Getter
public abstract class ActivityState extends BpmnElementState {
  int loopCnt;

  @JsonCreator
  protected ActivityState(
      @JsonProperty("state") StateEnum state,
      @JsonProperty("elementInstanceId") UUID elementInstanceId,
      @JsonProperty("loopCnt") int loopCnt) {
    super(state, elementInstanceId);
    this.loopCnt = loopCnt;
  }
}
