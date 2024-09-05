package nl.qunit.bpmnmeister.pd.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.annotation.Nonnull;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.Data;

@Data
public class FlowElementsDTO {
  public static final FlowElementsDTO EMPTY = new FlowElementsDTO(Map.of());

  private final Map<String, FlowElementDTO> elements;

  @JsonCreator
  public FlowElementsDTO(@Nonnull @JsonProperty("elements") Map<String, FlowElementDTO> elements) {
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
                .filter(e -> e instanceof FlowNodeDTO)
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
  public List<FlowNodeDTO> getFlowNodes() {
    return elements.values().stream()
        .filter(FlowNodeDTO.class::isInstance)
        .map(FlowNodeDTO.class::cast)
        .toList();
  }

  @JsonIgnore
  public List<ActivityDTO> getActivities() {
    return elements.values().stream()
        .filter(ActivityDTO.class::isInstance)
        .map(ActivityDTO.class::cast)
        .toList();
  }

  @JsonIgnore
  public Optional<FlowElementDTO> getFlowElement(String id) {
    return elements.values().stream()
        .filter(flowElement -> id.equals(flowElement.getId()))
        .findFirst();
  }

  @JsonIgnore
  public Optional<FlowNodeDTO> getFlowNode(String elementId) {
    return elements.values().stream()
        .filter(FlowNodeDTO.class::isInstance)
        .map(FlowNodeDTO.class::cast)
        .filter(flowElement -> elementId.equals(flowElement.getId()))
        .findFirst();
  }

  @JsonIgnore
  public List<BoundaryEventDTO> getBoundaryEventsAttachedToElement(String id) {
    return elements.values().stream()
        .filter(BoundaryEventDTO.class::isInstance)
        .map(BoundaryEventDTO.class::cast)
        .filter(boundaryEvent -> boundaryEvent.getAttachedToRef().equals(id))
        .toList();
  }

  @JsonIgnore
  public List<SequenceFlowDTO> getOutgoingSequenceFlowsForElement(FlowNodeDTO element) {
    return element.getOutgoing().stream()
        .map(this::getFlowElement)
        .filter(Optional::isPresent)
        .map(Optional::get)
        .filter(SequenceFlowDTO.class::isInstance)
        .map(SequenceFlowDTO.class::cast)
        .toList();
  }

  @JsonIgnore
  public Optional<FlowNodeDTO> getFlowNodeWithIncomingFlow(String sequenceFlowId) {
    FlowElementDTO flowElement = elements.get(sequenceFlowId);
    if (flowElement instanceof SequenceFlowDTO sequenceFlow) {
      return Optional.ofNullable((FlowNodeDTO) elements.get(sequenceFlow.getTarget()));
    }
    return Optional.empty();
  }

  @JsonIgnore
  public Optional<FlowNodeDTO> getFlowNodeWithOutgoingFlow(String sequenceFlowId) {
    FlowElementDTO flowElement = elements.get(sequenceFlowId);
    if (flowElement instanceof SequenceFlowDTO sequenceFlow) {
      return Optional.ofNullable((FlowNodeDTO) elements.get(sequenceFlow.getSource()));
    }
    return Optional.empty();
  }

  public Optional<IntermediateCatchEvent> getLinkedCatchElement(String name) {
    return elements.values().stream()
        .filter(IntermediateCatchEvent.class::isInstance)
        .map(IntermediateCatchEvent.class::cast)
        .filter(
            imce ->
                imce.getLinkventDefinitions().stream().anyMatch(def -> def.getName().equals(name)))
        .findFirst();
  }
}
