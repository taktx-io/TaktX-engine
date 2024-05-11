package nl.qunit.bpmnmeister.pi.state;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.annotation.Nonnull;
import java.util.Set;
import java.util.UUID;
import lombok.Getter;

@Getter
public class ParallelGatewayState extends GatewayState {
  Set<String> triggeredFlows;

  @JsonCreator
  public ParallelGatewayState(
      @Nonnull @JsonProperty("elementInstanceId") UUID elementInstanceId,
      @Nonnull @JsonProperty("triggeredFlows") Set<String> triggeredFlows,
      @JsonProperty("passedCnt") int passedCnt,
      @JsonProperty("state") ActivityStateEnum state) {
    super(elementInstanceId, passedCnt, state);
    this.triggeredFlows = triggeredFlows;
  }

  @Override
  public BpmnElementState terminate() {
    if (this.getState() == ActivityStateEnum.ACTIVE) {
      return new ParallelGatewayState(this.getElementInstanceId(), this.getTriggeredFlows(), this.getPassedCnt(), ActivityStateEnum.TERMINATED);
    } else {
      return this;
    }
  }}
