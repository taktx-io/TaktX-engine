package com.flomaestro.engine.pi;

import com.flomaestro.engine.pd.model.FlowElement;
import com.flomaestro.engine.pd.model.FlowNode;
import com.flomaestro.engine.pi.model.FlowNodeInstance;
import com.flomaestro.engine.pi.model.IFlowNodeInstance;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@ApplicationScoped
public class PathExtractor {

  public List<Long> getInstancePath(IFlowNodeInstance fLowNodeInstance) {
    List<Long> instancePath = new ArrayList<>();
    instancePath.add(fLowNodeInstance.getElementInstanceId());

    FlowNodeInstance<?> parent = fLowNodeInstance.getParentInstance();
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
