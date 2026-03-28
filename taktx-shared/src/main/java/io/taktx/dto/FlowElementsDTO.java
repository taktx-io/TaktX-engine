/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import io.quarkus.runtime.annotations.RegisterForReflection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Data
@EqualsAndHashCode
@ToString
@NoArgsConstructor
@JsonFormat(shape = JsonFormat.Shape.ARRAY)
@RegisterForReflection
public class FlowElementsDTO {

  private Map<String, FlowElementDTO> elements;

  public FlowElementsDTO(Map<String, FlowElementDTO> elements) {
    this.elements = elements;
  }

  @JsonIgnore
  public List<BaseElementDTO> values() {
    return List.copyOf(elements.values());
  }

  @JsonIgnore
  public Set<String> keySet() {
    return Set.copyOf(elements.keySet());
  }

  @JsonIgnore
  public FlowElementDTO get(String id) {
    return elements.get(id);
  }

  @JsonIgnore
  public List<StartEventDTO> getStartEvents() {
    return elements.values().stream()
        .filter(StartEventDTO.class::isInstance)
        .map(StartEventDTO.class::cast)
        .toList();
  }

  @JsonIgnore
  public FlowElementDTO getStartNode(String elementId) {
    FlowElementDTO flowNodeDTO;
    if (elementId != null) {
      flowNodeDTO = elements.get(elementId);
    } else {
      List<StartEventDTO> startEvents = getStartEvents();
      if (startEvents.isEmpty()) {
        Optional<FlowNodeDTO> withoutInputFlow =
            elements.values().stream()
                .filter(FlowNodeDTO.class::isInstance)
                .map(e -> (FlowNodeDTO) e)
                .filter(flowNode -> !flowNode.getIncoming().isEmpty())
                .findFirst();
        if (withoutInputFlow.isPresent()) {
          flowNodeDTO = withoutInputFlow.get();
        } else {
          flowNodeDTO = elements.values().iterator().next();
        }
      } else {
        flowNodeDTO = startEvents.getFirst();
      }
    }
    return flowNodeDTO;
  }

  @JsonIgnore
  public Optional<FlowNodeDTO> getFlowNode(String elementId) {
    return elements.values().stream()
        .filter(FlowNodeDTO.class::isInstance)
        .map(FlowNodeDTO.class::cast)
        .filter(flowElement -> elementId.equals(flowElement.getId()))
        .findFirst();
  }
}
