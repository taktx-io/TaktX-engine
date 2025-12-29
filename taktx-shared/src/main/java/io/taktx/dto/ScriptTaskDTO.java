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
import java.util.Set;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@RegisterForReflection
public class ScriptTaskDTO extends ExternalTaskDTO {

  private ScriptType scriptType;
  private List<String> scriptExpressions;
  private String resultVariableName;

  public ScriptTaskDTO(
      String id,
      String parentId,
      String name,
      String workerDefinition,
      String retries,
      Set<String> incoming,
      Set<String> outgoing,
      LoopCharacteristicsDTO loopCharacteristics,
      Map<String, String> headers,
      InputOutputMappingDTO ioMapping,
      ScriptType scriptType,
      List<String> scriptExpressions,
      String resultVariableName) {
    super(
        id,
        parentId,
        name,
        incoming,
        outgoing,
        loopCharacteristics,
        ioMapping,
        workerDefinition,
        retries,
        scriptType.name(),
        headers);
    this.scriptType = scriptType;
    this.scriptExpressions = scriptExpressions;
    this.resultVariableName = resultVariableName;
  }
}
