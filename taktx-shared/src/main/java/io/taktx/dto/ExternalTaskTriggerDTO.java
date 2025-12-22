/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
 */

package io.taktx.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@ToString
@NoArgsConstructor
@EqualsAndHashCode
@Builder
@RegisterForReflection
public class ExternalTaskTriggerDTO implements SchedulableMessageDTO {

  @Setter private UUID processInstanceId;

  private ProcessDefinitionKey processDefinitionKey;

  private String externalTaskId;

  private String elementId;

  private List<Long> elementInstanceIdPath;

  private VariablesDTO variables;

  private Map<String, String> headers;

  public ExternalTaskTriggerDTO(
      UUID processInstanceId,
      ProcessDefinitionKey processDefinitionKey,
      String externalTaskId,
      String elementId,
      List<Long> elementInstanceIdPath,
      VariablesDTO variables,
      Map<String, String> headers) {
    this.processInstanceId = processInstanceId;
    this.processDefinitionKey = processDefinitionKey;
    this.externalTaskId = externalTaskId;
    this.elementId = elementId;
    this.elementInstanceIdPath = elementInstanceIdPath;
    this.variables = variables;
    this.headers = headers;
  }
}
