/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
 */

package io.taktx.engine.pi.model;

import io.taktx.dto.ExecutionState;
import io.taktx.engine.pd.model.FlowNode;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public abstract class FlowNodeInstance<N extends FlowNode> implements IFlowNodeInstance {
  private ExecutionState state = ExecutionState.INITIALIZED;

  private boolean stateChanged = false;

  private boolean wasWaiting = false;

  private boolean wasNew = false;

  private long elementInstanceId;

  private int passedCnt;

  private N flowNode;

  private FlowNodeInstance<?> parentInstance;

  private boolean dirty = false;

  protected FlowNodeInstance(
      FlowNodeInstance<?> parentInstance, N flowNode, long elementInstanceId) {
    this.parentInstance = parentInstance;
    this.elementInstanceId = elementInstanceId;
    this.flowNode = flowNode;
  }

  @Override
  public List<Long> createKeyPath() {
    List<Long> parentKeyPath =
        parentInstance != null ? parentInstance.createKeyPath() : new ArrayList<>();
    parentKeyPath.add(elementInstanceId);
    return parentKeyPath;
  }

  public void increasePassedCnt() {
    this.passedCnt++;
    setDirty();
  }

  public boolean isDirty() {
    boolean result = dirty;
    if (!dirty && this instanceof WithScope withScope) {
      result |= withScope.getScope().isDirty();
    }
    return result;
  }

  public void setStateNoChange(ExecutionState state) {
    this.state = state;
    if (state == ExecutionState.ACTIVE) {
      wasWaiting = true;
    }
  }

  public void setState(ExecutionState state) {
    if (this.state != state) {
      stateChanged = true;
      setDirty();
    }
    if (this.state == ExecutionState.INITIALIZED && this.state != state) {
      wasNew = true;
    }
    if (state == ExecutionState.ACTIVE) {
      wasWaiting = true;
    }
    this.state = state;
  }

  public boolean wasNew() {
    return wasNew;
  }

  public boolean wasAwaiting() {
    return wasWaiting;
  }

  public boolean stateChanged() {
    return stateChanged;
  }

  public boolean stateAllowsStart() {
    return state == ExecutionState.INITIALIZED;
  }

  public boolean isDone() {
    return state == ExecutionState.COMPLETED
        || state == ExecutionState.ABORTED
        || state == ExecutionState.CANCELED;
  }

  public boolean stateAllowsStopping() {
    return state == ExecutionState.ACTIVE || state == ExecutionState.INITIALIZED;
  }

  public boolean stateAllowsContinue() {
    return state == ExecutionState.ACTIVE;
  }

  public boolean isNotAwaiting() {
    return state == ExecutionState.COMPLETED || state == ExecutionState.ABORTED;
  }

  public void abort() {
    if (stateAllowsStopping()) {
      setState(ExecutionState.ABORTED);
    }
  }

  public void cancel() {
    if (stateAllowsStopping()) {
      setState(ExecutionState.CANCELED);
    }
  }

  public abstract boolean canSelectNextNodeStart();

  public abstract boolean canSelectNextNodeContinue();

  public void setDirty() {
    dirty = true;
  }

  public boolean isActive() {
    return state == ExecutionState.ACTIVE;
  }

  public void setStartedState() {
    setState(ExecutionState.ACTIVE);
  }
}
