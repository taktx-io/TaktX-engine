package nl.qunit.bpmnmeister.pd.model;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

public class FlowElements2 {
  private final Map<String, FlowElement2> elements = new HashMap<>();

  public FlowElement2 get(String id) {
    return elements.get(id);
  }

  public Map<String, FlowElement2> getElements() {
    return elements;
  }

  public void addFlowElement(FlowElement2 flowElement) {
    elements.put(flowElement.getId(), flowElement);
  }

  public List<StartEvent2> getStartEvents() {
    return elements.values().stream()
        .filter(StartEvent2.class::isInstance)
        .map(StartEvent2.class::cast)
        .toList();
  }

  public List<FlowNode2> getFlowNodes() {
    return elements.values().stream()
        .filter(FlowNode2.class::isInstance)
        .map(FlowNode2.class::cast)
        .toList();
  }

  public Optional<FlowNode2> getFlowNode(String id) {
    return elements.values().stream()
        .filter(FlowNode2.class::isInstance)
        .map(FlowNode2.class::cast)
        .filter(flowNode -> flowNode.getId().equals(id))
        .findFirst();
  }

  public FlowNode2 getStartNode(String elementId) {
    FlowNode2 flowNode = null;
    if (!elementId.equals(Constants.NONE)) {
      flowNode = getFlowNode(elementId).orElse(null);
    }
    if (flowNode == null) {
      List<StartEvent2> startEvents = getStartEvents();
      if (startEvents.isEmpty()) {
        Optional<FlowNode2> withoutInputFlow =
            elements.values().stream()
                .filter(FlowNode2.class::isInstance)
                .map(FlowNode2.class::cast)
                .filter(node -> !node.getIncoming().isEmpty())
                .findFirst();
        flowNode = withoutInputFlow.orElseGet(() -> getFlowNodes().iterator().next());
      } else {
        flowNode = startEvents.getFirst();
      }
    }
    return flowNode;
  }

  public Iterable<Activity2> getActivities() {
    return elements.values().stream()
        .filter(Activity2.class::isInstance)
        .map(Activity2.class::cast)
        .toList();
  }

  public Optional<SequenceFlow2> getSequenceFlow(String id) {
    return elements.values().stream()
        .filter(SequenceFlow2.class::isInstance)
        .map(SequenceFlow2.class::cast)
        .filter(sequenceFlow -> sequenceFlow.getId().equals(id))
        .findFirst();
  }

  public Map<String, SequenceFlow2> getSequenceFlows() {
    return elements.values().stream()
        .filter(SequenceFlow2.class::isInstance)
        .map(SequenceFlow2.class::cast)
        .collect(Collectors.toMap(SequenceFlow2::getId, Function.identity()));
  }
}
