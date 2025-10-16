/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
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
