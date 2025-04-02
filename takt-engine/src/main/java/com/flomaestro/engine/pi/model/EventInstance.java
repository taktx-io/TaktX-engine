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

import com.flomaestro.engine.pd.model.Event;
import lombok.NoArgsConstructor;

@NoArgsConstructor
public abstract class EventInstance<N extends Event> extends FlowNodeInstance<N> {

  protected EventInstance(FlowNodeInstance<?> parentInstance, N flowNode, long elementInstanceId) {
    super(parentInstance, flowNode, elementInstanceId);
  }

  @Override
  public boolean canSelectNextNodeStart() {
    return isCompleted();
  }

  @Override
  public boolean isAwaiting() {
    return false;
  }

  @Override
  public void setStartedState() {
    // Do nothing
  }

  @Override
  public void setInitialState() {}

  @Override
  public boolean wasAwaiting() {
    return false;
  }

  @Override
  public boolean wasNew() {
    return true;
  }

  @Override
  public boolean stateChanged() {
    return false;
  }

  @Override
  public boolean canSelectNextNodeContinue() {
    return isCompleted();
  }
}
