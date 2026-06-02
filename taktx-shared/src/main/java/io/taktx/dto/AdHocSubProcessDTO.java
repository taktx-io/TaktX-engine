/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.dto;

import java.util.Set;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class AdHocSubProcessDTO extends SubProcessDTO {

  private String activeElementsCollection;
  private String completionCondition;
  private boolean cancelRemainingInstances = true;

  public AdHocSubProcessDTO(
      String id,
      String parentId,
      String name,
      Set<String> incoming,
      Set<String> outgoing,
      LoopCharacteristicsDTO loopCharacteristics,
      FlowElementsDTO elements,
      InputOutputMappingDTO ioMapping,
      String activeElementsCollection,
      String completionCondition,
      boolean cancelRemainingInstances) {

    super(id, parentId, name, incoming, outgoing, loopCharacteristics, elements, ioMapping, false);
    this.activeElementsCollection = activeElementsCollection;
    this.completionCondition = completionCondition;
    this.cancelRemainingInstances = cancelRemainingInstances;
  }
}
