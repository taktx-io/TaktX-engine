/*
 *
 *  * TaktX - A high-performance BPMN engine
 *  * Copyright (c) 2025 TaktX B.V. All rights reserved.
 *  * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 *  * Free use is permitted with up to 3 Kafka partitions. See LICENSE file for details.
 *  * For commercial use or more partitions and features, contact [info@taktx.io] or [https://www.taktx.io/contact].
 *
 */

package com.flomaestro.engine.pi.model;

import com.flomaestro.engine.pd.model.ExternalTask;
import com.flomaestro.takt.dto.v_1_0_0.ScheduleKeyDTO;
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
      FlowNodeInstance<?> parentInstance, N flowNode, long elementInstanceId) {
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
