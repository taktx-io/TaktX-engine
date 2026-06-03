/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */

package io.taktx.engine.pi.model;

import java.util.HashSet;
import java.util.Set;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Tracks an active compensation throw event and the handlers it has invoked. */
@Getter
@Setter
@NoArgsConstructor
public class CompensationTriggerState {

  private long throwEventInstanceKey;
  private String targetActivityId;
  private Set<Long> pendingHandlerInstanceKeys = new HashSet<>();
  private Set<Long> completedHandlerInstanceKeys = new HashSet<>();
  private Set<Long> failedHandlerInstanceKeys = new HashSet<>();

  public CompensationTriggerState(long throwEventInstanceKey, String targetActivityId) {
    this.throwEventInstanceKey = throwEventInstanceKey;
    this.targetActivityId = targetActivityId;
  }

  public void addPendingHandler(long instanceKey) {
    pendingHandlerInstanceKeys.add(instanceKey);
  }

  public boolean isAllHandlersDone() {
    return pendingHandlerInstanceKeys.isEmpty();
  }
}
