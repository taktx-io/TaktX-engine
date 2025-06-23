/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
 */

package io.taktx.engine.pi.model;

import io.taktx.engine.pd.model.Gateway;
import java.util.HashSet;
import java.util.Set;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Setter
@Getter
@NoArgsConstructor
public abstract class GatewayInstance<N extends Gateway> extends FlowNodeInstance<N> {
  private Set<String> selectedOutputFlows = new HashSet<>();

  protected GatewayInstance(
      FlowNodeInstance<?> parentInstance, N flowNode, long elementInstanceId) {
    super(parentInstance, flowNode, elementInstanceId);
  }

  @Override
  public boolean stateAllowsStart() {
    return true;
  }

  @Override
  public boolean stateAllowsContinue() {
    return false;
  }

  @Override
  public boolean stateAllowsTerminate() {
    return true;
  }

  @Override
  public boolean isNotAwaiting() {
    return true;
  }

  @Override
  public boolean wasAwaiting() {
    return false;
  }

  @Override
  public void setStartedState() {
    // Do nothing
  }

  @Override
  public void setInitialState() {
    // Do nothing
  }

  @Override
  public boolean stateChanged() {
    return false;
  }

  @Override
  public boolean wasNew() {
    return false;
  }

  @Override
  public boolean isAwaiting() {
    return false;
  }

  @Override
  public boolean isCompleted() {
    return true;
  }

  @Override
  public void terminate() {
    // Do nothing
  }

  @Override
  public boolean canSelectNextNodeStart() {
    return isCompleted();
  }

  @Override
  public boolean canSelectNextNodeContinue() {
    return isCompleted();
  }

  public abstract void resetFlows();
}
