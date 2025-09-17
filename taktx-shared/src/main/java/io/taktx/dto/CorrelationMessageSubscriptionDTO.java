/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
 */

package io.taktx.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;
import java.util.List;
import java.util.UUID;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@RegisterForReflection
public class CorrelationMessageSubscriptionDTO extends MessageEventDTO {

  private UUID processInstanceId;

  private String correlationKey;

  private List<Long> elementInstanceIdPath;

  private String elementId;

  public CorrelationMessageSubscriptionDTO(
      UUID processInstanceId,
      String correlationKey,
      List<Long> elementInstanceIdPath,
      String elementId,
      String messageName) {
    super(messageName);
    this.processInstanceId = processInstanceId;
    this.correlationKey = correlationKey;
    this.elementInstanceIdPath = elementInstanceIdPath;
    this.elementId = elementId;
  }
}
