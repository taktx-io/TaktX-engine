/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonFormat.Shape;
import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Getter
@EqualsAndHashCode
@ToString
@NoArgsConstructor
@JsonFormat(shape = Shape.ARRAY)
@RegisterForReflection
public class ProcessDefinitionKey implements Comparable<ProcessDefinitionKey> {
  private String processDefinitionId;

  private Integer version;

  public ProcessDefinitionKey(String processDefinitionId) {
    this(processDefinitionId, -1);
  }

  public ProcessDefinitionKey(String processDefinitionId, Integer version) {
    this.processDefinitionId = processDefinitionId;
    this.version = version;
  }

  public static ProcessDefinitionKey of(ProcessDefinitionDTO processDefinition) {
    return new ProcessDefinitionKey(
        processDefinition.getDefinitions().getDefinitionsKey().getProcessDefinitionId(),
        processDefinition.getVersion());
  }

  @Override
  public int compareTo(ProcessDefinitionKey other) {
    int cmp = this.processDefinitionId.compareTo(other.processDefinitionId);
    if (cmp != 0) return cmp;
    return Integer.compare(this.version, other.version);
  }
}
