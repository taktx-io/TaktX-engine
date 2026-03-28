/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */

package io.taktx.engine.pi.model;

import io.taktx.dto.ExecutionState;
import io.taktx.engine.pd.model.FlowNode;
import io.taktx.engine.pi.ProcessInstanceException;
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

  private boolean counted = false;

  private long elementInstanceId;

  private int passedCnt;

  private N flowNode;

  private IFlowNodeInstance parentInstance;

  private boolean dirty = false;

  private boolean incident = false;

  protected FlowNodeInstance(IFlowNodeInstance parentInstance, N flowNode, long elementInstanceId) {
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
    return state == ExecutionState.INITIALIZED && !incident;
  }

  public boolean isDone() {
    return getState() == ExecutionState.COMPLETED || getState() == ExecutionState.ABORTED;
  }

  public boolean stateAllowsStopping() {
    return !incident && (state == ExecutionState.ACTIVE || state == ExecutionState.INITIALIZED);
  }

  public boolean stateAllowsContinue() {
    return state == ExecutionState.ACTIVE && !incident;
  }

  public boolean isNotAwaiting() {
    return state == ExecutionState.COMPLETED || state == ExecutionState.ABORTED;
  }

  public void abort() {
    if (stateAllowsStopping()) {
      setState(ExecutionState.ABORTED);
    }
  }

  public void raiseIncident(String message) {
    this.incident = true;
    setDirty();
    throw new ProcessInstanceException(this, message);
  }

  public abstract boolean canSelectNextNodeStart();

  public abstract boolean canSelectNextNodeContinue();

  public void setDirty() {
    dirty = true;
  }

  public boolean isActive() {
    return state == ExecutionState.ACTIVE;
  }

  public boolean isCmpleted() {
    return state == ExecutionState.COMPLETED;
  }

  public boolean isIteration() {
    return false;
  }
}
