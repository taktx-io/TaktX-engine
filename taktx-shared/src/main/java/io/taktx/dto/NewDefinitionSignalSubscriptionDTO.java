/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
 */

package io.taktx.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;
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
public class NewDefinitionSignalSubscriptionDTO extends SignalDTO {

  private ProcessDefinitionKey processDefinitionKey;

  private String elementId;

  public NewDefinitionSignalSubscriptionDTO(
      ProcessDefinitionKey processDefinitionKey, String elementId, String signalName) {
    super(signalName);
    this.processDefinitionKey = processDefinitionKey;
    this.elementId = elementId;
  }
}
