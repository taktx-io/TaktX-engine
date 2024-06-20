package nl.qunit.bpmnmeister.pd.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.annotation.Nonnull;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.EqualsAndHashCode;
import lombok.Getter;

@Getter
@EqualsAndHashCode
public class FlowElements {
  public static final FlowElements EMPTY = new FlowElements(Map.of());

  private final Map<String, FlowElement> elements;

  @JsonCreator
  public FlowElements(@Nonnull @JsonProperty("elements") Map<String, FlowElement> elements) {
    this.elements = elements;
  }

  @JsonIgnore
  public List<BaseElement> values() {
    return List.copyOf(elements.values());
  }

  @JsonIgnore
  public Set<String> keySet() {
    return Set.copyOf(elements.keySet());
  }

  @JsonIgnore
  public FlowElement get(String id) {
    return elements.get(id);
  }

  @JsonIgnore
  public List<StartEvent> getStartEvents() {
    return elements.values().stream()
        .filter(StartEvent.class::isInstance)
        .map(StartEvent.class::cast)
        .toList();
  }

  @JsonIgnore
  public List<FlowNode> getFlowNodes() {
    return elements.values().stream()
        .filter(FlowNode.class::isInstance)
        .map(FlowNode.class::cast)
        .toList();
  }

  @JsonIgnore
  public List<Activity> getActivities() {
    return elements.values().stream()
        .filter(Activity.class::isInstance)
        .map(Activity.class::cast)
        .toList();
  }

  @JsonIgnore
  public Optional<FlowElement> getFlowElement(String id) {
    return elements.values().stream()
        .filter(flowElement -> id.equals(flowElement.getId()))
        .findFirst();
  }

  @JsonIgnore
  public Optional<FlowNode> getFlowNode(String elementId) {
    return elements.values().stream()
        .filter(FlowNode.class::isInstance)
        .map(FlowNode.class::cast)
        .filter(flowElement -> elementId.equals(flowElement.getId()))
        .findFirst();
  }

  @JsonIgnore
  public List<BoundaryEvent> getBoundaryEventsAttachedToElement(String id) {
    return elements.values().stream()
        .filter(BoundaryEvent.class::isInstance)
        .map(BoundaryEvent.class::cast)
        .filter(boundaryEvent -> boundaryEvent.getAttachedToRef().equals(id))
        .toList();
  }

  @JsonIgnore
  public List<SequenceFlow> getOutgoingSequenceFlowsForElement(FlowNode<?> element) {
    return element.getOutgoing().stream()
        .map(this::getFlowElement)
        .filter(Optional::isPresent)
        .map(Optional::get)
        .filter(SequenceFlow.class::isInstance)
        .map(SequenceFlow.class::cast)
        .toList();
  }

  @JsonIgnore
  public Optional<FlowNode<?>> getFlowNodeWithIncomingFlow(String sequenceFlowId) {
    FlowElement flowElement = elements.get(sequenceFlowId);
    if (flowElement instanceof SequenceFlow sequenceFlow) {
      return Optional.ofNullable((FlowNode<?>) elements.get(sequenceFlow.getTarget()));
    }
    return Optional.empty();
  }

  @JsonIgnore
  public Optional<FlowNode<?>> getFlowNodeWithOutgoingFlow(String sequenceFlowId) {
    FlowElement flowElement = elements.get(sequenceFlowId);
    if (flowElement instanceof SequenceFlow sequenceFlow) {
      return Optional.ofNullable((FlowNode<?>) elements.get(sequenceFlow.getSource()));
    }
    return Optional.empty();
  }

  public Optional<IntermediateCatchEvent> getLinkedCatchElement(String name) {
    return elements.values().stream()
        .filter(IntermediateCatchEvent.class::isInstance)
        .map(IntermediateCatchEvent.class::cast)
        .filter(imce -> imce.getLinkventDefinitions().stream().anyMatch(def -> def.getName().equals(name)))
        .findFirst();

  }
}
