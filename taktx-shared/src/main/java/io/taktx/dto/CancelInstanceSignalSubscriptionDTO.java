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
public class CancelInstanceSignalSubscriptionDTO extends SignalDTO {

  private UUID processInstanceId;

  private List<Long> elementInstanceIdPath;

  public CancelInstanceSignalSubscriptionDTO(
      UUID processInstanceId, List<Long> elementInstanceIdPath, String signalName) {
    super(signalName);
    this.processInstanceId = processInstanceId;
    this.elementInstanceIdPath = elementInstanceIdPath;
  }
}
