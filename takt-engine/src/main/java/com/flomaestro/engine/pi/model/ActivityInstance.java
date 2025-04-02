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

import com.fasterxml.jackson.databind.JsonNode;
import com.flomaestro.engine.pd.model.FlowNode;
import com.flomaestro.takt.dto.v_1_0_0.ActtivityStateEnum;
import java.util.HashSet;
import java.util.Set;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@NoArgsConstructor
@Setter
@Getter
public abstract class ActivityInstance<N extends FlowNode> extends FlowNodeInstance<N> {
  private ActtivityStateEnum state = null;
  private boolean stateChanged = false;
  private boolean wasWaiting = false;
  private boolean wasNew = false;
  private boolean iteration = false;
  private long nextIterationId;
  private JsonNode inputElement;
  private JsonNode outputElement;
  private int loopCnt;

  private Set<Long> boundaryEventIds = new HashSet<>();

  protected ActivityInstance(
      FlowNodeInstance<?> parentInstance, N flowNode, long elementInstanceId) {
    super(parentInstance, flowNode, elementInstanceId);
  }

  public void addBoundaryEventId(long boundaryEventId) {
    getBoundaryEventIds().add(boundaryEventId);
  }

  public Set<Long> getBoundaryEventIds() {
    if (boundaryEventIds == null) {
      boundaryEventIds = new HashSet<>();
    }
    return boundaryEventIds;
  }

  public void setOutputElement(JsonNode outputElement) {
    this.outputElement = outputElement;
    setDirty();
  }

  public void setInputElement(JsonNode inputElement) {
    this.inputElement = inputElement;
    setDirty();
  }

  @Override
  public void setInitialState() {
    setState(ActtivityStateEnum.INITIAL);
  }

  @Override
  public void setStartedState() {
    setState(ActtivityStateEnum.STARTED);
  }

  public void setState(ActtivityStateEnum state) {
    if (this.state == null && state == ActtivityStateEnum.INITIAL) {
      setDirty();
    }
    if (this.state != null && this.state != state) {
      stateChanged = true;
      setDirty();
    }
    if (this.state == ActtivityStateEnum.INITIAL && this.state != state) {
      wasNew = true;
    }
    if (state == ActtivityStateEnum.WAITING) {
      wasWaiting = true;
    }
    this.state = state;
  }

  @Override
  public boolean wasNew() {
    return wasNew;
  }

  @Override
  public boolean wasAwaiting() {
    return wasWaiting;
  }

  @Override
  public boolean stateChanged() {
    return stateChanged;
  }

  @Override
  public boolean stateAllowsStart() {
    return state == ActtivityStateEnum.INITIAL;
  }

  @Override
  public boolean stateAllowsContinue() {
    return state == ActtivityStateEnum.WAITING;
  }

  @Override
  public boolean stateAllowsTerminate() {
    return state == ActtivityStateEnum.INITIAL || state == ActtivityStateEnum.WAITING;
  }

  @Override
  public boolean isNotAwaiting() {
    return state == ActtivityStateEnum.FINISHED || state == ActtivityStateEnum.TERMINATED;
  }

  @Override
  public boolean isAwaiting() {
    return state == ActtivityStateEnum.WAITING;
  }

  @Override
  public boolean isCompleted() {
    return state == ActtivityStateEnum.FINISHED || state == ActtivityStateEnum.TERMINATED;
  }

  @Override
  public void terminate() {
    setState(ActtivityStateEnum.TERMINATED);
  }

  @Override
  public boolean canSelectNextNodeStart() {
    return isCompleted();
  }

  @Override
  public boolean canSelectNextNodeContinue() {
    return state == ActtivityStateEnum.FINISHED;
  }
}
