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
import io.taktx.engine.pi.model.SubProcessInstance;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Getter
@SuperBuilder
@NoArgsConstructor
public class SubProcess extends Activity implements WIthChildElements {
  private FlowElements elements;
  private boolean triggeredByEvent;

  @Override
  public ActivityInstance<?> newActivityInstance(
      IFlowNodeInstance parentInstance, long elementInstanceId) {
    return new SubProcessInstance(parentInstance, this, elementInstanceId);
  }
}
