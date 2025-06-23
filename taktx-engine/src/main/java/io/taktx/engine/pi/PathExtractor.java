/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
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
