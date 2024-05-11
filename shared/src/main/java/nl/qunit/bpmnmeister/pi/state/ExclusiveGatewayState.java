package nl.qunit.bpmnmeister.pi.state;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.annotation.Nonnull;
import lombok.Getter;

@Getter
public class ExclusiveGatewayState extends GatewayState {
  @JsonCreator
  public ExclusiveGatewayState(
      @Nonnull @JsonProperty("elementInstanceId") java.util.UUID elementInstanceId,
      @JsonProperty("passedCnt") int passedCnt,
      @JsonProperty("state") ActivityStateEnum state) {
    super(elementInstanceId, passedCnt, state);
  }

  @Override
  public BpmnElementState terminate() {
    if (this.getState() == ActivityStateEnum.ACTIVE) {
      return new ExclusiveGatewayState(this.getElementInstanceId(), this.getPassedCnt(), ActivityStateEnum.TERMINATED);
    } else {
      return this;
    }
  }
}
