package com.flomaestro.engine.pd.model;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.Getter;

@Getter
public class FlowElements {
  private final Map<String, FlowElement> elements = new HashMap<>();

  public FlowElement get(String id) {
    return elements.get(id);
  }

  public void addFlowElement(FlowElement flowElement) {
    elements.put(flowElement.getId(), flowElement);
  }

  public List<StartEvent> getStartEvents() {
    return elements.values().stream()
        .filter(StartEvent.class::isInstance)
        .map(StartEvent.class::cast)
        .toList();
  }

  public List<FlowNode> getFlowNodes() {
    return elements.values().stream()
        .filter(FlowNode.class::isInstance)
        .map(FlowNode.class::cast)
        .toList();
  }

  public Optional<FlowNode> getFlowNode(String id) {
    return elements.values().stream()
        .filter(FlowNode.class::isInstance)
        .map(FlowNode.class::cast)
        .filter(flowNode -> flowNode.getId().equals(id))
        .findFirst();
  }

  public Optional<Activity> getActivity(String id) {
    return elements.values().stream()
        .filter(Activity.class::isInstance)
        .map(Activity.class::cast)
        .filter(flowNode -> flowNode.getId().equals(id))
        .findFirst();
  }

  public FlowNode getStartNode(String elementId) {
    FlowNode flowNode = null;
    if (elementId != null) {
      flowNode = getFlowNode(elementId).orElse(null);
    }
    if (flowNode == null) {
      List<StartEvent> startEvents = getStartEvents();
      if (startEvents.isEmpty()) {
        Optional<FlowNode> withoutInputFlow =
            elements.values().stream()
                .filter(FlowNode.class::isInstance)
                .map(FlowNode.class::cast)
                .filter(node -> !node.getIncoming().isEmpty())
                .findFirst();
        flowNode = withoutInputFlow.orElseGet(() -> getFlowNodes().getFirst());
      } else {
        flowNode = startEvents.getFirst();
      }
    }
    return flowNode;
  }

  public Map<String, SequenceFlow> getSequenceFlows() {
    return elements.values().stream()
        .filter(SequenceFlow.class::isInstance)
        .map(SequenceFlow.class::cast)
        .collect(Collectors.toMap(SequenceFlow::getId, Function.identity()));
  }

  public Optional<IntermediateCatchEvent> getIntermediateCatchEventWithName(String name) {
    return elements.values().stream()
        .filter(IntermediateCatchEvent.class::isInstance)
        .map(IntermediateCatchEvent.class::cast)
        .filter(intermediateCatchEvent -> intermediateCatchEvent.hasLinkEventDefinition(name))
        .findFirst();
  }
}
