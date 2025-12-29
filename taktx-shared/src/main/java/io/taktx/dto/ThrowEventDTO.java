/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
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
public abstract class ThrowEventDTO extends EventDTO {

  private Set<EventDefinitionDTO> eventDefinitions;

  protected ThrowEventDTO(
      String id,
      String parentId,
      String name,
      Set<String> incoming,
      Set<String> outgoing,
      InputOutputMappingDTO ioMapping,
      Set<EventDefinitionDTO> eventDefinitions) {
    super(id, parentId, name, incoming, outgoing, ioMapping);
    this.eventDefinitions = eventDefinitions;
  }
}
