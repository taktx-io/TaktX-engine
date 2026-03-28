/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */

package io.taktx.engine.pd.model;

import io.taktx.engine.pi.model.ActivityInstance;
import io.taktx.engine.pi.model.IFlowNodeInstance;
import io.taktx.engine.pi.model.MultiInstanceInstance;
import io.taktx.engine.pi.model.Scope;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Getter
@NoArgsConstructor
@SuperBuilder
public abstract class Activity extends FlowNode implements WithIoMapping {
  private LoopCharacteristics loopCharacteristics;
  private InputOutputMapping ioMapping;

  @Setter private List<BoundaryEvent> boundaryEvents;

  @Override
  public final ActivityInstance<?> newInstance(IFlowNodeInstance parentInstance, Scope scope) {
    if (loopCharacteristics != null && !loopCharacteristics.equals(LoopCharacteristics.NONE)) {
      return new MultiInstanceInstance(this, parentInstance, scope.nextElementInstanceId());
    } else {
      return newActivityInstance(parentInstance, scope.nextElementInstanceId());
    }
  }

  public List<BoundaryEvent> getBoundaryEvents() {
    if (boundaryEvents == null) {
      boundaryEvents = new ArrayList<>();
    }
    return boundaryEvents;
  }

  public void addBoundaryEvent(BoundaryEvent boundaryEvent) {
    getBoundaryEvents().add(boundaryEvent);
  }

  public abstract ActivityInstance<?> newActivityInstance(
      IFlowNodeInstance parentInstance, long elementInstanceId);
}
