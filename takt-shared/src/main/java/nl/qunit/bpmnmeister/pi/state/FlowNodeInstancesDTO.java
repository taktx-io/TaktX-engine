package nl.qunit.bpmnmeister.pi.state;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.annotation.Nonnull;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import nl.qunit.bpmnmeister.pi.ProcessInstanceState;

@Getter
@Setter
@ToString
public class FlowNodeInstancesDTO {

  private final ProcessInstanceState state;
  private final UUID flowNodeInstancesId;

  @JsonCreator
  public FlowNodeInstancesDTO(
      @Nonnull @JsonProperty("state") ProcessInstanceState state,
      @Nonnull @JsonProperty("flowNodeInstancesId") UUID flowNodeInstancesId) {
    this.state = state;
    this.flowNodeInstancesId = flowNodeInstancesId;
  }
}
