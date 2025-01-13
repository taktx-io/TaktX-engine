package com.flomaestro.takt.dto.v_1_0_0;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Data
@EqualsAndHashCode
@ToString
@NoArgsConstructor
public class FlowElementsDTO {
  public static final FlowElementsDTO EMPTY = new FlowElementsDTO(Map.of());

  @JsonProperty("e")
  private Map<String, FlowElementDTO> elements;

  public FlowElementsDTO(Map<String, FlowElementDTO> elements) {
    this.elements = elements;
  }

  @JsonIgnore
  public List<BaseElementDTO> values() {
    return List.copyOf(elements.values());
  }

  @JsonIgnore
  public Set<String> keySet() {
    return Set.copyOf(elements.keySet());
  }

  @JsonIgnore
  public FlowElementDTO get(String id) {
    return elements.get(id);
  }

  @JsonIgnore
  public List<StartEventDTO> getStartEvents() {
    return elements.values().stream()
        .filter(StartEventDTO.class::isInstance)
        .map(StartEventDTO.class::cast)
        .toList();
  }

  @JsonIgnore
  public FlowElementDTO getStartNode(String elementId) {
    FlowElementDTO flowNodeDTO;
    if (!elementId.equals(Constants.NONE)) {
      flowNodeDTO = elements.get(elementId);
    } else {
      List<StartEventDTO> startEvents = getStartEvents();
      if (startEvents.isEmpty()) {
        Optional<FlowNodeDTO> withoutInputFlow =
            elements.values().stream()
                .filter(FlowNodeDTO.class::isInstance)
                .map(e -> (FlowNodeDTO) e)
                .filter(flowNode -> !flowNode.getIncoming().isEmpty())
                .findFirst();
        if (withoutInputFlow.isPresent()) {
          flowNodeDTO = withoutInputFlow.get();
        } else {
          flowNodeDTO = elements.values().iterator().next();
        }
      } else {
        flowNodeDTO = startEvents.getFirst();
      }
    }
    return flowNodeDTO;
  }

  @JsonIgnore
  public Optional<FlowNodeDTO> getFlowNode(String elementId) {
    return elements.values().stream()
        .filter(FlowNodeDTO.class::isInstance)
        .map(FlowNodeDTO.class::cast)
        .filter(flowElement -> elementId.equals(flowElement.getId()))
        .findFirst();
  }
}
