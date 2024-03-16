package nl.qunit.bpmnmeister.pi.state;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.annotation.Nonnull;
import lombok.Getter;

@Getter
public class ExclusiveGatewayState extends GatewayState {
  @JsonCreator
  public ExclusiveGatewayState(
      @Nonnull @JsonProperty("elementInstanceId") java.util.UUID elementInstanceId) {
    super(elementInstanceId);
  }
}
