/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
 */

package io.taktx.engine.pd.model;

import io.taktx.dto.ExecutionState;
import io.taktx.engine.pi.model.ActivityInstance;
import io.taktx.engine.pi.model.FlowNodeInstance;
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
  public final ActivityInstance<?> newInstance(FlowNodeInstance<?> parentInstance, Scope scope) {
    if (loopCharacteristics != null && !loopCharacteristics.equals(LoopCharacteristics.NONE)) {
      return new MultiInstanceInstance(this, parentInstance, scope.nextElementInstanceId());
    } else {
      ActivityInstance<?> activityInstance =
          newActivityInstance(parentInstance, scope.nextElementInstanceId());
      activityInstance.setState(ExecutionState.INITIALIZED);
      return activityInstance;
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
      FlowNodeInstance<?> parentInstance, long elementInstanceId);
}
