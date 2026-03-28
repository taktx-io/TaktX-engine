/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */

package io.taktx.engine.pi.model;

import io.taktx.dto.ScheduleKeyDTO;
import io.taktx.engine.pd.model.ExternalTask;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Setter
@Getter
@NoArgsConstructor
public class ExternalTaskInstance<N extends ExternalTask> extends ActivityInstance<N>
    implements FlowNodeInstanceWithScheduleKeys {
  private int attempt;
  private List<ScheduleKeyDTO> scheduledKeys = new ArrayList<>();

  public ExternalTaskInstance(
      IFlowNodeInstance parentInstance, N flowNode, long elementInstanceId) {
    super(parentInstance, flowNode, elementInstanceId);
  }

  public int increaseAttempt() {
    setDirty();
    return ++attempt;
  }

  @Override
  public void addScheduledKey(ScheduleKeyDTO scheduledKey) {
    this.scheduledKeys.add(scheduledKey);
  }

  public void clearScheduledKeys() {
    this.scheduledKeys.clear();
  }
}
