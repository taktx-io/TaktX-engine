package nl.qunit.bpmnmeister.pd.model;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class FlowElements2 {
  private final Map<String, FlowElement2> elements = new HashMap<>();

  public FlowElements2() {}

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

  public FlowElements2 getChildElements(List<String> parentIds) {
    FlowElements2 elements = this;
    for (String parentId : parentIds) {
      FlowElement2 element = elements.get(parentId);
      if (element instanceof SubProcess2 subProcess) {
        elements = subProcess.getElements();
      } else {
        return elements;
      }
    }
    return elements;
  }

  public Optional<FlowNode2> getFlowNode(String id) {
    return elements.values().stream()
        .filter(FlowNode2.class::isInstance)
        .map(FlowNode2.class::cast)
        .filter(flowNode -> flowNode.getId().equals(id))
        .findFirst();
  }

  public Optional<StartEvent2> getStartEvent(String elementId) {
    return elements.values().stream()
        .filter(StartEvent2.class::isInstance)
        .map(StartEvent2.class::cast)
        .filter(event2 -> event2.getId().equals(elementId))
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
                .filter(e -> e instanceof FlowNode2)
                .map(e -> (FlowNode2) e)
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
}
