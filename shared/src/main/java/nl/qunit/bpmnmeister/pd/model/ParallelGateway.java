package nl.qunit.bpmnmeister.pd.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.annotation.Nonnull;
import java.util.Set;
import java.util.UUID;
import lombok.Getter;
import nl.qunit.bpmnmeister.pi.state.ActivityStateEnum;
import nl.qunit.bpmnmeister.pi.state.ParallelGatewayState;

@Getter
public class ParallelGateway extends Gateway<ParallelGatewayState> {
  @JsonCreator
  public ParallelGateway(
      @Nonnull @JsonProperty("id") String id,
      @Nonnull @JsonProperty("parentId") String parentId,
      @Nonnull @JsonProperty("incoming") Set<String> incoming,
      @Nonnull @JsonProperty("outgoing") Set<String> outgoing) {
    super(id, parentId, incoming, outgoing);
  }

  @Override
  public ParallelGatewayState getInitialState() {
    return new ParallelGatewayState(UUID.randomUUID(), Set.of(), 0, ActivityStateEnum.READY);
  }
}
