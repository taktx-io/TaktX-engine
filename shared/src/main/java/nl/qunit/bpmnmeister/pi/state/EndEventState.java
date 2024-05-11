package nl.qunit.bpmnmeister.pi.state;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.annotation.Nonnull;
import java.util.UUID;
import lombok.Getter;
import lombok.ToString;

@Getter
@ToString(callSuper = true)
public class EndEventState extends EventState {
  @JsonCreator
  public EndEventState(
      @Nonnull @JsonProperty("elementInstanceId") UUID elementInstanceId,
      @JsonProperty("passedCnt") int passedCnt) {
    super(elementInstanceId, passedCnt);
  }

  @Override
  public BpmnElementState terminate() {
    return new EndEventState(getElementInstanceId(), getPassedCnt());
  }
}
