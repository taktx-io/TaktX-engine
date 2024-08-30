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
import nl.qunit.bpmnmeister.pi.state.FlowNodeStateDTO;

@Getter
@Setter
@ToString
public class FlowNodeStatesDTO {

  public static final FlowNodeStatesDTO EMPTY =
      new FlowNodeStatesDTO(ProcessInstanceState.START, Map.of());

  private final ProcessInstanceState state;
  private final Map<UUID, FlowNodeStateDTO> flowNodeInstances;

  @JsonCreator
  public FlowNodeStatesDTO(
      @Nonnull @JsonProperty("state") ProcessInstanceState state,
      @Nonnull @JsonProperty("flowNodeInstances") Map<UUID, FlowNodeStateDTO> flowNodeInstances) {
    this.state = state;
    this.flowNodeInstances = flowNodeInstances;
  }

  @JsonIgnore
  public List<FlowNodeStateDTO> get(String elementId) {
    return flowNodeInstances.values().stream().filter(state -> state.getElementId().equals(elementId)).toList();
  }

}
