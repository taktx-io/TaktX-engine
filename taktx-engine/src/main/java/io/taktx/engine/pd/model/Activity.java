/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
 */

package io.taktx.engine.pd.model;

import io.taktx.engine.pi.model.ActivityInstance;
import io.taktx.engine.pi.model.MultiInstanceInstance;
import io.taktx.engine.pi.model.Scope;
import io.taktx.engine.pi.model.WithScope;
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
  public final ActivityInstance<?> newInstance(WithScope parentInstance, Scope scope) {
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
      WithScope parentInstance, long elementInstanceId);
}
