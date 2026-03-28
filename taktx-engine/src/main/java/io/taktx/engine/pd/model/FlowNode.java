/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */

package io.taktx.engine.pd.model;

import io.taktx.engine.pi.model.FlowNodeInstance;
import io.taktx.engine.pi.model.IFlowNodeInstance;
import io.taktx.engine.pi.model.Scope;
import java.util.HashSet;
import java.util.Set;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Getter
@SuperBuilder
@NoArgsConstructor
public abstract class FlowNode extends FlowElement {
  @Builder.Default private Set<String> incoming = new HashSet<>();
  @Builder.Default private Set<String> outgoing = new HashSet<>();

  private final Set<SequenceFlow> incomingSequenceFlows = new HashSet<>();
  private final Set<SequenceFlow> outGoingSequenceFlows = new HashSet<>();

  public FlowNodeInstance<?> createAndStoreNewInstance(
      IFlowNodeInstance parentInstance, Scope scope) {
    FlowNodeInstance<?> newInstance = newInstance(parentInstance, scope);
    scope.putInstance(newInstance);
    return newInstance;
  }

  public abstract FlowNodeInstance<?> newInstance(IFlowNodeInstance parentInstance, Scope scope);
}
