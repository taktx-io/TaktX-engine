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
