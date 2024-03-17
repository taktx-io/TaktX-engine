package nl.qunit.bpmnmeister.pi.state;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.UUID;
import lombok.Getter;

@Getter
public class MultiInstanceState extends ActivityState {

  private final int loopCnt;

  @JsonCreator
  public MultiInstanceState(
      @JsonProperty("state") ActivityStateEnum state,
      @JsonProperty("elementInstanceId") UUID elementInstanceId,
      @JsonProperty("loopCnt") int loopCnt) {
    super(state, elementInstanceId);
    this.loopCnt = loopCnt;
  }
}
