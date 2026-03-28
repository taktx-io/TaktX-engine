/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */

package io.taktx.engine.pi;

import io.taktx.engine.pd.model.FlowElement;
import io.taktx.engine.pd.model.FlowNode;
import io.taktx.engine.pi.model.IFlowNodeInstance;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@ApplicationScoped
public class PathExtractor {

  public List<Long> getInstancePath(IFlowNodeInstance fLowNodeInstance) {
    List<Long> instancePath = new ArrayList<>();

    while (fLowNodeInstance != null) {
      instancePath.add(fLowNodeInstance.getElementInstanceId());
      fLowNodeInstance = fLowNodeInstance.getParentInstance();
    }

    Collections.reverse(instancePath);
    return instancePath;
  }

  public List<String> getElementPath(FlowNode flowNode) {
    List<String> elementPath = new ArrayList<>();
    elementPath.add(flowNode.getId());

    FlowElement parentElement = flowNode.getParentElement();
    while (parentElement != null) {
      elementPath.add(parentElement.getId());
      parentElement = parentElement.getParentElement();
    }
    Collections.reverse(elementPath);
    return elementPath;
  }
}
