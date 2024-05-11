package nl.qunit.bpmnmeister.pi.state;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.annotation.Nonnull;
import lombok.Getter;
import lombok.ToString;

@Getter
@ToString(callSuper = true)
public class StartEventState extends EventState {
  @JsonCreator
  public StartEventState(
      @Nonnull @JsonProperty("elementInstanceId") java.util.UUID elementInstanceId,
      @JsonProperty("passedCnt") int passedCnt) {
    super(elementInstanceId, passedCnt);
  }

  @Override
  public BpmnElementState terminate() {
    return new StartEventState(getElementInstanceId(), getPassedCnt());
  }
}
