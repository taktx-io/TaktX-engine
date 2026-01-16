/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
 */

package io.taktx.engine.pi.model;

import io.taktx.dto.MessageEventKeyDTO;
import io.taktx.dto.ScheduleKeyDTO;
import io.taktx.engine.pd.model.CatchEvent;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public abstract class CatchEventInstance<N extends CatchEvent> extends EventInstance<N>
    implements ReceivingMessageInstance, FlowNodeInstanceWithScheduleKeys {
  private Set<ScheduleKeyDTO> scheduledKeys = new HashSet<>();
  private Map<MessageEventKeyDTO, Set<String>> messageEventKeys = new HashMap<>();

  protected CatchEventInstance(WithScope parentInstance, N flowNode, long elementInstanceId) {
    super(parentInstance, flowNode, elementInstanceId);
  }

  public void addScheduledKey(ScheduleKeyDTO scheduledKey) {
    this.scheduledKeys.add(scheduledKey);
  }

  public void addMessageSubscriptionWithCorrelationKey(
      MessageEventKeyDTO messageEventKey, String correlationKey) {
    this.messageEventKeys
        .computeIfAbsent(messageEventKey, k -> new HashSet<>())
        .add(correlationKey);
  }
}
