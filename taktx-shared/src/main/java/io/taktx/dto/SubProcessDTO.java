/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;
import java.util.Set;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@RegisterForReflection
public class SubProcessDTO extends ActivityDTO {

  private FlowElementsDTO elements;
  private boolean triggeredByEvent;

  public SubProcessDTO(
      String id,
      String parentId,
      String name,
      Set<String> incoming,
      Set<String> outgoing,
      LoopCharacteristicsDTO loopCharacteristics,
      FlowElementsDTO elements,
      InputOutputMappingDTO ioMapping,
      boolean triggeredByEvent) {

    super(id, parentId, name, incoming, outgoing, loopCharacteristics, ioMapping);
    this.elements = elements;
    this.triggeredByEvent = triggeredByEvent;
  }
}
