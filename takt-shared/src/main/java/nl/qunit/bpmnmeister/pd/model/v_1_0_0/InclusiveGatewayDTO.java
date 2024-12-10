package nl.qunit.bpmnmeister.pd.model.v_1_0_0;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.annotation.Nonnull;
import java.util.Set;
import lombok.EqualsAndHashCode;
import lombok.Getter;

@Getter
@EqualsAndHashCode(callSuper = true)
public class InclusiveGatewayDTO extends GatewayDTO {

  @Nonnull private final String defaultFlow;

  @JsonCreator
  public InclusiveGatewayDTO(
      @Nonnull @JsonProperty("id") String id,
      @Nonnull @JsonProperty("parentId") String parentId,
      @Nonnull @JsonProperty("incoming") Set<String> incoming,
      @Nonnull @JsonProperty("outgoing") Set<String> outgoing,
      @Nonnull @JsonProperty("default") String defaultFlow) {
    super(id, parentId, incoming, outgoing, defaultFlow);
    this.defaultFlow = defaultFlow;
  }
}
