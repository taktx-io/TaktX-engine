/*
 *
 *  * TaktX - A high-performance BPMN engine
 *  * Copyright (c) 2025 TaktX B.V. All rights reserved.
 *  * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 *  * Free use is permitted with up to 3 Kafka partitions. See LICENSE file for details.
 *  * For commercial use or more partitions and features, contact [info@taktx.io] or [https://www.taktx.io/contact].
 *
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
