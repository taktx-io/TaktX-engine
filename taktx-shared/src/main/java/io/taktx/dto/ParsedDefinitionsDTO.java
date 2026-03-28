/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Getter
@EqualsAndHashCode(callSuper = true)
@Builder(toBuilder = true)
@ToString
@NoArgsConstructor
@AllArgsConstructor
@RegisterForReflection
public class ParsedDefinitionsDTO extends DefinitionsTriggerDTO {
  private DefinitionsKey definitionsKey;

  private ProcessDTO rootProcess;

  private Map<String, MessageDTO> messages;

  private Map<String, EscalationDTO> escalations;

  private Map<String, ErrorDTO> errors;

  private Map<String, SigDTO> signals;
}
