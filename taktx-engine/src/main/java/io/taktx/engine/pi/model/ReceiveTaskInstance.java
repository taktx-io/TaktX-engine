/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
 */

package io.taktx.engine.pi.model;

import io.taktx.dto.MessageEventKeyDTO;
import io.taktx.engine.pd.model.ReceiveTask;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@NoArgsConstructor
@Getter
@Setter
public class ReceiveTaskInstance extends ActivityInstance<ReceiveTask>
    implements ReceivingMessageInstance {

  private Map<MessageEventKeyDTO, Set<String>> messageEventKeys;

  private String correlationKey;

  public ReceiveTaskInstance(
      WithScope parentInstance, ReceiveTask flowNode, long elementInstanceId) {
    super(parentInstance, flowNode, elementInstanceId);
    messageEventKeys = new HashMap<>();
  }

  @Override
  public void addMessageSubscriptionWithCorrelationKey(
      MessageEventKeyDTO messageEventKey, String correlationKey) {
    messageEventKeys.computeIfAbsent(messageEventKey, k -> new HashSet<>()).add(correlationKey);
  }

  @Override
  public Map<MessageEventKeyDTO, Set<String>> getMessageEventKeys() {
    return messageEventKeys;
  }
}
