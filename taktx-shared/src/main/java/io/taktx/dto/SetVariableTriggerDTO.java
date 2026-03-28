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
import java.util.UUID;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@RegisterForReflection
public class SetVariableTriggerDTO extends ProcessInstanceTriggerDTO {

  private List<Long> parentElementInstanceIdPath;
  private VariablesDTO variables;

  public SetVariableTriggerDTO(
      UUID processInstanceId, List<Long> parentElementInstanceIdPath, VariablesDTO variables) {
    super(processInstanceId);
    this.parentElementInstanceIdPath = parentElementInstanceIdPath;
    this.variables = variables;
  }
}
