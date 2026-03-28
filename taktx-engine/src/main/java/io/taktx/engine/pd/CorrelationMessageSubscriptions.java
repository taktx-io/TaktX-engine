/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */

package io.taktx.engine.pd;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.taktx.dto.CorrelationMessageSubscriptionDTO;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode
public class CorrelationMessageSubscriptions {

  private Map<String, CorrelationMessageSubscriptionDTO> instances;

  public CorrelationMessageSubscriptions(Map<String, CorrelationMessageSubscriptionDTO> instances) {
    this.instances = instances;
  }

  @JsonIgnore
  public CorrelationMessageSubscriptions update(
      CorrelationMessageSubscriptionDTO messageSubscription) {
    Map<String, CorrelationMessageSubscriptionDTO> newInstances = new HashMap<>(instances);
    newInstances.put(messageSubscription.getCorrelationKey(), messageSubscription);
    return new CorrelationMessageSubscriptions(newInstances);
  }

  @JsonIgnore
  public CorrelationMessageSubscriptions remove(String correlationKey) {
    Map<String, CorrelationMessageSubscriptionDTO> newInstances = new HashMap<>(instances);
    newInstances.remove(correlationKey);
    return new CorrelationMessageSubscriptions(newInstances);
  }

  @JsonIgnore
  public CorrelationMessageSubscriptions removeAll(Set<String> toRemove) {
    Map<String, CorrelationMessageSubscriptionDTO> newInstances = new HashMap<>(instances);
    toRemove.forEach(newInstances::remove);
    return new CorrelationMessageSubscriptions(newInstances);
  }
}
