/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
 */

package io.taktx.dto;

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
public class CorrelationMessageEventTriggerDTO extends MessageEventDTO {

  private String correlationKey;

  private VariablesDTO variables;

  public CorrelationMessageEventTriggerDTO(
      String messageName, String correlationKey, VariablesDTO variables) {
    super(messageName);
    this.correlationKey = correlationKey;
    this.variables = variables;
  }
}
