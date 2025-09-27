/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
 */

package io.taktx.engine.pi.model;

import io.taktx.dto.FlowNodeStateEnum;
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
  private FlowNodeStateEnum state = null;

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

  public void setState(FlowNodeStateEnum state) {
    if (this.state == null && state == FlowNodeStateEnum.INITIAL) {
      setDirty();
    }
    if (this.state != null && this.state != state) {
      stateChanged = true;
      setDirty();
    }
    if (this.state == FlowNodeStateEnum.INITIAL && this.state != state) {
      wasNew = true;
    }
    if (state == FlowNodeStateEnum.ACTIVE) {
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
    return state == FlowNodeStateEnum.INITIAL;
  }

  public boolean isDone() {
    return state == FlowNodeStateEnum.COMPLETED
        || state == FlowNodeStateEnum.ABORTED
        || state == FlowNodeStateEnum.CANCELED;
  }

  public boolean stateAllowsStopping() {
    return state == FlowNodeStateEnum.ACTIVE || state == FlowNodeStateEnum.INITIAL;
  }

  public boolean stateAllowsContinue() {
    return state == FlowNodeStateEnum.ACTIVE;
  }

  public boolean isNotAwaiting() {
    return state == FlowNodeStateEnum.COMPLETED || state == FlowNodeStateEnum.ABORTED;
  }

  public void abort() {
    if (stateAllowsStopping()) {
      setState(FlowNodeStateEnum.ABORTED);
    }
  }

  public void cancel() {
    if (stateAllowsStopping()) {
      setState(FlowNodeStateEnum.CANCELED);
    }
  }

  public abstract boolean canSelectNextNodeStart();

  public abstract boolean canSelectNextNodeContinue();

  public void setDirty() {
    dirty = true;
  }

  public boolean isActive() {
    return state == FlowNodeStateEnum.ACTIVE;
  }

  public void setInitialState() {
    setState(FlowNodeStateEnum.INITIAL);
  }

  public void setStartedState() {
    setState(FlowNodeStateEnum.ACTIVE);
  }
}
