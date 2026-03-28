/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */

package io.taktx.engine.pd.model;

import io.taktx.engine.pi.model.BoundaryEventInstance;
import io.taktx.engine.pi.model.IFlowNodeInstance;
import io.taktx.engine.pi.model.Scope;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Getter
@SuperBuilder
public class BoundaryEvent extends CatchEvent {
  private String attachedToRef;
  @Setter private FlowNode attachedActivity;

  private boolean cancelActivity;

  @Override
  public BoundaryEventInstance newInstance(IFlowNodeInstance parentInstance, Scope scope) {
    return new BoundaryEventInstance(parentInstance, this, scope.nextElementInstanceId());
  }
}
