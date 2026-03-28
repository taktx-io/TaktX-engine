/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.quarkus.runtime.annotations.RegisterForReflection;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@RegisterForReflection
public abstract class CatchEventDTO extends EventDTO {
  protected Set<EventDefinitionDTO> eventDefinitions;

  protected CatchEventDTO(
      String id,
      String parentId,
      String name,
      Set<String> incoming,
      Set<String> outgoing,
      Set<EventDefinitionDTO> eventDefinitions,
      InputOutputMappingDTO ioMapping) {
    super(id, parentId, name, incoming, outgoing, ioMapping);
    this.eventDefinitions = eventDefinitions;
  }

  @JsonIgnore
  public Set<TimerEventDefinitionDTO> getTimerEventDefinitions() {
    return eventDefinitions.stream()
        .filter(TimerEventDefinitionDTO.class::isInstance)
        .map(TimerEventDefinitionDTO.class::cast)
        .collect(Collectors.toSet());
  }

  @JsonIgnore
  public Set<MessageEventDefinitionDTO> getMessageventDefinitions() {
    return eventDefinitions.stream()
        .filter(MessageEventDefinitionDTO.class::isInstance)
        .map(MessageEventDefinitionDTO.class::cast)
        .collect(Collectors.toSet());
  }

  @JsonIgnore
  public Set<SignalEventDefinitionDTO> getSignalDefinitions() {
    return eventDefinitions.stream()
        .filter(SignalEventDefinitionDTO.class::isInstance)
        .map(SignalEventDefinitionDTO.class::cast)
        .collect(Collectors.toSet());
  }
}
