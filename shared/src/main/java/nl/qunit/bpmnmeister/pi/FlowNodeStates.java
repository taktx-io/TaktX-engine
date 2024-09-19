package nl.qunit.bpmnmeister.pi;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;
import java.util.UUID;
import lombok.Getter;
import lombok.ToString;
import nl.qunit.bpmnmeister.pi.state.FlowNodeStateDTO;

@Getter
@ToString
public class FlowNodeStates {

  public static final FlowNodeStates EMPTY = new FlowNodeStates(Map.of());
  private final Map<UUID, FlowNodeStateDTO> elementStateMap;

  @JsonCreator
  public FlowNodeStates(
      @JsonProperty("flowNodeStates") Map<UUID, FlowNodeStateDTO> elementStateMap) {
    this.elementStateMap = elementStateMap;
  }
}
