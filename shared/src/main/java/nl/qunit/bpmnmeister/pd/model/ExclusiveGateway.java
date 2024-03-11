package nl.qunit.bpmnmeister.pd.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.annotation.Nonnull;
import java.util.Set;
import lombok.Getter;

@Getter
public class ExclusiveGateway extends Gateway {
  @JsonCreator
  public ExclusiveGateway(
      @Nonnull @JsonProperty("id") BaseElementId id,
      @Nonnull @JsonProperty("parentId") BaseElementId parentId,
      @Nonnull @JsonProperty("incoming") Set<BaseElementId> incoming,
      @Nonnull @JsonProperty("outgoing") Set<BaseElementId> outgoing) {
    super(id, parentId, incoming, outgoing);
  }
}
