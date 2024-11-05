package nl.qunit.bpmnmeister.engine.pi;

import jakarta.enterprise.context.ApplicationScoped;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import nl.qunit.bpmnmeister.pd.model.FlowElement;
import nl.qunit.bpmnmeister.pd.model.FlowNode;
import nl.qunit.bpmnmeister.pi.instances.FLowNodeInstance;
import nl.qunit.bpmnmeister.pi.instances.IFlowNodeInstance;

@ApplicationScoped
public class PathExtractor {

  public List<UUID> getInstancePath(IFlowNodeInstance fLowNodeInstance) {
    List<UUID> instancePath = new ArrayList<>();
    instancePath.add(fLowNodeInstance.getElementInstanceId());

    FLowNodeInstance<?> parent = fLowNodeInstance.getParentInstance();
    while (parent != null) {
      instancePath.add(parent.getElementInstanceId());
      parent = parent.getParentInstance();
    }
    Collections.reverse(instancePath);
    return instancePath;
  }

  public List<String> getElementIdPath(FlowNode flowNode) {
    // Create a list of parent element IDs recursively from the element's parent, the order of the
    // list is from the root to the parent of the element
    List<String> elementIdPath = new ArrayList<>();
    elementIdPath.add(flowNode.getId());
    FlowElement parent = flowNode.getParentElement();
    while (parent != null) {
      elementIdPath.add(parent.getId());
      parent = parent.getParentElement();
    }
    Collections.reverse(elementIdPath);
    return elementIdPath;
  }
}
