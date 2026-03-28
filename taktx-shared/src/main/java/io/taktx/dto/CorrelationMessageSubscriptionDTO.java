/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
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
