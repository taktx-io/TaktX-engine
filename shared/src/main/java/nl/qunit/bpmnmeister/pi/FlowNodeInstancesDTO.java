package nl.qunit.bpmnmeister.pi;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.annotation.Nonnull;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import nl.qunit.bpmnmeister.pi.state.FlowNodeInstanceDTO;

@Getter
@Setter
@ToString
public class FlowNodeInstancesDTO {

  private final ProcessInstanceState state;
  private final Map<UUID, FlowNodeInstanceDTO> instances;

  @JsonCreator
  public FlowNodeInstancesDTO(
      @Nonnull @JsonProperty("state") ProcessInstanceState state,
      @Nonnull @JsonProperty("instances") Map<UUID, FlowNodeInstanceDTO> instances) {
    this.state = state;
    this.instances = instances;
  }

  @JsonIgnore
  public List<FlowNodeInstanceDTO> get(String elementId) {
    return instances.values().stream()
        .filter(state -> state.getElementId().equals(elementId))
        .toList();
  }
}
